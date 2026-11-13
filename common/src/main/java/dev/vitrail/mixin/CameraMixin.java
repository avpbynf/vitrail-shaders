package dev.vitrail.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

	@Shadow
	private Level level;

	@Inject(method = "getCameraEntityPartialTicks", at = @At("HEAD"), cancellable = true)
	private void vitrail$guardNullLevel(DeltaTracker deltaTracker, CallbackInfoReturnable<Float> cir) {
		if (this.level == null) {
			cir.setReturnValue(deltaTracker.getGameTimeDeltaPartialTick(false));
		}
	}
}
