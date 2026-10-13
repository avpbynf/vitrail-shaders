package dev.vitrail.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.CodeAttribute;
import java.util.Locale;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

/**
 * Holds {@link EntityFrame#tangent} to what it gives and to what it costs: the frame a face of the
 * game's own models gets, every word over a fixed spread of inputs, and every method small enough
 * for C2 to inline.
 * <p>
 * The last is not something a mob on screen would ever show. Over the limit the arithmetic still
 * comes out right and the serializer only gets slower, so nothing but a count of bytecode notices.
 */
class EntityFrameTest {

	/** C2's default {@code FreqInlineSize}: the most bytecode of a hot method it inlines. */
	private static final int INLINED = 325;

	/**
	 * Every word {@link #everyWordStaysPut} draws, folded in order as {@code 31 * digest + word}.
	 * Thirty-one is odd, so a change to any one word changes the digest. Pinned rather than worked
	 * out again so that no second copy of the arithmetic lives here to drift from the first: a change
	 * to the arithmetic that is meant writes its new digest here.
	 */
	private static final long WORDS = 0xCE76690FCB1B54D0L;

	@Test
	void everyMethodInlines() throws IOException {
		ClassModel frame;
		try (InputStream in = EntityFrame.class.getResourceAsStream("EntityFrame.class")) {
			assertNotNull(in, "EntityFrame's class file");
			frame = ClassFile.of().parse(in.readAllBytes());
		}

		for (MethodModel method : frame.methods()) {
			String name = method.methodName().stringValue();
			method.findAttribute(Attributes.code()).map(CodeAttribute::codeLength).ifPresent(length ->
					assertTrue(length <= INLINED, name + " is " + length + " bytes of bytecode, over the "
							+ INLINED + " C2 inlines of a hot method"));
		}
	}

	/** A face seen head on, its skin's U along X and V down the face as the game lays skins out. */
	@Test
	void aFaceRunsAlongU() {
		assertEquals(EntityFrame.pack(1.0F, 0.0F, 0.0F, 1.0F), EntityFrame.tangent(0.0F, 0.0F, 1.0F,
				false,
				0.0F, 1.0F, 0.0F, 0.0F, 0.0F,
				1.0F, 1.0F, 0.0F, 1.0F, 0.0F,
				1.0F, 0.0F, 0.0F, 1.0F, 1.0F));
	}

	/** The same face with its skin mirrored, as a model part marked mirror draws it. */
	@Test
	void aMirroredFaceTurnsTheThirdAxis() {
		assertEquals(EntityFrame.pack(-1.0F, 0.0F, 0.0F, -1.0F), EntityFrame.tangent(0.0F, 0.0F, 1.0F,
				false,
				1.0F, 1.0F, 0.0F, 0.0F, 0.0F,
				0.0F, 1.0F, 0.0F, 1.0F, 0.0F,
				0.0F, 0.0F, 0.0F, 1.0F, 1.0F));
	}

	/** A face whose corners lean off its normal leans its tangent with them, unless flattened. */
	@Test
	void flatteningTakesTheLeanOut() {
		float lean = (float) (1.0 / Math.sqrt(5.0));
		assertEquals(EntityFrame.pack(2.0F * lean, 0.0F, -lean, 1.0F), leaningFace(false));
		assertEquals(EntityFrame.pack(1.0F, 0.0F, 0.0F, 1.0F), leaningFace(true));
	}

	/** Three corners on one texture coordinate have no gradient, and get the axis and not a NaN. */
	@Test
	void noGradientGivesTheAxis() {
		assertEquals(EntityFrame.FLAT, EntityFrame.tangent(0.0F, 0.0F, 1.0F, false,
				0.0F, 1.0F, 0.0F, 0.5F, 0.5F,
				1.0F, 1.0F, 0.0F, 0.5F, 0.5F,
				1.0F, 0.0F, 0.0F, 0.5F, 0.5F));
	}

	/** A face so small its tangent would be rounding error gets the axis too. */
	@Test
	void aSpeckGivesTheAxis() {
		assertEquals(EntityFrame.FLAT, EntityFrame.tangent(0.0F, 0.0F, 1.0F, false,
				0.0F, 1.0E-7F, 0.0F, 0.0F, 0.0F,
				1.0E-7F, 1.0E-7F, 0.0F, 1.0F, 0.0F,
				1.0E-7F, 0.0F, 0.0F, 1.0F, 1.0F));
	}

	@Test
	void everyWordStaysPut() {
		SplittableRandom random = new SplittableRandom(42L);
		float[] corners = new float[15];
		long digest = 0L;
		for (int i = 0; i < 2_000_000; i++) {
			int kind = i % 7;
			for (int k = 0; k < corners.length; k++) {
				corners[k] = switch (kind) {
					case 0 -> (float) random.nextDouble(-2.0, 2.0);
					// An entity model is built in sixteenths of a block.
					case 1 -> (float) Math.floor(random.nextDouble(-64.0, 64.0)) / 16.0F;
					// Corners a millionth apart over ordinary texture coordinates: a tangent whose
					// square falls on both sides of TINY, a quarter of them at or under it.
					case 2 -> k % 5 < 3 ? (float) random.nextDouble(-1.0E-6, 1.0E-6)
							: (float) random.nextDouble(-1.0, 1.0);
					case 3 -> random.nextInt(3) == 0 ? 0.0F : (float) random.nextDouble(-1.0, 1.0);
					case 4 -> (float) random.nextDouble(-3.0E4, 3.0E4);
					// Any bits at all, NaN and the infinities among them.
					case 5 -> Float.intBitsToFloat(random.nextInt());
					// Three corners on the first one's texture coordinate: no gradient to invert.
					default -> k % 5 < 3 || k < 5 ? (float) random.nextDouble(-2.0, 2.0)
							: corners[k % 5];
				};
			}
			float x = (float) random.nextDouble(-1.0, 1.0);
			float y = (float) random.nextDouble(-1.0, 1.0);
			float z = (float) random.nextDouble(-1.0, 1.0);
			float length = (float) Math.sqrt(x * x + y * y + z * z);
			if (length > 0.0F) {
				x /= length;
				y /= length;
				z /= length;
			}

			for (boolean flatten : new boolean[] {false, true}) {
				digest = 31L * digest + EntityFrame.tangent(x, y, z, flatten, corners[0], corners[1],
						corners[2], corners[3], corners[4], corners[5], corners[6], corners[7],
						corners[8], corners[9], corners[10], corners[11], corners[12], corners[13],
						corners[14]);
			}
		}
		assertEquals(WORDS, digest, "the words of four million tangents, which fold to 0x"
				+ Long.toHexString(digest).toUpperCase(Locale.ROOT));
	}

	/** The face of {@link #aFaceRunsAlongU} with its first corner pulled half a block toward the eye. */
	private static int leaningFace(boolean flatten) {
		return EntityFrame.tangent(0.0F, 0.0F, 1.0F, flatten,
				0.0F, 1.0F, 0.5F, 0.0F, 0.0F,
				1.0F, 1.0F, 0.0F, 1.0F, 0.0F,
				1.0F, 0.0F, 0.0F, 1.0F, 1.0F);
	}
}
