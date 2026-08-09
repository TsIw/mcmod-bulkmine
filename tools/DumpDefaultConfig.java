package io.github.tsiw.bulkmine.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regenerates src/main/resources/default-config.json from {@link ConfigWriter}
 * so the shipped file can never drift from what the mod writes at runtime.
 * A game test asserts the two still match.
 *
 * <p>From the project root:
 * <pre>
 * javac -d build/tmp/gen src/main/java/io/github/tsiw/bulkmine/config/BulkMineConfig.java \
 *       src/main/java/io/github/tsiw/bulkmine/config/BlockDefaults.java \
 *       src/main/java/io/github/tsiw/bulkmine/config/ConfigWriter.java tools/DumpDefaultConfig.java
 * java -cp build/tmp/gen io.github.tsiw.bulkmine.config.DumpDefaultConfig
 * </pre>
 */
public final class DumpDefaultConfig {
	private DumpDefaultConfig() {
	}

	public static void main(final String[] args) throws Exception {
		Path out = Path.of("src/main/resources/default-config.json");
		Files.writeString(out, ConfigWriter.render(new BulkMineConfig()), StandardCharsets.UTF_8);
		System.out.println("wrote " + out.toAbsolutePath());
	}
}
