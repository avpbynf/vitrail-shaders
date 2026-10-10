package dev.vitrail.mixin.game;

import dev.vitrail.render.PackChain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.WorldBorderRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Opens the world border's pass on the layer {@link PackChain#openBorder()} empties for it, in place
 * of the main target, and composes the layer onto the pack's picture once the pass has closed.
 * <p>
 * On this game the border opens a pass of its own, on the main target or the weather target, and
 * reads no override, so the colour image of that one opening is what is swapped. Its depth is kept:
 * the border still hides behind the terrain. {@link PackChain#openBorder()} says why the border has
 * to be carried at all.
 * <p>
 * The opening is reached only on a frame the border is drawn, past the renderer's own test for an
 * alpha, so nothing is emptied or composed on the frames nobody stands near one.
 */
@Mixin(WorldBorderRenderer.class)
public abstract class WorldBorderRendererMixin {

	/**
	 * Whether this frame's border was drawn into the layer. A field of the mixin and not a static:
	 * the renderer is one object and its draw does not overlap with itself.
	 */
	@Unique
	private boolean vitrail$layered;

	@WrapOperation(method = "render", require = 1,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/systems/CommandEncoder;createRenderPass("
							+ "Ljava/util/function/Supplier;"
							+ "Lcom/mojang/blaze3d/textures/GpuTextureView;"
							+ "Ljava/util/Optional;"
							+ "Lcom/mojang/blaze3d/textures/GpuTextureView;"
							+ "Ljava/util/OptionalDouble;"
							+ ")Lcom/mojang/blaze3d/systems/RenderPass;"))
	private RenderPass vitrail$open(CommandEncoder encoder, Supplier<String> label,
			GpuTextureView colour, Optional<?> clearColour, GpuTextureView depth,
			OptionalDouble clearDepth, Operation<RenderPass> original) {
		GpuTextureView layer = PackChain.openBorder();
		this.vitrail$layered = layer != null;

		return original.call(encoder, label, layer == null ? colour : layer, clearColour, depth,
				clearDepth);
	}

	/** At every return, the pass having closed by then; a no on the frames the border is not drawn. */
	@Inject(method = "render", require = 1, at = @At("RETURN"))
	private void vitrail$compose(CallbackInfo callback) {
		if (this.vitrail$layered) {
			this.vitrail$layered = false;
			PackChain.closeBorder();
		}
	}
}
