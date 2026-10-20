package io.github.tsiw.bulkmine.mining;

import io.github.tsiw.bulkmine.Role;
import io.github.tsiw.bulkmine.config.BulkMineConfig;
import io.github.tsiw.bulkmine.config.ConfigManager;
import io.github.tsiw.bulkmine.registry.BlockWhitelist;
import io.github.tsiw.bulkmine.registry.BlockWhitelist.TreeGroup;
import io.github.tsiw.bulkmine.registry.ToolRegistry;
import io.github.tsiw.bulkmine.state.PlayerSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns one ordinary block break into a bulk break.
 *
 * <p>Hangs off {@link PlayerBlockBreakEvents}: BEFORE only records the tool as
 * it was at that instant, AFTER does the work. Recording matters because by
 * the time AFTER runs vanilla has already spent a point of durability, and a
 * tool on its last point is gone from the player's hand entirely.
 */
public final class BreakHandler {
	private BreakHandler() {
	}

	/** Set while we are breaking, so our own breaks do not recurse. */
	private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> Boolean.FALSE);

	/** The main-hand stack as it was before vanilla damaged it, per player. */
	private static final Map<UUID, ItemStack> TOOL_SNAPSHOTS = new ConcurrentHashMap<>();

	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register(BreakHandler::rememberTool);
		PlayerBlockBreakEvents.AFTER.register(BreakHandler::onBlockBroken);
	}

	private static boolean rememberTool(
			final Level level, final Player player, final BlockPos pos,
			final BlockState state, final BlockEntity blockEntity) {
		if (!BUSY.get() && player instanceof ServerPlayer serverPlayer) {
			TOOL_SNAPSHOTS.put(serverPlayer.getUUID(), serverPlayer.getMainHandItem().copy());
		}
		return true;
	}

	private static void onBlockBroken(
			final Level level, final Player player, final BlockPos pos,
			final BlockState state, final BlockEntity blockEntity) {
		if (BUSY.get()) {
			return;
		}
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		BulkMineConfig.Server config = ConfigManager.get().server;
		if (!config.enabled || !PlayerSettings.isEnabled(serverPlayer)) {
			return;
		}
		if (!config.allowWhileSneaking && serverPlayer.isShiftKeyDown()) {
			return;
		}

		ItemStack tool = TOOL_SNAPSHOTS.getOrDefault(serverPlayer.getUUID(), serverPlayer.getMainHandItem());
		// May be empty: hoes and shears hold no role, yet clear leaves.
		Set<Role> roles = ToolRegistry.rolesOf(tool);

		List<BlockPos> targets = selectTargets(
				serverLevel, pos, state, roles, config, PlayerSettings.cubeSize(serverPlayer), tool);
		if (targets.isEmpty()) {
			return;
		}

		BUSY.set(Boolean.TRUE);
		try {
			BreakExecutor.breakAll(serverLevel, serverPlayer, pos, targets, tool);
		} finally {
			BUSY.set(Boolean.FALSE);
		}
	}

	/**
	 * Picks what one swing breaks, by what was hit: a log fells its tree, a
	 * root clears roots or fells the tree they lead to, a leaf clears leaves,
	 * and anything else on a cube list digs a cube. Trees are checked first,
	 * so a block the file gives to a tree is never also dug as a cube.
	 */
	private static List<BlockPos> selectTargets(
			final ServerLevel level, final BlockPos origin, final BlockState state,
			final Set<Role> roles, final BulkMineConfig.Server config, final int cubeSize,
			final ItemStack tool) {
		// A block the tool cannot harvest drops nothing, so breaking it in bulk
		// would only destroy it -- a stone pickaxe would clear a vein of diamond
		// ore and leave nothing behind. Vanilla still breaks the one block the
		// player aimed at; the sweep stops there.
		if (config.requireCorrectTool && !BreakExecutor.canHarvest(state, tool)) {
			return List.of();
		}

		List<BlockPos> targets;
		TreeGroup logGroup = BlockWhitelist.groupForLog(state);
		TreeGroup rootGroup = logGroup == null ? BlockWhitelist.groupForRoot(state) : null;
		List<TreeGroup> leafGroups = logGroup == null && rootGroup == null
				? BlockWhitelist.groupsForLeaf(state) : List.of();

		if (logGroup != null) {
			if (!roles.contains(Role.AXE)) {
				return List.of();
			}
			targets = fell(level, origin, logGroup, config);
		} else if (rootGroup != null) {
			if (!suits(tool, state)) {
				return List.of();
			}
			targets = fromRoot(level, origin, rootGroup, roles, config);
		} else if (!leafGroups.isEmpty()) {
			if (!suits(tool, state)) {
				return List.of();
			}
			targets = fromLeaf(level, origin, state, leafGroups, config);
		} else {
			Set<Role> required = BlockWhitelist.cubeRolesFor(state);
			if (required.isEmpty() || !overlaps(required, roles)) {
				return List.of();
			}

			targets = new ArrayList<>();
			for (BlockPos candidate : CubeMiner.collect(origin, cubeSize)) {
				Set<Role> needed = BlockWhitelist.cubeRolesFor(level.getBlockState(candidate));
				if (!needed.isEmpty() && overlaps(needed, roles)) {
					targets.add(candidate);
				}
			}
		}

		return config.requireCorrectTool ? harvestable(level, targets, tool) : targets;
	}

	/**
	 * Whether this is the tool for clearing leaves or roots from: the one the
	 * game itself counts as right for the block -- a hoe for leaves and wart,
	 * an axe for mushroom caps and mangrove roots -- or shears on leaves.
	 * Swords are fast on leaves too but deliberately do not count, or a missed
	 * swing in a fight would clear the canopy.
	 */
	private static boolean suits(final ItemStack tool, final BlockState state) {
		if (tool.isCorrectToolForDrops(state)) {
			return true;
		}
		return state.is(BlockTags.LEAVES) && tool.is(ConventionalItemTags.SHEAR_TOOLS);
	}

	/** Drops the targets this tool would break for nothing. */
	private static List<BlockPos> harvestable(
			final ServerLevel level, final List<BlockPos> targets, final ItemStack tool) {
		List<BlockPos> kept = new ArrayList<>(targets.size());
		for (BlockPos pos : targets) {
			if (BreakExecutor.canHarvest(level.getBlockState(pos), tool)) {
				kept.add(pos);
			}
		}
		return kept;
	}

	/** The tree above the cut log, and its own canopy. */
	private static List<BlockPos> fell(
			final ServerLevel level, final BlockPos origin, final TreeGroup group,
			final BulkMineConfig.Server config) {
		List<BlockPos> logs = TreeFeller.collectLogs(
				level, origin, false, group, origin.getY(), config.maxLogs, config.treeRadius);

		if (!config.breakLeaves) {
			return logs;
		}

		// Seed the leaf search from the broken log too, otherwise felling a
		// one-block trunk would leave its canopy floating.
		List<BlockPos> leafSeeds = new ArrayList<>(logs);
		leafSeeds.add(origin);

		List<BlockPos> targets = new ArrayList<>(logs);
		targets.addAll(TreeFeller.collectLeaves(level, leafSeeds, grownLeafOf(List.of(group), config),
				TreeFeller.NO_FLOOR, config.leafDistance, config.maxLeaves));
		return targets;
	}

	/**
	 * Roots on their own, or -- when they lead to the tree's trunk within
	 * reach -- the whole tree from the height of the hit root up, centred on
	 * that trunk. Hitting the trunk leaves the roots; hitting a root takes
	 * the tree with it. Which one happens is the player's choice of where to
	 * swing.
	 */
	private static List<BlockPos> fromRoot(
			final ServerLevel level, final BlockPos origin, final TreeGroup group,
			final Set<Role> roles, final BulkMineConfig.Server config) {
		TreeFeller.RootSearch search = TreeFeller.searchRoots(
				level, origin, group, config.rootRadius, config.maxRoots);
		// Felling takes logs, which needs the same axe role as cutting a log.
		if (search.firstLog() == null || !roles.contains(Role.AXE)) {
			return search.roots();
		}

		BlockPos trunk = search.firstLog();
		int floor = origin.getY();
		List<BlockPos> logs = TreeFeller.collectLogs(
				level, trunk, true, group, floor, config.maxLogs, config.treeRadius);

		List<BlockPos> rootSeeds = new ArrayList<>(logs);
		rootSeeds.add(origin);

		List<BlockPos> targets = new ArrayList<>(logs);
		targets.addAll(TreeFeller.collectTreeRoots(
				level, rootSeeds, group, floor, trunk, config.treeRadius, config.maxRoots));
		if (config.breakLeaves) {
			targets.addAll(TreeFeller.collectLeaves(level, logs, grownLeafOf(List.of(group), config),
					TreeFeller.NO_FLOOR, config.leafDistance, config.maxLeaves));
		}
		return targets;
	}

	/**
	 * Leaves joined to the hit one, at or above it. Starting from a placed
	 * leaf reaches grown ones too; starting from a grown one never reaches a
	 * placed one, unless the distinction is switched off.
	 */
	private static List<BlockPos> fromLeaf(
			final ServerLevel level, final BlockPos origin, final BlockState state,
			final List<TreeGroup> groups, final BulkMineConfig.Server config) {
		Predicate<BlockState> isLeaf = TreeFeller.isPlaced(state)
				? candidate -> isLeafOf(groups, candidate)
				: grownLeafOf(groups, config);
		return TreeFeller.collectLeaves(
				level, List.of(origin), isLeaf, origin.getY(), config.leafDistance, config.maxLeaves);
	}

	/**
	 * Leaves of these trees that a swing starting on something grown may take:
	 * all of them, or only the grown ones while placed leaves are kept apart.
	 */
	private static Predicate<BlockState> grownLeafOf(
			final List<TreeGroup> groups, final BulkMineConfig.Server config) {
		boolean keepPlaced = config.distinguishPlacedLeaves;
		return candidate -> isLeafOf(groups, candidate) && !(keepPlaced && TreeFeller.isPlaced(candidate));
	}

	private static boolean isLeafOf(final List<TreeGroup> groups, final BlockState state) {
		for (TreeGroup group : groups) {
			if (group.isLeaf(state)) {
				return true;
			}
		}
		return false;
	}

	private static boolean overlaps(final Set<Role> required, final Set<Role> held) {
		for (Role role : required) {
			if (held.contains(role)) {
				return true;
			}
		}
		return false;
	}

	public static void forget(final ServerPlayer player) {
		TOOL_SNAPSHOTS.remove(player.getUUID());
	}
}
