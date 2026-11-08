package dev.vitrail;

import org.joml.Vector3dc;
import org.joml.Vector3f;

/**
 * Which Sodium is in front, as far as a chunk pass's region offset is concerned.
 * <p>
 * <strong>0.9.3-beta.2 moved the camera's fraction out of the region offset.</strong> Up to
 * 0.9.3-beta.1 a chunk pass was told where its region stands relative to the camera's exact
 * position, worked out on the CPU by {@code DefaultChunkRenderer.getCameraTranslation} taking the
 * camera's whole block and its fraction as two arguments. From beta.2 that method takes the block
 * alone, the fraction rides beside the offset in {@code u_CameraOffset} instead, and Sodium's own
 * {@code block_layer_opaque.vsh} subtracts it: {@code position = (_vert_position + translation) -
 * u_CameraOffset}. Its {@code CameraTransform.fractional} lost the {@code Math.copySign(128.0f, f)}
 * quantisation in the same change.
 * <p>
 * A pack's chunk stage is not Sodium's, so it never sees that line. Subtracting nothing puts every
 * section on a whole block: the world trails the camera by up to one block and jumps back a whole
 * one at every boundary crossed, which reads as a world that moves in steps rather than as a frame
 * rate that stutters. The two releases want two different numbers and the head is one text, so the
 * answer is settled here and the uniform carries nought on the older one
 * ({@link dev.vitrail.uniform.WorldState#sodiumCameraOffset()}).
 * <p>
 * <strong>The arithmetic is Sodium's, not this engine's idea of it.</strong> Its fractional part
 * truncates toward nought, {@code (float) (d - (int) d)}, where the {@code cameraPositionFract} a
 * pack reads floors. On positive coordinates the two agree, which is why the difference is worth
 * spelling out: west and north of the origin a floor leaves a fraction of nought point three where
 * Sodium wants minus nought point seven, and the terrain would sit one block out on exactly two of
 * the three axes.
 * <p>
 * Read off the Sodium version and not off a byte of its shader, since nothing in this module may
 * name a Sodium class. The rule is the release the change landed in: 0.9.3-beta.2 and everything
 * after it moved, everything before it did not, and a version this cannot read is answered with
 * "did not" plus one line in the log, because inventing a displacement on a Sodium nobody here has
 * measured is the worse of the two ways to be wrong.
 */
public final class SodiumTerrainOffset {

	/** The base version the change landed in, as three numbers. */
	private static final int[] MOVED_AT = {0, 9, 3};

	/** The pre-release of that base the change landed in, and the number within it. */
	private static final String MOVED_AT_STAGE = "beta";
	private static final int MOVED_AT_NUMBER = 2;

	/** Beta's place in the order of pre-releases, so that a name can be ranked without a table. */
	private static final int STAGE_ALPHA = 0;
	private static final int STAGE_BETA = 1;
	private static final int STAGE_LATER = 2;

	/** Asked of the loaders by id: the one mod whose release decides the answer. */
	private static final String SODIUM = "sodium";

	private static volatile Boolean moved;

	private SodiumTerrainOffset() {
	}

	/** Whether the Sodium in front leaves the camera's fraction out of a region offset. */
	public static boolean moved() {
		Boolean known = moved;
		if (known == null) {
			String version = version();
			Boolean read = read(version);
			if (read == null) {
				Vitrail.logger().warn("Sodium's version reads {} and this engine cannot tell which "
						+ "side of 0.9.3-beta.2 that is, so a chunk pass takes the camera's fraction "
						+ "out of its region offset as it always has: a world that moves in whole "
						+ "blocks on a Sodium newer than that is the shape of what is left to fix "
						+ "here", version);
			}

			known = Boolean.TRUE.equals(read);
			moved = known;
			Vitrail.logger().info("Sodium {}: a chunk pass {} the camera's fraction of its region "
					+ "offset", version, known ? "subtracts" : "takes");
		}

		return known;
	}

	/**
	 * Writes the fraction a chunk pass has to take off its region offset, or nought where the offset
	 * already carries it.
	 *
	 * @param camera the camera this frame draws with, unshifted, which is the position Sodium
	 *               measured its own offsets from
	 * @param out    the three floats to write, reused rather than returned
	 */
	public static void write(Vector3dc camera, Vector3f out) {
		if (!moved()) {
			out.set(0.0F, 0.0F, 0.0F);

			return;
		}

		out.set(fraction(camera.x()), fraction(camera.y()), fraction(camera.z()));
	}

	/**
	 * Sodium's own arithmetic, and truncating rather than flooring is the whole of it.
	 * <p>
	 * Package visible for the same reason {@link #moved(String)} is: a fraction of minus nought
	 * point seven where a floor would have said nought point three is a whole block of terrain on
	 * two of the three axes, and it is one line to check.
	 */
	static float fraction(double value) {
		return (float) (value - (int) value);
	}

	private static String version() {
		return Vitrail.platform().modVersion(SODIUM);
	}

	/**
	 * The rule, on a version string as a loader writes it: {@code 0.9.3-beta.4+mc26.3}.
	 * <p>
	 * Package visible rather than private so that the answer can be checked without a game in front
	 * of it, which is the only way this rule ever gets tested against a release nobody here runs.
	 * Silent on a version it cannot read, and {@link #moved()} is where that becomes a line.
	 */
	static boolean moved(String version) {
		return Boolean.TRUE.equals(read(version));
	}

	/** The rule, or null where the string is not a version this can compare. */
	private static Boolean read(String version) {
		String bare = version;
		int plus = bare.indexOf('+');
		if (plus >= 0) {
			bare = bare.substring(0, plus);
		}

		String base = bare;
		String stage = "";
		int dash = bare.indexOf('-');
		if (dash >= 0) {
			base = bare.substring(0, dash);
			stage = bare.substring(dash + 1);
		}

		int[] numbers = numbers(base);
		if (numbers == null) {
			return null;
		}

		for (int axis = 0; axis < MOVED_AT.length; axis++) {
			// A version that stops short of three numbers reads as trailing zeroes: 0.9 is older
			// than 0.9.3, and no release spells a trailing zero out.
			int number = axis < numbers.length ? numbers[axis] : 0;
			if (number != MOVED_AT[axis]) {
				return number > MOVED_AT[axis];
			}
		}

		// The base is the release the change landed in, so what is left is the pre-release: the
		// released 0.9.3 has it, and so does every beta from the second on.
		if (stage.isEmpty()) {
			return Boolean.TRUE;
		}

		return stageAfter(stage);
	}

	/** Whether a pre-release of 0.9.3 comes at or after {@code beta.2}. */
	private static boolean stageAfter(String stage) {
		int dot = stage.indexOf('.');
		String name = dot >= 0 ? stage.substring(0, dot) : stage;
		int rank = rank(name);
		if (rank != STAGE_BETA) {
			return rank > STAGE_BETA;
		}

		int number = 0;
		if (dot >= 0) {
			int[] parsed = numbers(stage.substring(dot + 1));
			number = parsed == null || parsed.length == 0 ? 0 : parsed[0];
		}

		return number >= MOVED_AT_NUMBER;
	}

	/** Alpha before beta before anything else, which is where a release candidate sits. */
	private static int rank(String name) {
		if (name.equalsIgnoreCase("alpha")) {
			return STAGE_ALPHA;
		}

		if (name.equalsIgnoreCase(MOVED_AT_STAGE)) {
			return STAGE_BETA;
		}

		return STAGE_LATER;
	}

	/** The dot separated numbers of one part, or null where it is not made of them. */
	private static int[] numbers(String text) {
		if (text.isEmpty()) {
			return new int[0];
		}

		String[] parts = text.split("\\.", -1);
		int[] numbers = new int[parts.length];
		for (int index = 0; index < parts.length; index++) {
			try {
				numbers[index] = Integer.parseInt(parts[index]);
			} catch (NumberFormatException notANumber) {
				return null;
			}
		}

		return numbers;
	}
}
