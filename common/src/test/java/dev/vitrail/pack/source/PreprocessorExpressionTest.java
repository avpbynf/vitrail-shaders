package dev.vitrail.pack.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.vitrail.pack.option.SettingSet;

/**
 * Pastel puts a {@code shaders.properties} function into a GLSL {@code #if}. The reference's
 * preprocessor answers an undefined call with nought and skips the group. Writing it as taken
 * instead defined {@code fogColor} a second time beside the overworld one, and the compiler
 * refused {@code world0/deferred1} for redefinition.
 */
class PreprocessorExpressionTest {

	@TempDir
	Path pack;

	@Test
	void answersAnUndefinedCallAsFalse() {
		PreprocessorExpression.Verdict verdict = PreprocessorExpression.decide(
				"(in(biome, BIOME_SOUL_SAND_VALLEY)", Map.of());

		assertEquals(Optional.of(false), verdict.taken());
		assertTrue(verdict.unreadable());
		assertFalse(verdict.fractional());
	}

	@Test
	void anUndefinedCallWinsOverTheRestOfTheExpression() {
		PreprocessorExpression.Verdict verdict = PreprocessorExpression.decide("in(1) || 1", Map.of());

		assertEquals(Optional.of(false), verdict.taken());
		assertTrue(verdict.unreadable());
	}

	@Test
	void aNameThePackDefinesIsStillReadAsItsValue() {
		PreprocessorExpression.Verdict verdict = PreprocessorExpression.decide("FOO", Map.of("FOO", "1"));

		assertEquals(Optional.of(true), verdict.taken());
		assertFalse(verdict.unreadable());
	}

	@Test
	void anExpressionThatCannotBeWorkedOutStaysUndecidable() {
		PreprocessorExpression.Verdict verdict = PreprocessorExpression.decide("1 & 0.5", Map.of());

		assertTrue(verdict.taken().isEmpty());
		assertFalse(verdict.unreadable());
	}

	@Test
	void writesTheCallOutAsADecidedDirective() throws IOException {
		Path shaders = Files.createDirectories(this.pack.resolve("shaders"));
		Files.writeString(shaders.resolve("gbuffers.fsh"), "#version 460 core\n"
				+ "#if (in(biome, BIOME_SOUL_SAND_VALLEY)\n"
				+ "vec3 fogColor = vec3(1.0);\n"
				+ "#endif\n");

		try (ShaderPackSource source = ShaderPackSource.open(this.pack)) {
			IncludeExpander.ExpandedUnit unit = new IncludeExpander(source, SettingSet.defaults())
					.expand(source.file("gbuffers.fsh").orElseThrow());

			assertTrue(unit.lines().stream().anyMatch(line -> line.trim().equals("#if 0")));
			assertFalse(unit.lines().stream().anyMatch(line -> line.contains("in(biome")));
		}
	}
}
