package dev.vitrail.sodium;

import org.lwjgl.system.MemoryUtil;

/**
 * A long read and written at an address that need not be a multiple of eight, which is where
 * {@link EntityMeshSerializer} finds every other vertex: the game's entity stride is thirty-six.
 * <p>
 * <strong>The plain accessors, because they are all the LWJGL this game ships has</strong>, and they
 * need nothing more: they are {@code Unsafe}'s own, which take any address. The other game's LWJGL
 * routes them through a backend that can hold them to their alignment, which is why its copy of this
 * class reads and writes through the unaligned ones instead.
 */
final class UnalignedLongs {

	private UnalignedLongs() {
	}

	static long get(long address) {
		return MemoryUtil.memGetLong(address);
	}

	static void put(long address, long value) {
		MemoryUtil.memPutLong(address, value);
	}
}
