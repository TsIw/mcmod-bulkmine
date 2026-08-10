package io.github.tsiw.bulkmine.client;

import io.github.tsiw.bulkmine.BulkMine;
import io.github.tsiw.bulkmine.Colours;
import io.github.tsiw.bulkmine.net.BulkMinePayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.network.chat.Component;

/**
 * Client entry point: the keybind, the HUD line, and the action bar nudge that
 * confirms a toggle actually reached the server.
 */
public class BulkMineClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(BulkMinePayloads.State.TYPE, (payload, context) -> {
			ClientState.set(payload.enabled(), payload.cubeSize());

			// The permanent HUD line answers "what is it doing right now"; this
			// answers "did my keypress land", which is a different question.
			// Same colours as the HUD line, so the two read as one thing.
			context.player().sendOverlayMessage(payload.enabled()
					? Component.translatable("bulkmine.toggle.on", payload.cubeSize()).withColor(Colours.ON)
					: Component.translatable("bulkmine.toggle.off").withColor(Colours.OFF));
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.reset());

		ClientKeys.register();
		HudElementRegistry.addLast(BulkMine.id("status"), new BulkMineHud());
	}
}
