package dev.vitrail.render;

import dev.vitrail.Vitrail;
import dev.vitrail.glsl.ChunkFadeShader;
import dev.vitrail.glsl.PackProgram;
import dev.vitrail.pack.model.ProgramStage;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Set;


/**
 * Optional bridge to CFI's own shader generator. Reflection keeps the mod optional and keeps its
 * game-specific classes out of the shared translator. This bridge never owns or frees a buffer;
 * CFI's existing Sodium draw hook supplies the named resources, including on a pack draw.
 */
final class ChunkFadeCompat {
	private static volatile boolean resolved;
	private static VarHandle revision;
	private static int installedRevision;

	/** Resolve the exact named-buffer contract once, before any shader is patched. */
	private static boolean available() {
		return resolved ? revision != null : resolve();
	}

	private static synchronized boolean resolve() {
		if (!resolved) {
			try {
				if (!GraphicsApi.supportsChunkFade() || !Vitrail.platform().isModLoaded("chunksfadein")) {
					return false;
				}
				String cfi = Vitrail.platform().modVersion("chunksfadein");
				String sodium = Vitrail.platform().modVersion("sodium");
				if (!"3.0.29-26.3".equals(cfi) || !Set.of("0.9.2+mc26.3", "0.9.3-beta.1+mc26.3").contains(sodium)) {
					Vitrail.logger().warn("Chunks Fade In bridge requires CFI 3.0.29-26.3 and Sodium "
							+ "0.9.2 or 0.9.3-beta.1 for 26.3; found {} and {}, leaving pack shaders unchanged", cfi, sodium);
					return false;
				}
				try {
					revision = MethodHandles.publicLookup().findStaticVarHandle(
							Class.forName("com.koteinik.chunksfadein.ShaderUtils"), "shaderVersion", int.class);
				} catch (ReflectiveOperationException | LinkageError e) {
					Vitrail.logger().warn("Could not resolve Chunks Fade In shader revision", e);
				}
			} finally {
				resolved = true;
			}
		}
		return revision != null;
	}

	/** CFI advances this counter when its settings require new shader code. */
	static boolean changed() {
		return available() && (int) revision.get() != installedRevision;
	}

	/** Remember the revision the current load was built against, including an empty load. */
	static void settle() {
		installedRevision = available() ? (int) revision.get() : 0;
	}

	private ChunkFadeCompat() {
	}

	record Patch(String vertex, String fragment, boolean fade) {
	}

	static Patch prepare(GeometryProgram.Pass pass, PackProgram.Loaded loaded) {
		if (!pass.namespace().contains("sodium") || !available()) {
			return null;
		}
		if (loaded.program().stages().containsKey(ProgramStage.GEOMETRY)) {
			Vitrail.logger().warn("Chunks Fade In bridge does not support geometry stages in {}", loaded.path());
			return null;
		}
		String vertex = loaded.program().stages().get(ProgramStage.VERTEX).text();
		String fragment = loaded.program().stages().get(ProgramStage.FRAGMENT).text();
		if (noInjection(vertex) || noInjection(fragment)) {
			return null;
		}
		try {
			Class<?> config = Class.forName("com.koteinik.chunksfadein.config.Config");
			if (!flag(config, "isModEnabled")) {
				return null;
			}
			boolean animation = flag(config, "isAnimationEnabled") && flag(config, "patchShaderAnimation")
					&& !ChunkFadeShader.marker(vertex, "_cfi_noInjectModMarker")
					&& !ChunkFadeShader.marker(vertex, "_cfi_noInjectVertModMarker");
			boolean fade = !pass.shadow() && flag(config, "isFadeEnabled") && flag(config, "patchShaderFade")
					&& !ChunkFadeShader.marker(fragment, "_cfi_noInjectModMarker")
					&& !ChunkFadeShader.marker(fragment, "_cfi_noInjectFragModMarker");
			if (!animation && !fade) {
				return null;
			}
			Generator shader = new Generator();
			shader.call("regionId", "of_RegionId");
			shader.call("overrideAnimation", animation);
			shader.call("overrideFade", fade);
			shader.call("overrideCurvature", false);
			shader.call("vertInVars");
			shader.call("vertOutVars");
			shader.call("utilFunctions");
			String vertexDeclarations = shader.flush();
			shader.call("vertInitOutVarsDrawId", "ofLocal", "a_LightAndData.w");
			shader.call("vertInitMod", "ofLocal", "of_Vertex.xyz", true, "vec3(a_LightAndData.w)", false);
			String vertexInit = shader.flush();
			if (fade) {
				shader.call("fragInVars");
				shader.call("utilFunctions");
				String declarations = shader.flush();
				shader.call("fragColorMod", "ofCfiColour",
						"texture(cfi_sky, gl_FragCoord.xy / cfi_screenSize).ofCfiChannels");
				String operation = shader.flush();
				shader.call("calculateFade", "float ofCfiFade = ");
				String scalar = shader.flush() + "\nofCfiColour *= ofCfiFade;\n";
				boolean oklab = ((Enum<?>) config.getField("fadeMixType").get(null)).name().equals("OKLAB");
				fragment = ChunkFadeShader.fade(fragment, declarations, (colour, channels) -> {
					if (oklab && !channels.equals("rgb")) {
						throw new IllegalArgumentException("Oklab fade requires an RGB colour before packing");
					}
					return operation.replace("ofCfiColour", colour).replace("ofCfiChannels", channels);
				}, scalar);
			}
			vertex = ChunkFadeShader.vertex(vertex, vertexDeclarations, vertexInit);
			Vitrail.logger().info("Chunks Fade In bridge: {} motion={}, fade={}", pass.name(), animation, fade);
			return new Patch(vertex, fragment, fade);
		} catch (ReflectiveOperationException | LinkageError | IllegalArgumentException e) {
			Vitrail.logger().warn("Could not connect Chunks Fade In to {}; leaving pack shaders unchanged", loaded.path(), e);
			return null;
		}
	}

	private static boolean noInjection(String source) {
		return ChunkFadeShader.marker(source, "_cfi_noInjectMarker")
				|| ChunkFadeShader.marker(source, "_cfi_ignoreMarker");
	}

	private static boolean flag(Class<?> config, String name) throws ReflectiveOperationException {
		return config.getField(name).getBoolean(null);
	}

	/** Invoked only while equipping a pack, never in the frame or per-region loop. */
	private static final class Generator {
		private final Class<?> type;
		private final Object shader;
		Generator() throws ReflectiveOperationException {
			this.type = Class.forName("com.koteinik.chunksfadein.core.FadeShader");
			this.shader = this.type.getConstructor().newInstance();
		}
		void call(String name, Object... args) throws ReflectiveOperationException {
			Class<?>[] types = new Class<?>[args.length];
			for (int i = 0; i < args.length; i++) {
				types[i] = args[i] instanceof Boolean ? boolean.class : String.class;
			}
			this.type.getMethod(name, types).invoke(this.shader, args);
		}
		String flush() throws ReflectiveOperationException {
			return (String) this.type.getMethod("flushMultiline").invoke(this.shader);
		}
	}
}
