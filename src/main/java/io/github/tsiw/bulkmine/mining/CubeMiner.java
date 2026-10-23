package io.github.tsiw.bulkmine.mining;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * The dig shape: an axis-aligned cube centred on the block you broke, with
 * every layer below that block dropped unless the player asked for them.
 *
 * <p>Nothing here rotates with the player's view. That is what makes the shape
 * behave sensibly in awkward terrain: a wall mined from the side leaves the
 * layer towards the player as air, a floor mined from above leaves the layer
 * above as air, and a pocket in uneven ground is treated exactly like both.
 * With the default size of 3 that leaves 3x3x2 = 18 candidate positions, or
 * the whole 3x3x3 = 27 with the layers below.
 */
public final class CubeMiner {
	private CubeMiner() {
	}

	/**
	 * Candidate positions excluding the origin, which vanilla already broke.
	 *
	 * @param below whether to take the layers below the origin as well
	 */
	public static List<BlockPos> collect(final BlockPos origin, final int size, final boolean below) {
		int radius = (size - 1) / 2;
		List<BlockPos> positions = new ArrayList<>();

		for (int dy = below ? -radius : 0; dy <= radius; dy++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					positions.add(origin.offset(dx, dy, dz));
				}
			}
		}

		return positions;
	}
}
