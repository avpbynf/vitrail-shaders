package dev.vitrail.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vitrail.pack.option.OptionValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Shader adapters must read the actual defaults even when a pack hides an option from its menu. */
class PackSessionTest {
	@TempDir
	Path game;

	@Test
	void resolvesHiddenDefaultsAndExplicitChoices() throws IOException {
		Path pack = this.game.resolve("shaderpacks/test-pack");
		Path shaders = Files.createDirectories(pack.resolve("shaders"));
		Files.write(shaders.resolve("shaders.properties"), List.of("screen = VISIBLE"));
		Files.write(shaders.resolve("options.glsl"), List.of(
				"#define VISIBLE 1 // [0 1]", "#define waves_amplitude 0.1 // [0.1 0.65]",
				"//#define WAVING_WATER", "#ifdef WAVING_WATER", "#endif"));
		PackSession defaults = PackSession.read(this.game, pack, "en_us");
		assertTrue(defaults.menu().option("waves_amplitude").isEmpty());
		assertEquals("0.1", defaults.appliedOption("waves_amplitude").orElseThrow());
		assertEquals("false", defaults.appliedOption("WAVING_WATER").orElseThrow());
		assertTrue(defaults.appliedOption("missing").isEmpty());
		Files.writeString(defaults.settingsFile(), "waves_amplitude=0.65\nWAVING_WATER=true\n");
		PackSession saved = PackSession.read(this.game, pack, "en_us");
		assertEquals("0.65", saved.appliedOption("waves_amplitude").orElseThrow());
		assertTrue(OptionValue.parse(saved.appliedOption("WAVING_WATER").orElseThrow()).asBoolean());
	}
}
