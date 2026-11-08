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

	/**
	 * Another mod's version as its loader writes it, or {@code "unknown"} where it is not here.
	 * <p>
	 * Here rather than read off a file because the loader is the one place that knows which build is
	 * really in front, and one question outside this mod turns on it: which Sodium was installed
	 * decides whether a chunk pass has to subtract the camera's fraction itself
	 * ({@link dev.vitrail.SodiumTerrainOffset}).
	 */
	String modVersion(String modId);

	String minecraftVersion();

	/** Root of the game instance, where the user-editable shader sources live. */
	Path gameDirectory();

	boolean isModLoaded(String modId);
}
