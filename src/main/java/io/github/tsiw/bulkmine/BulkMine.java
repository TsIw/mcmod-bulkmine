package io.github.tsiw.bulkmine;

import io.github.tsiw.bulkmine.command.BulkMineCommand;
import io.github.tsiw.bulkmine.config.ConfigManager;
import io.github.tsiw.bulkmine.mining.BreakHandler;
import io.github.tsiw.bulkmine.net.BulkMinePayloads;
import io.github.tsiw.bulkmine.registry.BlockWhitelist;
import io.github.tsiw.bulkmine.registry.ToolRegistry;
import io.github.tsiw.bulkmine.state.PlayerSettings;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BulkMine implements ModInitializer {
	public static final String MOD_ID = "bulkmine";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ConfigManager.load();
		// Must happen here: the attachment types have to exist before the
		// server reads anyone's player data.
		PlayerSettings.init();

		// Resolving the config's item and block ids has to wait for the
		// registries to be populated. Doing it here would race other mods'
		// initialisers, which silently dropped their tools on every restart.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			ToolRegistry.reload(ConfigManager.get());
			BlockWhitelist.reload(ConfigManager.get());
		});

		PayloadTypeRegistry.serverboundPlay().register(
				BulkMinePayloads.ToggleRequest.TYPE, BulkMinePayloads.ToggleRequest.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(
				BulkMinePayloads.State.TYPE, BulkMinePayloads.State.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(
				BulkMinePayloads.ToggleRequest.TYPE,
				(payload, context) -> PlayerSettings.toggle(context.player()));

		// Tell a joining client where it stands, so the HUD is right immediately.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PlayerSettings.sync(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BreakHandler.forget(handler.getPlayer()));

		BreakHandler.register();
		CommandRegistrationCallback.EVENT.register(
				(dispatcher, build, environment) -> BulkMineCommand.register(dispatcher, build));

		LOGGER.info("BulkMine ready");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
