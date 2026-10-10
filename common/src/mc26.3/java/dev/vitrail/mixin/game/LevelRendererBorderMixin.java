package dev.vitrail.mixin.game;

import dev.vitrail.render.GraphicsApi;
import dev.vitrail.render.PackChain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.state.level.WorldBorderRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Hands the world border a pass on the layer {@link PackChain#openBorder()} empties for it, in
 * place of the level's own pass, and composes the layer onto the pack's picture once it is drawn.
 * <p>
 * The one call that draws the border in the classic order, the last line of
 * {@code executeClassicTransparency}, which is the only order this engine draws a pack in. Drawn into
 * the level's pass it lands on the main target after everything the pack's image is made of, and
 * the pack's final overwrites that target: {@link PackChain#openBorder()} says the rest.
 * <p>
 * The level's pass steps aside first, as it does for the engine's own stages: the layer is emptied
 * with a clear and composed with a pass of its own, and neither may happen while another pass is
 * open. It opens again at the level's next draw, of which there is none left in this method.
 * <p>
 * Only on a frame the border is really drawn, which is the renderer's own test: it draws nothing at
 * no alpha, and nearly every frame is one where nobody stands near enough for it to have any.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererBorderMixin {

	@WrapOperation(method = "executeClassicTransparency", require = 1,
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/WorldBorderRenderer;render("
							+ "Lnet/minecraft/client/renderer/state/level/WorldBorderRenderState;"
							+ "Lcom/mojang/renderpearl/api/commands/RenderPass;"
							+ "Lnet/minecraft/world/phys/Vec3;D)V"))
	private void vitrail$border(WorldBorderRenderer border, WorldBorderRenderState state,
			RenderPass pass, Vec3 camera, double distance, Operation<Void> original) {
		if (state.alpha <= 0.0) {
			original.call(border, state, pass, camera, distance);

			return;
		}

		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		GraphicsApi.suspendLevelPass();
		GpuTextureView layer = PackChain.openBorder();
		if (layer == null) {
			original.call(border, state, pass, camera, distance);

			return;
		}

		// The world's depth beside the layer, as the features are given it: the border still hides
		// behind the terrain, and writes the depth it would have written. openBorder answers a
		// layer only where the main target is there to be read.
		try (RenderPass into = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "Vitrail world border", layer, Optional.empty(), main.getDepthTextureView(),
				OptionalDouble.empty())) {
			original.call(border, state, into, camera, distance);
		}

		PackChain.closeBorder();
	}
}
