package io.github.tsiw.bulkmine;

/**
 * The three colours the mod speaks in.
 *
 * <p>Defined once, in the shared source set, because the HUD line lives on the
 * client while the durability warning is sent by the server, and the two are
 * meant to read as one thing. Left as plain RGB rather than
 * {@code ChatFormatting}, which no longer exposes its value in 26.2; these are
 * vanilla's own chat green, gray and red.
 */
public final class Colours {
	private Colours() {
	}

	public static final int ON = 0x55FF55;
	public static final int OFF = 0xAAAAAA;
	public static final int ALERT = 0xFF5555;

	/** The same colour as an ARGB value, which is what the HUD draws with. */
	public static int opaque(final int rgb) {
		return 0xFF000000 | rgb;
	}
}
