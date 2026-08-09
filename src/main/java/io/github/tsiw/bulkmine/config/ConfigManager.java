package io.github.tsiw.bulkmine.config;

import io.github.tsiw.bulkmine.BulkMine;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Owns the single live {@link BulkMineConfig} and the file behind it.
 *
 * <p>Both sides load the same {@code config/bulkmine.json}; each then reads
 * only its own section. Writes always go through {@link ConfigWriter} so the
 * comments survive.
 */
public final class ConfigManager {
	private ConfigManager() {
	}

	private static final Gson GSON = new Gson();
	private static volatile BulkMineConfig current = new BulkMineConfig();

	public static BulkMineConfig get() {
		return current;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("bulkmine.json");
	}

	/**
	 * Reads the file, creating it from defaults when absent. A broken file is
	 * reported and left untouched so the player can fix it by hand; defaults
	 * are used for this run.
	 */
	public static void load() {
		Path file = path();

		if (!Files.exists(file)) {
			current = new BulkMineConfig();
			write(current);
			BulkMine.LOGGER.info("Wrote default config to {}", file);
			return;
		}

		BulkMineConfig parsed;
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			// Lenient so the // comments in the shipped template are tolerated.
			JsonReader json = new JsonReader(reader);
			json.setLenient(true);
			parsed = GSON.fromJson(JsonParser.parseReader(json), BulkMineConfig.class);
		} catch (IOException | RuntimeException e) {
			BulkMine.LOGGER.error("Could not read {}, falling back to defaults for this run", file, e);
			current = new BulkMineConfig();
			return;
		}

		if (parsed == null) {
			BulkMine.LOGGER.error("{} is empty, falling back to defaults for this run", file);
			current = new BulkMineConfig();
			return;
		}

		List<String> problems = parsed.sanitise();
		for (String problem : problems) {
			BulkMine.LOGGER.warn("bulkmine.json: {}", problem);
		}

		current = parsed;

		// Write the file back through the template. Values the player set are
		// kept, settings added by a mod update appear with their defaults, and
		// keys that no longer exist drop out -- so an older file migrates
		// itself instead of quietly missing whole sections.
		write(current);
	}

	/** Persists the live config -- used by the {@code /bulkmine} commands. */
	public static void save() {
		write(current);
	}

	/**
	 * Swaps the live config without touching the file, so callers that only
	 * want different settings in memory -- game tests, mainly -- cannot
	 * clobber what the player has on disk.
	 */
	public static void replace(final BulkMineConfig config) {
		config.sanitise();
		current = config;
	}

	private static void write(final BulkMineConfig config) {
		Path file = path();
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, ConfigWriter.render(config), StandardCharsets.UTF_8);
		} catch (IOException e) {
			BulkMine.LOGGER.error("Could not write {}", file, e);
		}
	}
}
