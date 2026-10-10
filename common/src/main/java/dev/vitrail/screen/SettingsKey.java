package dev.vitrail.screen;

import dev.vitrail.HostReport;
import dev.vitrail.IrisBeside;
import dev.vitrail.render.PackChoice;
import dev.vitrail.ScreenText;
import dev.vitrail.settings.PackFile;
import dev.vitrail.settings.PackSession;
import dev.vitrail.Vitrail;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The three keys this mod binds: R reads the pack again, K turns shaders on and off, I opens the
 * pack screen.
 * <p>
 * All three are Iris's own, {@code iris.keybind.reload}, {@code iris.keybind.toggleShaders} and
 * {@code iris.keybind.shaderPackSelection} at {@code Iris.java:811}, {@code 812} and {@code 813}, so
 * a player who has configured a pack before will try them first and find them. Iris on Vulkan binds
 * only the last of them ({@code IrisVKOnly.java:17}), so K and R reach this mod alone there. The
 * game hands a press to every mapping bound to its key, so beside Iris one press reaches both mods,
 * and only one of them may answer it. Off Vulkan this engine draws nothing and its keys do nothing,
 * which beside Iris on OpenGL leaves each press to Iris's own, its reload
 * answering only with its debug options on ({@code Iris.java:181}). Where Iris does not draw, on
 * Vulkan, Iris binds I to a screen saying it cannot run there ({@code IrisVKOnly.java:17}), and the
 * press is taken from Iris's mapping before Iris asks it ({@link #beforeTick}). A player who moved
 * either key on Vulkan gets each screen on its own.
 * <p>
 * The mappings and what a press does are here; registering them and asking them on the tick is each
 * loader's own business, because those are the two things they do differently. Asking on a tick
 * rather than on a key event is deliberate and is the same on both: the game only feeds key mappings
 * while no screen is open, so the shortcut cannot open a second copy of this screen over the first,
 * and it costs two booleans a tick, and one lookup more beside Iris.
 * <p>
 * That last point is also why the screen keeps a reload button of its own. From an open screen these
 * keys are not fed at all, so the key alone would leave the one place where a pack is being worked on
 * without the one thing that reads it again.
 */
public final class SettingsKey {

	public static final KeyMapping.Category CATEGORY =
			new KeyMapping.Category(Identifier.fromNamespaceAndPath(Vitrail.MOD_ID, "keybinds"));

	// The default key is a number, and the two games number keys differently: 26.2 hands a mapping
	// a GLFW key code, 26.3 an SDL scancode, where I is 73 in the first and 12 in the second. The
	// game's own InputConstants carries each key under one name in the numbering that game uses, so
	// naming the key through it binds the same physical key under either game.
	public static final KeyMapping OPEN =
			new KeyMapping(ScreenText.OPEN_SETTINGS, InputConstants.KEY_I, CATEGORY);

	public static final KeyMapping RELOAD =
			new KeyMapping(ScreenText.RELOAD_PACK, InputConstants.KEY_R, CATEGORY);

	public static final KeyMapping TOGGLE =
			new KeyMapping(ScreenText.TOGGLE_SHADERS, InputConstants.KEY_K, CATEGORY);

	/** Iris's mapping for its pack screen, by the name it registers under on both backends. */
	private static final String IRIS_SCREEN_KEY = "iris.keybind.shaderPackSelection";

	private SettingsKey() {
	}

	/**
	 * Takes the press of the key this mod shares with Iris from Iris's mapping, on Vulkan where this
	 * engine draws. Called before anything of the tick has asked a key, because Iris asks its own at
	 * the end of the tick ({@code VKOnly_InitKeys.java:27}) and would open its screen over this one.
	 * Off Vulkan Iris's mapping is left alone, whichever of Iris's two faces a fallback left it with.
	 */
	public static void beforeTick() {
		if (!IrisBeside.installed() || HostReport.otherBackend()) {
			return;
		}

		KeyMapping iris = sharedIrisKey();
		if (iris != null) {
			drain(iris);
		}
	}

	/**
	 * Acts on whichever of the three was pressed since the last tick, and does nothing otherwise. Off
	 * Vulkan the presses are only emptied, so that none of them waits to be answered on a later tick.
	 * <p>
	 * One of them a tick, the screen first: a toggle and a reload pressed together would read the
	 * pack twice, and the second press is still queued for the next tick if it was not emptied.
	 */
	public static void poll() {
		if (HostReport.otherBackend()) {
			drain(OPEN);
			drain(RELOAD);
			drain(TOGGLE);
			return;
		}

		if (drain(OPEN)) {
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.gui.setScreen(PackScreens.open(minecraft.gui.screen()));

			return;
		}

		if (drain(TOGGLE)) {
			toggle();

			return;
		}

		if (drain(RELOAD)) {
			reload();
		}
	}

	/** Iris's mapping for its pack screen where it is bound to the same key as {@link #OPEN}, or null. */
	private static @Nullable KeyMapping sharedIrisKey() {
		KeyMapping iris = KeyMapping.get(IRIS_SCREEN_KEY);

		return iris != null && iris.same(OPEN) ? iris : null;
	}

	/**
	 * Answers whether a key was pressed, and empties whatever else it queued.
	 * <p>
	 * A tick can carry more than one press, and the rest are dropped rather than acted on. Opening one
	 * screen per press would stack them, each holding the one before it as the screen to go back to,
	 * so leaving would take as many Escapes as the key was tapped; reading the pack once per press
	 * would read the same files twice for nothing.
	 */
	private static boolean drain(KeyMapping key) {
		if (!key.consumeClick()) {
			return false;
		}

		while (key.consumeClick()) {
			// Nothing to do: the queue is only being emptied.
		}

		return true;
	}

	/**
	 * Reads the pack again from disk, and says which of the two things happened.
	 * <p>
	 * The directory is the loaded session's rather than the platform's whenever there is one, which is
	 * the choice the settings screen makes for the same reason: what a hand edit reloads and what the
	 * screen reloads must be the same folder.
	 * <p>
	 * The failure is asked for rather than caught, because catching is not where it lands: reading a
	 * pack puts what went wrong where the screen's own bottom line reads it instead of throwing, and
	 * the load's own catch is what makes that so. So a press that read nothing is told apart by asking
	 * that same question. Iris says both lines too, {@code Iris.java:187} and {@code Iris.java:194},
	 * off a catch rather than off a question because throwing is what its own reload does.
	 * <p>
	 * <b>A reading that read nothing because the named pack is gone answers as a failure.</b> Warned
	 * about and then treated as no pack having been asked for, it would put "Shaders Reloaded!" over
	 * a reading that opened nothing, and the screen's own bottom line would go further and say the
	 * game was drawing its own image on purpose. Asking for no pack at all is still no failure and
	 * still says the first line, which is the whole distinction: {@code PackChoice.packMissing} holds
	 * it.
	 */
	private static void reload() {
		PackChoice.reload(directory());

		say(PackChoice.lastError()
				.map(reason -> Component.translatable(ScreenText.RELOAD_FAILED, reason)
						.withStyle(ChatFormatting.RED))
				.orElseGet(() -> Component.translatable(ScreenText.PACK_RELOADED)));
	}

	/**
	 * Turns shaders off, or back on with the pack that was chosen, and says which, as Iris's toggle
	 * does ({@code Iris.toggleShaders}).
	 * <p>
	 * <b>The settings screen's own road, not a second one.</b> The switch is the {@code enabled} half
	 * of {@code pack.txt}, flipped over what the file says now and written back with the pack's name
	 * left where it is, which is what the screen's toggle writes; then the pack is read again from
	 * that file. So K and the screen cannot disagree about whether shaders are on, a hand edit of the
	 * file is honoured, and turning them back on lands on the pack the player had rather than on
	 * whatever a list shows first: {@code PackFile} keeps the two facts apart for exactly that.
	 * <p>
	 * Turning on with no pack chosen says so in the words the screen uses, rather than claiming a
	 * pack was switched to; a pack that is named and fails to load says why, as the reload key does.
	 * <p>
	 * <b>Where nothing came on, shaders are turned back off.</b> Iris does the same on a failed
	 * toggle: it says why in red and leaves shaders off ({@code Iris.java:199-209}). Left enabled,
	 * {@code pack.txt} would say shaders are on while nothing draws, the screen's toggle would agree
	 * with the file rather than with the picture, the next press of K would turn off what never came
	 * on, and every launch after would try the same pack again. So the {@code enabled} half is written
	 * back off, the pack's name kept, and the pack read again, which is what pressing K a second time
	 * would have done: the next press tries the pack afresh.
	 */
	private static void toggle() {
		Path directory = directory();
		Path file = PackChoice.packFile(directory);
		boolean enabled;
		try {
			PackFile onDisk = PackFile.read(file);
			enabled = !onDisk.enabled();
			PackFile.write(file, onDisk.withEnabled(enabled));
		} catch (IOException | RuntimeException e) {
			Vitrail.logger().error("Vitrail could not write {}", file, e);
			say(Component.translatable(ScreenText.TOGGLE_FAILED, String.valueOf(e.getMessage()))
					.withStyle(ChatFormatting.RED));

			return;
		}

		PackChoice.reload(directory);

		if (!enabled) {
			say(Component.translatable(ScreenText.SHADERS_TURNED_OFF));

			return;
		}

		PackFile asked = PackChoice.askedFor();
		MutableComponent refused = refusal(asked);
		if (refused == null) {
			say(Component.translatable(ScreenText.SHADERS_TOGGLED, asked.name()));

			return;
		}

		// Said before the switch goes back, which reads the pack again and forgets the reason.
		turnBackOff(directory, file);
		say(refused);
	}

	/**
	 * Why turning shaders on drew nothing, in the words the screen uses for each case, or null where
	 * a pack is drawing: none chosen, the one chosen missing from the folder, or the one chosen
	 * failing to load.
	 */
	private static @Nullable MutableComponent refusal(PackFile asked) {
		if (PackChoice.noPackWanted() || !asked.wantsPack() || asked.namesNone()) {
			return Component.translatable(ScreenText.NO_PACK).withStyle(ChatFormatting.YELLOW);
		}

		if (PackChoice.packMissing()) {
			return Component.translatable(ScreenText.PACK_MISSING, asked.name())
					.withStyle(ChatFormatting.RED);
		}

		return PackChoice.lastError()
				.map(reason -> Component.translatable(ScreenText.TOGGLE_FAILED, reason)
						.withStyle(ChatFormatting.RED))
				.orElse(null);
	}

	/**
	 * Writes the {@code enabled} half of {@code pack.txt} back off, the pack's name kept, and reads
	 * the pack again, for {@link #toggle} where nothing came on. A file that cannot be written is
	 * said in the log only: the reason nothing came on is the line the player needs.
	 */
	private static void turnBackOff(Path directory, Path file) {
		try {
			PackFile.write(file, PackFile.read(file).withEnabled(false));
		} catch (IOException | RuntimeException e) {
			Vitrail.logger().error("Vitrail could not write {}", file, e);

			return;
		}

		PackChoice.reload(directory);
	}

	/**
	 * The directory a key reads the pack from, which is the loaded session's rather than the
	 * platform's whenever there is one, for the reason {@link #reload} gives.
	 */
	private static Path directory() {
		return PackChoice.session()
				.map(PackSession::gameDirectory)
				.orElseGet(() -> Vitrail.platform().gameDirectory());
	}

	/**
	 * Says one line in chat, in a world only, which is Iris's own guard: outside one there is no chat
	 * to say it in, and what was read is in the log either way.
	 */
	private static void say(MutableComponent said) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			minecraft.player.sendSystemMessage(said);
		}
	}
}
