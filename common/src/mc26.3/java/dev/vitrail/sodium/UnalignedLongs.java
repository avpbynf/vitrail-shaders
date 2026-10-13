package dev.vitrail.sodium;

import org.lwjgl.system.MemoryUtil;

/**
 * A long read and written at an address that need not be a multiple of eight, which is where
 * {@link EntityMeshSerializer} finds every other vertex: the game's entity stride is thirty-six.
 * <p>
 * <strong>The unaligned accessors and not {@code memGetLong}</strong>, because the LWJGL this game
 * ships routes both through a backend, and its FFM backend holds the aligned ones to their alignment
 * when LWJGL's debug flag is on: the second vertex of the first mob would throw. On the machines the
 * game runs on, the unaligned ones compile to the same move as the aligned ones and time the same,
 * measured off-game over a field of mobs.
 */
final class UnalignedLongs {

	private UnalignedLongs() {
	}

	static long get(long address) {
		return MemoryUtil.memGetLongUnaligned(address);
	}

	static void put(long address, long value) {
		MemoryUtil.memPutLongUnaligned(address, value);
	}
}
