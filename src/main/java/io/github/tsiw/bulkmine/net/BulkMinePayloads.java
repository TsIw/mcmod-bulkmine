package io.github.tsiw.bulkmine.net;

import io.github.tsiw.bulkmine.BulkMine;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The two packets this mod sends. Defined in the common source set because
 * both sides need the types; the handlers themselves stay on their own side.
 */
public final class BulkMinePayloads {
	private BulkMinePayloads() {
	}

	/**
	 * Client asks the server to flip its state. Deliberately carries no value:
	 * the server owns the state, the client only requests a change.
	 */
	public record ToggleRequest() implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ToggleRequest> TYPE =
				new CustomPacketPayload.Type<>(BulkMine.id("toggle_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ToggleRequest> CODEC =
				StreamCodec.unit(new ToggleRequest());

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server tells the client what the state actually is, on join and on
	 * change. Carries the cube size too, because the HUD shows it.
	 */
	public record State(boolean enabled, int cubeSize) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<State> TYPE =
				new CustomPacketPayload.Type<>(BulkMine.id("state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, State::enabled,
				ByteBufCodecs.VAR_INT, State::cubeSize,
				State::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
