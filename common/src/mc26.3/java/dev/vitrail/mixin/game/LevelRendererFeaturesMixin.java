package dev.vitrail.mixin.game;

import dev.vitrail.render.EntityDraw;
import dev.vitrail.render.GameRender;
import dev.vitrail.render.LevelPass;
import dev.vitrail.render.PackChain;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Hands the game's translucent features the pass {@link GameRender#featuresPass()} opens on the
 * layer, while a redirect stands, in place of the level's own pass.
 * <p>
 * The one call that draws that phase in the classic order, which is the only order this engine
 * draws a pack in, improved transparency being kept off meanwhile. The level's pass steps aside for
 * the layer's as it does for the engine's own stages, and opens again at the translucent terrain
 * after it; the layer is composed onto the pack's image where the phase's closing stage runs, after
 * this returns, whichever loader posts it.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererFeaturesMixin {

	@WrapOperation(method = "executeClassicTransparency", require = 1,
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;"
							+ "executeTranslucent(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
	private void vitrail$layer(FeatureRenderDispatcher.PreparedFrame frame, RenderPass pass,
			Operation<Void> original) {
		RenderPass layer = GameRender.featuresPass();
		if (layer == null) {
			original.call(frame, pass);

			return;
		}

		try {
			original.call(frame, layer);
		} finally {
			layer.close();
		}
	}

	/**
	 * Opens the translucent entity window around the text 26.3 draws through a wall, so the entity
	 * door serves it with the pack's program as it serves the name of a player in the open.
	 * <p>
	 * That game draws the see-through copy of a name tag in a phase of its own, after the translucent
	 * terrain and the outlines, into a pass it opens on the main target's colour alone
	 * ({@code LevelRenderer.executeSeeThrough}), past the window the translucent features are served
	 * in. Its six text rows are the door's already, on {@code gbuffers_entities_translucent} where
	 * Iris 26.3 sends them ({@code pipeline/IrisPipelines.java:79-80} on its 26.3 branch), so what
	 * the phase needs is the window and nothing else: no layer is emptied and composed for it, which
	 * is what the first cut of this bracket cost on every frame showing a name. The pass is replaced
	 * by a {@link LevelPass} on the same attachment, for the reason {@link GameRender#featuresPass}
	 * gives: the door records a served run into a pass of its own, and the game's would have to step
	 * aside for it. A draw the door hands back lands on the main target as it did, under the pack's
	 * final, which is where it went before this bracket and where a pack with no entity program at all
	 * still sends it. With no pack loaded the phase runs untouched.
	 */
	@WrapMethod(method = "executeSeeThrough", require = 1)
	private void vitrail$seeThrough(FeatureRenderDispatcher.PreparedFrame frame, RenderTarget target,
			Operation<Void> original) {
		if (!PackChain.drawingPack()) {
			original.call(frame, target);

			return;
		}

		GpuTextureView colour = target.getColorTextureView();
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		Supplier<String> label = () -> "See through features";
		Supplier<RenderPass> opener = () -> encoder.createRenderPass(label, colour, Optional.empty(),
				null, OptionalDouble.empty());

		EntityDraw.translucentFeatures(true);
		try (RenderPass pass = LevelPass.open(opener.get(), colour, null, opener)) {
			RenderSystem.bindDefaultUniforms(pass);
			frame.executeSeeThrough(pass);
		} finally {
			EntityDraw.translucentFeatures(false);
		}
	}
}
