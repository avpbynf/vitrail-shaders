package dev.vitrail.sodium;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import org.junit.jupiter.api.Test;

/**
 * Holds the game's entity vertex to the thirty-six bytes {@code EntityMeshSerializer.copy} moves as
 * four longs and an int.
 * <p>
 * Nothing else would notice a game that changed it: the copy falls back to {@code memCopy}, every
 * mob still draws right, and the serializer only gets slower.
 */
class EntityMeshSerializerTest {

	@Test
	void theGameWritesThirtySixBytes() {
		assertEquals(4 * Long.BYTES + Integer.BYTES, DefaultVertexFormat.ENTITY.getVertexSize(),
				"the game's entity vertex, which EntityMeshSerializer copies as four longs and an int");
	}
}
