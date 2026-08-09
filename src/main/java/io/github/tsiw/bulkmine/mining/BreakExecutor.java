package io.github.tsiw.bulkmine.mining;

import io.github.tsiw.bulkmine.config.BulkMineConfig;
import io.github.tsiw.bulkmine.config.ConfigManager;
import java.util.List;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Breaks the extra blocks, following vanilla's own destroy path closely enough
 * that Fortune, Silk Touch and experience all behave normally.
 */
public final class BreakExecutor {
	private BreakExecutor() {
	}

	/**
	 * @param origin the block the player actually broke; drops and the single
	 *               break sound are placed here
	 * @param tool   the stack as it was <em>before</em> vanilla took its
	 *               durability, so drops still resolve correctly in the edge
	 *               case where that break destroyed the tool
	 */
	public static void breakAll(
			final ServerLevel level,
			final ServerPlayer player,
			final BlockPos origin,
			final List<BlockPos> targets,
			final ItemStack tool) {
		BulkMineConfig.Server config = ConfigManager.get().server;
		// The same predicate vanilla's own destroy path uses to decide whether
		// a break yields anything -- creative players get neither drops nor wear.
		boolean noDrops = player.preventsBlockDrops();
		int floor = durabilityFloor(player, tool);
		BlockState loudest = null;

		for (BlockPos pos : targets) {
			BlockState state = level.getBlockState(pos);
			if (!isBreakable(state, level, pos)) {
				continue;
			}

			BlockEntity blockEntity = level.getBlockEntity(pos);

			// Give protection mods and claim plugins the same veto they get on
			// an ordinary break. We never fire AFTER for these: the re-entry
			// guard in BreakHandler would ignore it anyway.
			if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, blockEntity)) {
				continue;
			}

			Block block = state.getBlock();
			BlockState adjusted = block.playerWillDestroy(level, pos, state, player);
			boolean removed = level.removeBlock(pos, false);
			if (!removed) {
				continue;
			}

			block.destroy(level, pos, adjusted);
			spawnParticles(level, pos, adjusted);
			loudest = adjusted;

			if (!noDrops) {
				dropFor(level, player, pos, origin, adjusted, blockEntity, tool, config.aggregateDrops);
				consumeDurability(level, player, floor);
			}
		}

		if (loudest != null) {
			level.playSound(null, origin, loudest.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
		}
	}

	/** Skips air, fluids, unbreakable blocks and anything holding data. */
	private static boolean isBreakable(final BlockState state, final ServerLevel level, final BlockPos pos) {
		if (state.isAir() || state.liquid() || state.hasBlockEntity()) {
			return false;
		}
		return state.getDestroySpeed(level, pos) >= 0.0F;
	}

	/**
	 * Vanilla's own rule for whether breaking a block yields anything: one that
	 * needs a proper tool drops nothing for the wrong one. Measured against the
	 * tool captured before the break rather than whatever is in the hand now.
	 */
	public static boolean canHarvest(final BlockState state, final ItemStack tool) {
		return !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
	}

	private static void dropFor(
			final ServerLevel level,
			final ServerPlayer player,
			final BlockPos pos,
			final BlockPos origin,
			final BlockState state,
			final BlockEntity blockEntity,
			final ItemStack tool,
			final boolean aggregate) {
		// Normally already filtered out before we get here, but stays as a
		// backstop for when requireCorrectTool is switched off.
		if (!canHarvest(state, tool)) {
			return;
		}

		BlockPos dropPos = aggregate ? origin : pos;
		for (ItemStack drop : Block.getDrops(state, level, pos, blockEntity, player, tool)) {
			Block.popResource(level, dropPos, drop);
		}

		// Ore experience rides on spawnAfterBreak, so this has to be called
		// explicitly -- getDrops alone would silently swallow it.
		state.spawnAfterBreak(level, dropPos, tool, true);
	}

	/**
	 * How much durability the sweep must leave behind so the tool survives.
	 *
	 * <p>Normally one point. But this runs from the AFTER event, which fires
	 * before vanilla spends its own point on the origin block, so in that case
	 * two must be left or vanilla finishes the tool off afterwards. Whether
	 * vanilla has already taken its point is visible by comparing the live
	 * stack against the snapshot taken in the BEFORE event.
	 */
	private static int durabilityFloor(final ServerPlayer player, final ItemStack snapshot) {
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty() || snapshot.isEmpty() || held.getItem() != snapshot.getItem()) {
			return 1;
		}
		return held.getDamageValue() == snapshot.getDamageValue() ? 2 : 1;
	}

	/**
	 * One point per block until only {@code floor} is left, then free. The
	 * sweep always runs to the end, so a tool that started on its very last
	 * point still breaks -- on vanilla's origin block, not on ours -- while
	 * every block in range is cleared regardless.
	 */
	private static void consumeDurability(final ServerLevel level, final ServerPlayer player, final int floor) {
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty() || !held.isDamageableItem()) {
			return;
		}

		int remaining = held.getMaxDamage() - held.getDamageValue();
		if (remaining <= floor) {
			return;
		}

		held.hurtAndBreak(1, level, player, item -> {
		});
	}

	/**
	 * Particles per block but no sound per block -- eighteen overlapping break
	 * sounds in one tick is unpleasant, so the caller plays one at the origin.
	 */
	private static void spawnParticles(final ServerLevel level, final BlockPos pos, final BlockState state) {
		level.sendParticles(
				new BlockParticleOption(ParticleTypes.BLOCK, state),
				pos.getX() + 0.5,
				pos.getY() + 0.5,
				pos.getZ() + 0.5,
				8,
				0.25,
				0.25,
				0.25,
				0.0);
	}
}
