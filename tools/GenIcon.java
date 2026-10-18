// Generates src/main/resources/assets/bulkmine/icon.png (128x128).
// Run with JDK 25 single-file source launch, from the project root:
//   "C:\Program Files\Java\jdk-25.0.4\bin\java.exe" tools/GenIcon.java
//
// The same design at another size, e.g. the CurseForge project logo:
//   "C:\Program Files\Java\jdk-25.0.4\bin\java.exe" tools/GenIcon.java 512 curseforge/logo.png
//
// This is a build-time helper and is not shipped inside the mod jar.

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class GenIcon {
	private static final int DEFAULT_SIZE = 128;
	private static final String DEFAULT_OUT = "src/main/resources/assets/bulkmine/icon.png";
	private static int SIZE;
	private static int MARGIN;
	private static final Color BACKGROUND = new Color(0x2B, 0x3A, 0x42);
	private static final Color TEXT = new Color(0xFF, 0xFF, 0xFF);
	private static final String[] LINES = {"Bulk", "Mine"};

	public static void main(String[] args) throws Exception {
		SIZE = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_SIZE;
		// Scaled so every size is the same picture: 12px at the original 128.
		MARGIN = Math.round(SIZE * 12f / DEFAULT_SIZE);
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		g.setColor(BACKGROUND);
		g.fillRect(0, 0, SIZE, SIZE);

		Font font = fitFont();
		g.setFont(font);
		g.setColor(TEXT);

		FontRenderContext frc = g.getFontRenderContext();
		double lineHeight = font.getLineMetrics(LINES[0], frc).getHeight();
		// Stack the two lines around the vertical centre, then shift down by the
		// ascent so drawString's baseline lands where we want it.
		double blockTop = (SIZE - lineHeight * LINES.length) / 2.0;
		double ascent = font.getLineMetrics(LINES[0], frc).getAscent();

		for (int i = 0; i < LINES.length; i++) {
			Rectangle2D bounds = font.getStringBounds(LINES[i], frc);
			float x = (float) ((SIZE - bounds.getWidth()) / 2.0);
			float y = (float) (blockTop + lineHeight * i + ascent);
			g.drawString(LINES[i], x, y);
		}

		g.dispose();

		File out = new File(args.length > 1 ? args[1] : DEFAULT_OUT);
		out.getParentFile().mkdirs();
		ImageIO.write(image, "PNG", out);
		System.out.println("wrote " + out.getAbsolutePath() + " using font " + font.getFontName());
	}

	/** Largest Segoe UI Bold that keeps both lines inside the margins. */
	private static Font fitFont() {
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = probe.createGraphics();
		int usable = SIZE - MARGIN * 2;
		Font best = new Font("Segoe UI", Font.BOLD, 8);

		for (int size = 8; size <= SIZE / 2; size++) {
			Font candidate = new Font("Segoe UI", Font.BOLD, size);
			FontRenderContext frc = g.getFontRenderContext();
			double widest = 0;
			for (String line : LINES) {
				widest = Math.max(widest, candidate.getStringBounds(line, frc).getWidth());
			}
			double totalHeight = candidate.getLineMetrics(LINES[0], frc).getHeight() * LINES.length;
			if (widest > usable || totalHeight > usable) {
				break;
			}
			best = candidate;
		}

		g.dispose();
		return best;
	}
}
