package io.github.tsiw.bulkmine.mining;

import io.github.tsiw.bulkmine.Role;
import io.github.tsiw.bulkmine.config.BulkMineConfig;
import io.github.tsiw.bulkmine.config.ConfigManager;
import io.github.tsiw.bulkmine.registry.BlockWhitelist;
import io.github.tsiw.bulkmine.registry.ToolRegistry;
import io.github.tsiw.bulkmine.state.PlayerSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
		Set<Role> roles = ToolRegistry.rolesOf(tool);
		if (roles.isEmpty()) {
			return;
		}

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

	/** Felling wins for logs; everything else that is whitelisted digs a cube. */
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
		if (BlockWhitelist.isFellable(state)) {
			if (!roles.contains(Role.AXE)) {
				return List.of();
			}
			targets = fell(level, origin, state, config);
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

	private static List<BlockPos> fell(
			final ServerLevel level, final BlockPos origin, final BlockState state, final BulkMineConfig.Server config) {
		List<BlockPos> logs = TreeFeller.collectLogs(
				level, origin, state.getBlock(), config.maxLogs, config.treeRadius);

		if (!config.breakLeaves) {
			return logs;
		}

		// Seed the leaf search from the broken log too, otherwise felling a
		// one-block trunk would leave its canopy floating.
		List<BlockPos> leafSeeds = new ArrayList<>(logs);
		leafSeeds.add(origin);

		List<BlockPos> targets = new ArrayList<>(logs);
		targets.addAll(TreeFeller.collectLeaves(level, leafSeeds, config.leafDistance, config.maxLeaves));
		return targets;
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
