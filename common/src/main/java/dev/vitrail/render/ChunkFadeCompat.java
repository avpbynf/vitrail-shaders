package dev.vitrail.render;

import dev.vitrail.Vitrail;
import dev.vitrail.glsl.ChunkFadeShader;
import dev.vitrail.glsl.PackProgram;
import dev.vitrail.pack.model.ProgramStage;


/**
 * Optional bridge to CFI's own shader generator. Reflection keeps the mod optional and keeps its
 * game-specific classes out of the shared translator. This bridge never owns or frees a buffer;
 * CFI's existing Sodium draw hook supplies the named resources, including on a pack draw.
 */
final class ChunkFadeCompat {
	private ChunkFadeCompat() {
	}

	record Patch(String vertexDeclarations, String vertexInit, String fragmentDeclarations,
			String fragmentTail, boolean fade) {
		String vertex(String source) {
			return ChunkFadeShader.vertex(source, this.vertexDeclarations, this.vertexInit);
		}
		String fragment(String source) {
			return this.fade ? ChunkFadeShader.fragment(source, this.fragmentDeclarations, this.fragmentTail) : source;
		}
	}

	static Patch prepare(GeometryProgram.Pass pass, PackProgram.Loaded loaded) {
		if (!GraphicsApi.supportsChunkFade() || !pass.namespace().contains("sodium")
				|| !Vitrail.platform().isModLoaded("chunksfadein")) {
			return null;
		}
		if (loaded.program().stages().containsKey(ProgramStage.GEOMETRY)) {
			Vitrail.logger().warn("Chunks Fade In bridge does not yet support geometry stages in {}", loaded.path());
			return null;
		}
		try {
			Class<?> config = Class.forName("com.koteinik.chunksfadein.config.Config");
			if (!flag(config, "isModEnabled")) {
				return null;
			}
			boolean animation = flag(config, "isAnimationEnabled") && flag(config, "patchShaderAnimation");
			boolean fade = !pass.shadow() && flag(config, "isFadeEnabled") && flag(config, "patchShaderFade");
			String colour = ChunkFadeShader.firstColour(loaded.program().stages().get(ProgramStage.FRAGMENT).text());
			if (fade && colour.isEmpty()) {
				Vitrail.logger().warn("Chunks Fade In cannot identify the first colour output in {}", loaded.path());
				fade = false;
			}
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
			String fragmentDeclarations = "";
			String fragmentTail = "";
			if (fade) {
				shader.call("fragInVars");
				shader.call("utilFunctions");
				fragmentDeclarations = shader.flush();
				shader.call("fragColorMod", colour + ".rgb");
				fragmentTail = shader.flush();
			}
			Vitrail.logger().info("Chunks Fade In bridge: {} motion={}, fade={}", pass.name(), animation, fade);
			return new Patch(vertexDeclarations, vertexInit, fragmentDeclarations, fragmentTail, fade);
		} catch (ReflectiveOperationException | LinkageError e) {
			Vitrail.logger().warn("Could not connect Chunks Fade In to {}", loaded.path(), e);
			return null;
		}
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
