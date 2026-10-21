package dev.vitrail.mixin.game;

import dev.vitrail.pack.option.OptionValue;
import dev.vitrail.render.PackChain;
import dev.vitrail.render.PackChoice;
import dev.vitrail.settings.PackSession;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Supplies Wakes Reforged's no-Iris adapter from the pack this engine actually draws. Without it,
 * AUTO selects the game's lit pipeline rather than the block pipeline Wakes uses with shaders.
 * No Iris API is installed, and an absent adapter or changed method leaves that mod alone.
 */
@Pseudo
@Mixin(targets = "com.leclowndu93150.wakes.compat.iris.NoIrisHooks", remap = false)
public abstract class WakesShaderHooksMixin {
	@Unique private static PackSession vitrail$session;
	@Unique private static int vitrail$revision;

	@Unique
	private static PackSession vitrail$current() {
		PackSession current = PackChain.drawingPack() ? PackChoice.session().orElse(null) : null;
		if (current != vitrail$session) {
			vitrail$session = current;
			vitrail$revision++;
		}
		return current;
	}

	@Unique
	private static String vitrail$value(String name) {
		PackSession session = vitrail$current();
		return session == null ? null : session.appliedOption(name).orElse(null);
	}

	@Inject(method = "shadersEnabled", at = @At("RETURN"), cancellable = true, require = 0)
	private void vitrail$enabled(CallbackInfoReturnable<Boolean> callback) {
		callback.setReturnValue(vitrail$current() != null);
	}

	@Inject(method = "packName", at = @At("RETURN"), cancellable = true, require = 0)
	private void vitrail$name(CallbackInfoReturnable<String> callback) {
		PackSession session = vitrail$current();
		if (session != null) {
			callback.setReturnValue(session.packFileName());
		}
	}

	@Inject(method = "optionsRevision", at = @At("RETURN"), cancellable = true, require = 0)
	private void vitrail$revision(CallbackInfoReturnable<Integer> callback) {
		if (vitrail$current() != null) {
			callback.setReturnValue(vitrail$revision);
		}
	}

	@Inject(method = "hasOption", at = @At("RETURN"), cancellable = true, require = 0)
	private void vitrail$has(String name, CallbackInfoReturnable<Boolean> callback) {
		PackSession session = vitrail$current();
		if (session != null) {
			callback.setReturnValue(session.declared().contains(name));
		}
	}

	@Inject(method = "flag", at = @At("RETURN"), cancellable = true, require = 0)
	private void vitrail$flag(String name, boolean fallback, CallbackInfoReturnable<Boolean> callback) {
		String value = vitrail$value(name);
		if (value != null) {
			OptionValue parsed = OptionValue.parse(value);
			if (parsed.isBoolean()) {
				callback.setReturnValue(parsed.asBoolean());
			}
		}
	}

	@Inject(method = "number", at = @At("RETURN"), cancellable = true, require = 0)
	private void vitrail$number(String name, float fallback, CallbackInfoReturnable<Float> callback) {
		String value = vitrail$value(name);
		if (value != null) {
			try {
				callback.setReturnValue(Float.parseFloat(value.trim()));
			} catch (NumberFormatException ignored) {
				// The no-Iris adapter already returned the caller's fallback.
			}
		}
	}
}
