package dev.vitrail.mixin.access;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the depth image the game draws the first person hand against when a post effect asks the
 * hand's depth to line up with the world's.
 * <p>
 * <strong>It is the second screen-sized target the render scale has to move, and the reason is in
 * {@code GameRenderer.render3dHud}.</strong> That method picks the depth the hand pass is built
 * with, {@code consistentDepthRequired ? hud3DTarget : mainRenderTarget}, and
 * {@code consistentDepthRequired} is {@code !appliedPostEffects.isEmpty()}. The colour attachment
 * comes from the main target either way, so on a frame where a post effect is applied and the main
 * target's colour is the scaled image, a hand pass built against this target's window-sized depth
 * asks the device for a mismatched pair and Vulkan refuses it by size.
 * <p>
 * It is allocated once, at the window's size, and moved only when the game resizes the window, so it
 * does not follow the swap the way the main target and the per frame targets do. The render scale
 * resizes it alongside the entity outline target, which is the same kind of thing for the same
 * reason.
 */
@Mixin(GameRenderer.class)
public interface GameRendererHudTargetAccessor {

	@Accessor("hud3DTarget")
	RenderTarget vitrail$hud3DTarget();
}
