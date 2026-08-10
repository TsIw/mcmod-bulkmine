package io.github.tsiw.bulkmine.client;

import io.github.tsiw.bulkmine.Colours;
import io.github.tsiw.bulkmine.config.ConfigManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * One small line of text in a corner, always on, saying what the toggle is
 * doing and at what size. Text only -- no textures, no icons.
 */
public final class BulkMineHud implements HudElement {
	/** Small enough to stay out of the way, large enough to still read. */
	private static final float SCALE = 0.6F;
	private static final int MARGIN = 4;

	// Shared with the action bar messages that mirror this line, so the two
	// can never drift apart.
	private static final int COLOUR_ON = Colours.opaque(Colours.ON);
	private static final int COLOUR_OFF = Colours.opaque(Colours.OFF);
	private static final int COLOUR_UNSUPPORTED = Colours.opaque(Colours.ALERT);

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		// F1 and the F3 debug screen both mean "get out of the way".
		if (minecraft.gui.hud.isHidden() || minecraft.getDebugOverlay().showDebugScreen()) {
			return;
		}

		Component text;
		int colour;
		if (!ClientState.serverSupported()) {
			text = Component.translatable("bulkmine.hud.unsupported");
			colour = COLOUR_UNSUPPORTED;
		} else if (ClientState.isEnabled()) {
			text = Component.translatable("bulkmine.hud.on", ClientState.cubeSize());
			colour = COLOUR_ON;
		} else {
			text = Component.translatable("bulkmine.hud.off");
			colour = COLOUR_OFF;
		}

		// Lay the corner out in real screen pixels, then divide by the scale,
		// because the scaled matrix multiplies whatever coordinates we pass.
		float width = minecraft.font.width(text) * SCALE;
		float height = minecraft.font.lineHeight * SCALE;
		String corner = ConfigManager.get().client.hudCorner;

		float x = switch (corner) {
			case "TOP_LEFT", "BOTTOM_LEFT" -> MARGIN;
			default -> graphics.guiWidth() - MARGIN - width;
		};
		float y = switch (corner) {
			case "TOP_LEFT", "TOP_RIGHT" -> MARGIN;
			default -> graphics.guiHeight() - MARGIN - height;
		};

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(SCALE, SCALE);
		graphics.text(minecraft.font, text, Math.round(x / SCALE), Math.round(y / SCALE), colour, true);
		pose.popMatrix();
	}
}
