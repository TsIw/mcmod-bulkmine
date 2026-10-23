package io.github.tsiw.bulkmine.state;

import io.github.tsiw.bulkmine.BulkMine;
import io.github.tsiw.bulkmine.config.ConfigManager;
import io.github.tsiw.bulkmine.net.BulkMinePayloads;
import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player state, owned by the server and persisted with the player's data
 * so it survives relogging and death. The client never decides these values;
 * it is told what they are.
 */
public final class PlayerSettings {
	private PlayerSettings() {
	}

	private static final AttachmentType<Boolean> ENABLED = AttachmentRegistry.<Boolean>builder()
			.persistent(Codec.BOOL)
			.copyOnDeath()
			.initializer(() -> Boolean.FALSE)
			.buildAndRegister(BulkMine.id("enabled"));

	/**
	 * Absent until the player sets one, so raising {@code defaultCubeSize} in
	 * the config still moves everyone who has not chosen for themselves.
	 */
	private static final AttachmentType<Integer> CUBE_SIZE = AttachmentRegistry.<Integer>builder()
			.persistent(Codec.INT)
			.copyOnDeath()
			.buildAndRegister(BulkMine.id("cube_size"));

	/**
	 * Whether the cube takes the layers below the cursor too, and whether
	 * felling and clearing go below the block that was hit. Off until the
	 * player turns them on, with no server-side default to follow: a default
	 * that could move would change someone's digging without their asking.
	 *
	 * <p>Not part of the state sent to the client, so the HUD and the network
	 * protocol stay as they were and older clients keep working.
	 */
	private static final AttachmentType<Boolean> CUBE_BELOW = AttachmentRegistry.<Boolean>builder()
			.persistent(Codec.BOOL)
			.copyOnDeath()
			.initializer(() -> Boolean.FALSE)
			.buildAndRegister(BulkMine.id("cube_below"));

	private static final AttachmentType<Boolean> TREE_BELOW = AttachmentRegistry.<Boolean>builder()
			.persistent(Codec.BOOL)
			.copyOnDeath()
			.initializer(() -> Boolean.FALSE)
			.buildAndRegister(BulkMine.id("tree_below"));

	/**
	 * Forces the attachment types above to exist.
	 *
	 * <p>They register from this class's static initialiser, which Java does
	 * not run until something touches the class. Every other reference here is
	 * inside a lambda, so that first touch was the first player joining --
	 * after the server had already read player data, which it then threw away
	 * with "Found unknown attachment type bulkmine:enabled". Calling this from
	 * mod init makes the types exist before any data is read.
	 */
	public static void init() {
	}

	public static boolean isEnabled(final ServerPlayer player) {
		return player.getAttachedOrCreate(ENABLED);
	}

	public static void setEnabled(final ServerPlayer player, final boolean enabled) {
		player.setAttached(ENABLED, enabled);
		sync(player);
	}

	/**
	 * Steps the toggle key on: off, then each of the configured sizes in turn,
	 * then off again. A size the list does not mention -- one set by hand with
	 * {@code /bulkmine config size} -- turns off on the next press, which is
	 * also what happens at the end of the list.
	 *
	 * <p>Writes both values before syncing, so the client is never shown a
	 * half-applied state.
	 */
	public static void cycle(final ServerPlayer player) {
		List<Integer> sizes = ConfigManager.get().server.toggleSizes;
		int next = 0;
		if (isEnabled(player)) {
			int current = sizes.indexOf(cubeSize(player));
			// A size that is not a step of the cycle has no next step, so the
			// press falls off the end the same way the last size does.
			next = current < 0 ? sizes.size() : current + 1;
		}

		if (next >= sizes.size()) {
			player.setAttached(ENABLED, Boolean.FALSE);
		} else {
			player.setAttached(ENABLED, Boolean.TRUE);
			player.setAttached(CUBE_SIZE, sizes.get(next));
		}
		sync(player);
	}

	/**
	 * The player's size, clamped to whatever the server currently allows. Until
	 * they change it -- with the toggle key or the command -- this is just the
	 * server's starting value, and moving that in the config moves them with it.
	 */
	public static int cubeSize(final ServerPlayer player) {
		Integer chosen = player.getAttached(CUBE_SIZE);
		int size = chosen == null ? ConfigManager.get().server.defaultCubeSize : chosen;
		return Math.min(size, ConfigManager.get().server.maxCubeSize);
	}

	public static void setCubeSize(final ServerPlayer player, final int size) {
		player.setAttached(CUBE_SIZE, size);
		sync(player);
	}

	public static boolean cubeBelow(final ServerPlayer player) {
		return player.getAttachedOrCreate(CUBE_BELOW);
	}

	public static void setCubeBelow(final ServerPlayer player, final boolean below) {
		player.setAttached(CUBE_BELOW, below);
	}

	public static boolean treeBelow(final ServerPlayer player) {
		return player.getAttachedOrCreate(TREE_BELOW);
	}

	public static void setTreeBelow(final ServerPlayer player, final boolean below) {
		player.setAttached(TREE_BELOW, below);
	}

	/** Pushes the authoritative values to the client's HUD. */
	public static void sync(final ServerPlayer player) {
		// Mock players in game tests have no connection to send down.
		if (player.connection == null) {
			return;
		}
		if (ServerPlayNetworking.canSend(player, BulkMinePayloads.State.TYPE)) {
			ServerPlayNetworking.send(player, new BulkMinePayloads.State(isEnabled(player), cubeSize(player)));
		}
	}
}
