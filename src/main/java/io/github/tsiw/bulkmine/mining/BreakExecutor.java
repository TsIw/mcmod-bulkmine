package io.github.tsiw.bulkmine.mining;

import io.github.tsiw.bulkmine.BulkMine;
import io.github.tsiw.bulkmine.Colours;
import io.github.tsiw.bulkmine.config.BulkMineConfig;
import io.github.tsiw.bulkmine.config.ConfigManager;
import java.util.List;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
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

	/** The warning sound id last looked up, and what it resolved to. */
	private static String soundId;
	private static Holder<SoundEvent> soundHolder;

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
		// Read before the sweep moves the damage value: once it has, the floor
		// is no longer comparable against what is left.
		boolean toolSurvives = toolSurvivesTheSwing(player, tool);
		boolean limitReached = false;
		BlockState loudest = null;

		for (BlockPos pos : targets) {
			BlockState state = level.getBlockState(pos);
			if (!isBreakable(state)) {
				continue;
			}
			// Kept from before the break: once the block is gone the level can
			// no longer answer for it, and zero hardness has to be told apart
			// from unbreakable.
			float hardness = state.getDestroySpeed(level, pos);
			if (hardness < 0.0F) {
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

			if (noDrops) {
				continue;
			}
			dropFor(level, player, pos, origin, adjusted, blockEntity, tool, config.aggregateDrops);

			// Zero hardness costs nothing in vanilla either.
			if (hardness == 0.0F || !rollsForDurability(level, config.durabilityChance)) {
				continue;
			}
			limitReached |= !consumeDurability(level, player, floor);
		}

		if (loudest != null) {
			level.playSound(null, origin, loudest.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		// A tool the game is about to finish off is told nothing: the player is
		// watching it break, which says more than the warning would.
		if (limitReached && toolSurvives) {
			warnDurabilityLimit(level, player, config.durabilityWarningSound);
		}
	}

	/**
	 * Whether the tool is still in the player's hand once vanilla has spent the
	 * point it is holding back for the block under the cursor.
	 *
	 * <p>This is what separates "the sweep stopped so your tool would live" from
	 * "your tool was never going to survive this swing". Both make
	 * {@link #consumeDurability} refuse, so without it a tool on its very last
	 * point is warned about a limit and then breaks anyway.
	 *
	 * <p>Public because the game tests check the rule here rather than through
	 * {@link #breakAll}: what it gates is a message and a sound, and neither
	 * leaves anything in the world for a test to assert against.
	 *
	 * @param snapshot the stack as the BEFORE event saw it, which is how the
	 *                 pending vanilla charge is detected
	 */
	public static boolean toolSurvivesTheSwing(final ServerPlayer player, final ItemStack snapshot) {
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty() || !held.isDamageableItem()) {
			return false;
		}
		return held.getMaxDamage() - held.getDamageValue() >= durabilityFloor(player, snapshot);
	}

	/**
	 * Tells the player their tool stopped taking wear, in the action bar and --
	 * unless the config empties the sound id -- with a noise, since a swing that
	 * cleared a cube is a busy moment to read text in.
	 *
	 * <p>Sent straight down this player's connection rather than played into the
	 * level, so nobody else hears one player's tool complaining.
	 */
	private static void warnDurabilityLimit(
			final ServerLevel level, final ServerPlayer player, final String soundId) {
		player.sendSystemMessage(
				Component.translatable("bulkmine.durability.limit").withColor(Colours.ALERT), true);

		Holder<SoundEvent> sound = warningSound(soundId);
		if (sound == null) {
			return;
		}
		player.connection.send(new ClientboundSoundPacket(
				sound,
				SoundSource.PLAYERS,
				player.getX(),
				player.getY(),
				player.getZ(),
				0.7F,
				1.2F,
				level.getRandom().nextLong()));
	}

	/**
	 * Resolves the configured sound, remembering the answer so a bad id is only
	 * complained about once rather than on every swing. Reached from the server
	 * thread alone, which is the only thread that breaks blocks.
	 *
	 * @return null when the id is blank, malformed or unknown, meaning no sound
	 */
	private static Holder<SoundEvent> warningSound(final String id) {
		String wanted = id == null ? "" : id.trim();
		if (wanted.isEmpty()) {
			return null;
		}
		if (wanted.equals(soundId)) {
			return soundHolder;
		}

		soundId = wanted;
		Identifier parsed = Identifier.tryParse(wanted);
		soundHolder = parsed == null ? null : BuiltInRegistries.SOUND_EVENT.get(parsed).orElse(null);
		if (soundHolder == null) {
			BulkMine.LOGGER.warn("durabilityWarningSound: unknown sound \"{}\", playing none", wanted);
		}
		return soundHolder;
	}

	/** Skips air, fluids and anything holding data. */
	private static boolean isBreakable(final BlockState state) {
		return !state.isAir() && !state.liquid() && !state.hasBlockEntity();
	}

	/**
	 * One roll per block, the way Unbreaking works, so nothing has to remember
	 * a running total between swings.
	 */
	private static boolean rollsForDurability(final ServerLevel level, final double chance) {
		if (chance >= 100.0) {
			return true;
		}
		if (chance <= 0.0) {
			return false;
		}
		return level.getRandom().nextDouble() * 100.0 < chance;
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
	 * Charges one block against the tool, down to {@code floor} and no further.
	 * The sweep always runs to the end, so a tool that started on its very last
	 * point still breaks -- on vanilla's origin block, not on ours -- while
	 * every block in range is cleared regardless.
	 *
	 * @return false when the tool was left alone because it is down to its
	 *         floor, which is what the player is warned about
	 */
	private static boolean consumeDurability(final ServerLevel level, final ServerPlayer player, final int floor) {
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty() || !held.isDamageableItem()) {
			return true;
		}

		int amount = damagePerBlock(held);
		if (amount <= 0) {
			return true;
		}

		int remaining = held.getMaxDamage() - held.getDamageValue();
		if (remaining - amount < floor) {
			return false;
		}

		// Goes through hurtAndBreak rather than setDamageValue so Unbreaking
		// still gets its say, on top of the roll above.
		held.hurtAndBreak(amount, level, player, item -> {
		});
		return true;
	}

	/**
	 * What one block costs this tool. Vanilla tools all say one, but the value
	 * belongs to the item, and an item with no tool component costs nothing at
	 * all -- which is exactly what vanilla does with it.
	 */
	private static int damagePerBlock(final ItemStack stack) {
		Tool tool = stack.get(DataComponents.TOOL);
		return tool == null ? 0 : tool.damagePerBlock();
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
