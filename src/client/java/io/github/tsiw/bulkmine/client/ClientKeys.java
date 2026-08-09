package io.github.tsiw.bulkmine.client;

import io.github.tsiw.bulkmine.BulkMine;
import io.github.tsiw.bulkmine.net.BulkMinePayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** The toggle key. Pressing it asks the server; it never flips state locally. */
public final class ClientKeys {
	private ClientKeys() {
	}

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(BulkMine.id("keys"));

	private static final KeyMapping TOGGLE = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.bulkmine.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY));

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TOGGLE.consumeClick()) {
				if (client.player != null && ClientState.serverSupported()) {
					ClientPlayNetworking.send(new BulkMinePayloads.ToggleRequest());
				}
			}
		});
	}
}
