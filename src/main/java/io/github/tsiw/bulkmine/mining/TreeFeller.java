package io.github.tsiw.bulkmine.mining;

import io.github.tsiw.bulkmine.registry.BlockWhitelist.TreeGroup;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Flood fills a tree outward from the block that was just broken.
 *
 * <p>Every search follows one tree's own blocks only, and every search but the
 * canopy around felled logs has a floor it never goes below: cutting a trunk
 * halfway leaves the stump, and logs buried underground are never reached.
 * Each also stops at a block count, and logs and roots at a horizontal radius.
 */
public final class TreeFeller {
	private TreeFeller() {
	}

	/** For {@link #collectLeaves}: no floor at all. */
	public static final int NO_FLOOR = Integer.MIN_VALUE;

	/**
	 * Whether a player placed this leaf. Only leaves can tell: the game marks
	 * the ones a player puts down so they never decay, and grown ones are not
	 * marked. Anything without that mark -- logs, roots, mushroom caps --
	 * counts as grown.
	 */
	public static boolean isPlaced(final BlockState state) {
		return state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT);
	}

	/**
	 * Connected logs of the group, searched in all 26 directions because jungle
	 * and dark oak trunks join diagonally.
	 *
	 * <p>The radius is horizontal only. Height is bounded by {@code maxLogs}
	 * already, and counting it as well cut the top off giant spruce and jungle
	 * trees, which grow to around 30 blocks.
	 *
	 * @param start        where the search begins, and the centre of the radius
	 * @param includeStart false when the game has already broken {@code start}
	 * @param floorY       logs below this height are left alone
	 */
	public static List<BlockPos> collectLogs(
			final Level level, final BlockPos start, final boolean includeStart, final TreeGroup group,
			final int floorY, final int maxLogs, final int radius) {
		List<BlockPos> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();

		BlockPos seed = start.immutable();
		seen.add(seed);
		queue.add(seed);
		if (includeStart) {
			found.add(seed);
		}

		while (!queue.isEmpty() && found.size() < maxLogs) {
			BlockPos current = queue.poll();

			for (BlockPos neighbour : neighbours(current)) {
				if (neighbour.getY() < floorY || !seen.add(neighbour)) {
					continue;
				}
				if (horizontal(start, neighbour) > radius || !group.isLog(level.getBlockState(neighbour))) {
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
	 * Leaves passing {@code isLeaf} within {@code distance} steps of any seed,
	 * following leaves only.
	 *
	 * @param floorY {@link #NO_FLOOR} around felled logs, where a canopy hangs
	 *               below the cut; the hit leaf's height when clearing leaves
	 *               on their own, so that never digs downward
	 */
	public static List<BlockPos> collectLeaves(
			final Level level, final Collection<BlockPos> seeds, final Predicate<BlockState> isLeaf,
			final int floorY, final int distance, final int maxLeaves) {
		List<BlockPos> found = new ArrayList<>();
		if (distance <= 0 || maxLeaves <= 0) {
			return found;
		}

		Set<BlockPos> seen = new HashSet<>(seeds);
		Deque<BlockPos> frontier = new ArrayDeque<>(seeds);

		for (int step = 0; step < distance && !frontier.isEmpty() && found.size() < maxLeaves; step++) {
			Deque<BlockPos> next = new ArrayDeque<>();

			while (!frontier.isEmpty() && found.size() < maxLeaves) {
				BlockPos current = frontier.poll();

				for (BlockPos neighbour : neighbours(current)) {
					if (neighbour.getY() < floorY || !seen.add(neighbour)) {
						continue;
					}
					if (!isLeaf.test(level.getBlockState(neighbour))) {
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

	/**
	 * What a search from a hit root found.
	 *
	 * @param roots    the roots within reach, at or above the hit one
	 * @param firstLog the nearest of the group's logs those roots led to, or
	 *                 null when they led to none
	 */
	public record RootSearch(List<BlockPos> roots, BlockPos firstLog) {
	}

	/**
	 * Follows roots outward from the one that was hit, at or above its height
	 * and up to {@code radius} blocks away horizontally, watching for the
	 * tree's trunk. Stops as soon as the trunk turns up, since the caller
	 * fells the tree from there instead.
	 *
	 * <p>The radius is horizontal only, the same as for logs. It used to be a
	 * count of steps along the roots, and the way back from the low end of a
	 * tall mangrove's root climbs as much as it goes in, so six steps fell
	 * short of trunks that were only six blocks away. Height needs no limit:
	 * the hit root is the floor, and grown roots never reach above their
	 * trunk's base.
	 */
	public static RootSearch searchRoots(
			final Level level, final BlockPos origin, final TreeGroup group, final int radius, final int maxRoots) {
		List<BlockPos> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		seen.add(origin.immutable());
		queue.add(origin.immutable());

		// Breadth first, so the trunk found is the nearest one along the roots.
		while (!queue.isEmpty()) {
			BlockPos current = queue.poll();

			for (BlockPos neighbour : neighbours(current)) {
				if (neighbour.getY() < origin.getY() || !seen.add(neighbour)) {
					continue;
				}
				if (horizontal(origin, neighbour) > radius) {
					continue;
				}

				BlockState state = level.getBlockState(neighbour);
				if (group.isLog(state)) {
					return new RootSearch(found, neighbour);
				}
				if (!group.isRoot(state) || found.size() >= maxRoots) {
					continue;
				}

				found.add(neighbour);
				queue.add(neighbour);
			}
		}

		return new RootSearch(found, null);
	}

	/**
	 * The roots of a tree being felled from one of them: every root joined to
	 * the seeds through other roots, at or above {@code floorY}, within the
	 * horizontal radius of the trunk.
	 *
	 * <p>Roots cannot be told apart by tree, so where a neighbour's roots
	 * touch these, the part inside the radius goes too. Its trunk never does:
	 * logs are only ever found through other logs.
	 */
	public static List<BlockPos> collectTreeRoots(
			final Level level, final Collection<BlockPos> seeds, final TreeGroup group, final int floorY,
			final BlockPos centre, final int radius, final int maxRoots) {
		List<BlockPos> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>(seeds);
		Deque<BlockPos> queue = new ArrayDeque<>(seeds);

		while (!queue.isEmpty() && found.size() < maxRoots) {
			BlockPos current = queue.poll();

			for (BlockPos neighbour : neighbours(current)) {
				if (neighbour.getY() < floorY || !seen.add(neighbour)) {
					continue;
				}
				if (horizontal(centre, neighbour) > radius || !group.isRoot(level.getBlockState(neighbour))) {
					continue;
				}

				found.add(neighbour);
				if (found.size() >= maxRoots) {
					break;
				}
				queue.add(neighbour);
			}
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

	private static int horizontal(final BlockPos a, final BlockPos b) {
		return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ()));
	}
}
