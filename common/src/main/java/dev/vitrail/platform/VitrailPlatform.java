package dev.vitrail.platform;

import java.nio.file.Path;

/**
 * The whole surface the loader modules have to implement. Kept deliberately small: if a
 * new method shows up here it should be because the common code genuinely cannot answer
 * the question on its own.
 */
public interface VitrailPlatform {

	String loaderName();

	String loaderVersion();

	String modVersion();

	/** Installed version of an optional integration, or "unknown" when absent. */
	String modVersion(String modId);

	String minecraftVersion();

	/** Root of the game instance, where the user-editable shader sources live. */
	Path gameDirectory();

	boolean isModLoaded(String modId);
}
