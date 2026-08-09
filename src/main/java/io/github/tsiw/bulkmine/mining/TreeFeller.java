package io.github.tsiw.bulkmine.mining;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import io.github.tsiw.bulkmine.registry.BlockWhitelist;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Flood fills a tree from the log that was just broken.
 *
 * <p>Two deliberate limits keep this from eating builds and from stalling the
 * server: only blocks at or above the broken log are followed, so cutting a
 * trunk halfway leaves the stump and never reaches logs buried underground,
 * and the search stops at a block count and a radius.
 */
public final class TreeFeller {
	private TreeFeller() {
	}

	/**
	 * Connected logs of exactly the same block, searched in all 26 directions
	 * because jungle and dark oak trunks join diagonally.
	 *
	 * @param origin the already-broken block, used as the search seed and as
	 *               the floor below which logs are left alone
	 */
	public static List<BlockPos> collectLogs(
			final Level level, final BlockPos origin, final Block log, final int maxLogs, final int radius) {
		List<BlockPos> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();

		seen.add(origin.immutable());
		queue.add(origin.immutable());

		while (!queue.isEmpty() && found.size() < maxLogs) {
			BlockPos current = queue.poll();

			for (BlockPos neighbour : neighbours(current)) {
				if (neighbour.getY() < origin.getY() || !seen.add(neighbour)) {
					continue;
				}
				if (chebyshev(origin, neighbour) > radius) {
					continue;
				}
				if (!level.getBlockState(neighbour).is(log)) {
					continue;
				}

				found.add(neighbour);
				if (found.size() >= maxLogs) {
					break;
				}
				queue.add(neighbour);
			}
		}

		return found;
	}

	/**
	 * Blocks on the config's leaf list within {@code distance} steps of any
	 * felled log. Player-placed (persistent) leaves are cut too -- that is the
	 * requested behaviour, so a hedge next to a tree will go with it.
	 */
	public static List<BlockPos> collectLeaves(
			final Level level, final List<BlockPos> logs, final int distance, final int maxLeaves) {
		List<BlockPos> found = new ArrayList<>();
		if (distance <= 0 || maxLeaves <= 0) {
			return found;
		}

		Set<BlockPos> seen = new HashSet<>(logs);
		Deque<BlockPos> frontier = new ArrayDeque<>(logs);

		for (int step = 0; step < distance && !frontier.isEmpty() && found.size() < maxLeaves; step++) {
			Deque<BlockPos> next = new ArrayDeque<>();

			while (!frontier.isEmpty() && found.size() < maxLeaves) {
				BlockPos current = frontier.poll();

				for (BlockPos neighbour : neighbours(current)) {
					if (!seen.add(neighbour)) {
						continue;
					}

					if (!BlockWhitelist.isLeaf(level.getBlockState(neighbour))) {
						continue;
					}

					found.add(neighbour);
					next.add(neighbour);
					if (found.size() >= maxLeaves) {
						break;
					}
				}
			}

			frontier = next;
		}

		return found;
	}

	/** All 26 surrounding positions. */
	private static List<BlockPos> neighbours(final BlockPos pos) {
		List<BlockPos> result = new ArrayList<>(26);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx != 0 || dy != 0 || dz != 0) {
						result.add(pos.offset(dx, dy, dz));
					}
				}
			}
		}
		return result;
	}

	private static int chebyshev(final BlockPos a, final BlockPos b) {
		return Math.max(Math.abs(a.getX() - b.getX()),
				Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
	}
}
