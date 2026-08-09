package io.github.tsiw.bulkmine.state;

import io.github.tsiw.bulkmine.BulkMine;
import io.github.tsiw.bulkmine.config.ConfigManager;
import io.github.tsiw.bulkmine.net.BulkMinePayloads;
import com.mojang.serialization.Codec;
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

	public static boolean toggle(final ServerPlayer player) {
		boolean next = !isEnabled(player);
		setEnabled(player, next);
		return next;
	}

	/** The player's own size, clamped to whatever the server currently allows. */
	public static int cubeSize(final ServerPlayer player) {
		Integer chosen = player.getAttached(CUBE_SIZE);
		int size = chosen == null ? ConfigManager.get().server.defaultCubeSize : chosen;
		return Math.min(size, ConfigManager.get().server.maxCubeSize);
	}

	public static boolean hasOwnCubeSize(final ServerPlayer player) {
		return player.getAttached(CUBE_SIZE) != null;
	}

	public static void setCubeSize(final ServerPlayer player, final int size) {
		player.setAttached(CUBE_SIZE, size);
		sync(player);
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
