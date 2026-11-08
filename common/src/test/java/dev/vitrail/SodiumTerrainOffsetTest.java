package dev.vitrail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Holds {@link SodiumTerrainOffset} to the two things it has to get right without a game in front of
 * it: which Sodium releases moved the camera's fraction out of a chunk pass's region offset, and the
 * arithmetic that fraction is measured by.
 * <p>
 * Written against the releases that really exist rather than against a shape, because the rule is
 * the difference between a world that follows the camera and a world that moves in whole blocks, and
 * a version string a loader writes is the only thing on the machine that says which one is in force.
 * The two that matter most are a pair: 0.9.3-beta.1 and 0.9.3-beta.2 are one beta apart and want
 * opposite answers.
 */
class SodiumTerrainOffsetTest {

	/** Sodium releases up to and including beta.1, which put the fraction inside the offset. */
	private static final List<String> UNMOVED = List.of(
			"0.9.1",
			"0.9.2+mc26.3",
			"0.9.2-beta.1+mc26.2",
			"0.9.3-alpha.1+mc26.3",
			"0.9.3-beta.1+mc26.3");

	/** The release the change landed in, and everything after it. */
	private static final List<String> MOVED = List.of(
			"0.9.3-beta.2+mc26.3",
			"0.9.3-beta.3+mc26.3",
			"0.9.3-beta.4+mc26.3",
			"0.9.3+mc26.3",
			"0.9.4-beta.1+mc26.3",
			"0.9.10-beta.1+mc26.3",
			"0.10.0-beta.1+mc26.3",
			"1.0.0");

	@Test
	void leavesTheOffsetAloneBeforeTheChange() {
		for (String version : UNMOVED) {
			assertFalse(SodiumTerrainOffset.moved(version), version + " puts the fraction in the offset");
		}
	}

	@Test
	void takesTheFractionOffFromTheChangeOnwards() {
		for (String version : MOVED) {
			assertTrue(SodiumTerrainOffset.moved(version), version + " leaves the fraction to the pass");
		}
	}

	/**
	 * A release candidate sorts after every beta of the same base, whatever it is called: a name is
	 * only ever ranked against beta, and anything that is not alpha or beta is later than both.
	 */
	@Test
	void readsAnUnknownPreReleaseAsLaterThanBeta() {
		assertTrue(SodiumTerrainOffset.moved("0.9.3-rc.1"));
		assertTrue(SodiumTerrainOffset.moved("0.9.3-pre2"));
		assertFalse(SodiumTerrainOffset.moved("0.9.3-beta"));
	}

	/**
	 * The one answer that must not be invented. A version nobody here can read keeps the behaviour
	 * every release before the change had, and {@link SodiumTerrainOffset#moved()} is what turns
	 * that silence into a line in the log.
	 */
	@Test
	void refusesToGuessAtAStringItCannotRead() {
		assertFalse(SodiumTerrainOffset.moved("unknown"));
		assertFalse(SodiumTerrainOffset.moved(""));
		assertFalse(SodiumTerrainOffset.moved("snapshot"));
	}

	/**
	 * Truncated toward nought, which is Sodium's {@code CameraTransform.fractional} and not
	 * {@code Math.floor}. West or north of the origin the two disagree by a whole block.
	 */
	@Test
	void measuresTheFractionTheWaySodiumDoes() {
		assertEquals(0.7F, SodiumTerrainOffset.fraction(3.7), 1.0E-6F);
		assertEquals(-0.7F, SodiumTerrainOffset.fraction(-3.7), 1.0E-6F);
		assertEquals(0.0F, SodiumTerrainOffset.fraction(5.0), 1.0E-6F);
		assertEquals(-0.5F, SodiumTerrainOffset.fraction(-0.5), 1.0E-6F);
		assertEquals(0.0F, SodiumTerrainOffset.fraction(0.0), 1.0E-6F);
	}
}
