package dev.vitrail.mixin.sodium;

import dev.vitrail.sodium.SodiumPasses;
import dev.vitrail.pack.program.TerrainPass;
import dev.vitrail.render.TerrainDraw;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.client.gpu.device.backend.DrawBackend;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Hands Sodium the pack's own programs in place of its chunk shader, one per pass.
 * <p>
 * The head of {@code compileProgram} is the whole hook: it is one point, it has no state of its own,
 * and short circuiting it there leaves Sodium's memo untouched, so nothing of ours is ever handed
 * back once this is turned off.
 * <p>
 * <strong>The 26.3 half</strong>, updated for Sodium 0.9.2 through 0.9.3-beta.5.
 * {@code compileProgram} takes an order independent stage beside the pass (null on the classic road)
 * and, as of 0.9.3-beta.5, a trailing {@code ShaderCompilationParameters} record; using MixinExtras
 * {@code @Local(argsOnly = true)} matches both signatures without binding to the private parameter
 * type. The terrain atlas sampler is captured at the head of {@code DefaultChunkRenderer#render},
 * which still carries {@code terrainSampler} after {@code ShaderChunkRenderer#begin} dropped it in
 * 0.9.3-beta.5.
 */
@Mixin(value = ShaderChunkRenderer.class, remap = false)
public abstract class MixinShaderChunkRenderer {

	@Shadow
	protected VertexFormat vertexFormat;

	@Inject(method = "compileProgram", at = @At("HEAD"), cancellable = true)
	private void vitrail$terrain(CallbackInfoReturnable<RenderPipeline> callback,
			@Local(argsOnly = true) TerrainRenderPass pass,
			@Local(argsOnly = true) @Nullable OitStage stage) {
		if (DrawBackend.BACKEND == DrawBackend.OPENGL || stage != null) {
			return;
		}

		TerrainPass ours = SodiumPasses.of(pass);
		if (ours == null) {
			return;
		}

		RenderPipeline pipeline = TerrainDraw.pipeline(ours, this.vertexFormat, pass.getAtlas());
		if (pipeline != null) {
			callback.setReturnValue(pipeline);
		}
	}

	@Redirect(method = "compileProgram",
			at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;",
					ordinal = 0),
			require = 1)
	private Object vitrail$ofThisFormat(Map<?, ?> memo, Object pass) {
		Object found = memo.get(pass);
		if (found instanceof RenderPipeline pipeline
				&& !this.vertexFormat.equals(pipeline.getVertexFormatBinding(0))) {
			return null;
		}

		return found;
	}
}
