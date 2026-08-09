package io.github.tsiw.bulkmine.client;

import io.github.tsiw.bulkmine.net.BulkMinePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * The client's mirror of the server-owned state. Nothing here decides
 * anything; it only remembers what the server last said so the HUD has
 * something to draw between packets.
 */
public final class ClientState {
	private ClientState() {
	}

	private static volatile boolean enabled;
	private static volatile int cubeSize = 3;

	public static boolean isEnabled() {
		return enabled;
	}

	public static int cubeSize() {
		return cubeSize;
	}

	public static void set(final boolean value, final int size) {
		enabled = value;
		cubeSize = size;
	}

	public static void reset() {
		enabled = false;
		cubeSize = 3;
	}

	/**
	 * Whether the server can receive our toggle request -- which is the same
	 * question as whether it has this mod installed.
	 */
	public static boolean serverSupported() {
		if (Minecraft.getInstance().getConnection() == null) {
			return false;
		}
		return ClientPlayNetworking.canSend(BulkMinePayloads.ToggleRequest.TYPE);
	}
}
