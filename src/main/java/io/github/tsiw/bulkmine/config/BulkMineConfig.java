package io.github.tsiw.bulkmine.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The whole of {@code config/bulkmine.json}. One file is shared by both sides:
 * a server reads {@link #server} and ignores {@link #client}, a client reads
 * {@link #client} and -- when connected to a remote server -- ignores
 * {@link #server}, because the remote server's own copy is authoritative.
 *
 * <p>Plain mutable fields, because Gson populates them by reflection. Anything
 * missing from the file keeps the default assigned here.
 */
public final class BulkMineConfig {
	public Server server = new Server();
	public Client client = new Client();

	public static final class Server {
		public boolean enabled = true;
		/** Starting cube size for a player who has never set their own. */
		public int defaultCubeSize = 3;
		/** Ceiling on what a player may set with {@code /bulkmine config size}. */
		public int maxCubeSize = 9;
		/** The sizes the toggle key steps through before it turns off. */
		public List<Integer> toggleSizes = new ArrayList<>(List.of(3, 5));
		public boolean breakLeaves = true;
		public int leafDistance = 6;
		public int maxLogs = 256;
		public int maxLeaves = 1024;
		public int treeRadius = 16;
		public boolean aggregateDrops = true;
		public boolean allowWhileSneaking = true;
		/** Skip blocks the held tool is too weak to get drops from. */
		public boolean requireCorrectTool = true;
		/** Percent chance that one bulk-broken block costs the tool durability. */
		public double durabilityChance = 100.0;
		/** Sound id played when a swing stops at the durability floor, or "". */
		public String durabilityWarningSound = "minecraft:block.anvil.place";
		public List<ToolEntry> tools = new ArrayList<>();
		public Blocks blocks = new Blocks();
	}

	public static final class Client {
		public String hudCorner = "BOTTOM_RIGHT";
	}

	public static final class ToolEntry {
		public String item;
		public List<String> roles = new ArrayList<>();

		public ToolEntry() {
		}

		public ToolEntry(String item, List<String> roles) {
			this.item = item;
			this.roles = new ArrayList<>(roles);
		}
	}

	/**
	 * Every block bulk breaking may touch, spelled out. Entries are block ids
	 * ({@code minecraft:stone}) or tag references ({@code #c:ores}); the
	 * shipped defaults use plain ids only, so any single block -- including
	 * naturally generated ones -- can be dropped by deleting its line.
	 */
	public static final class Blocks {
		public List<String> pickaxe = new ArrayList<>(BlockDefaults.PICKAXE);
		public List<String> shovel = new ArrayList<>(BlockDefaults.SHOVEL);
		public List<String> fellable = new ArrayList<>(BlockDefaults.FELLABLE);
		public List<String> leaves = new ArrayList<>(BlockDefaults.LEAVES);
		/** Wins over every list above. */
		public List<String> blacklist = new ArrayList<>();
	}

	/**
	 * Repairs values that would break the mining logic, so a hand-edited file
	 * can never crash the server. Returns the messages worth logging.
	 */
	public List<String> sanitise() {
		List<String> problems = new ArrayList<>();

		if (server == null) {
			server = new Server();
			problems.add("missing \"server\" section, using defaults");
		}
		if (client == null) {
			client = new Client();
			problems.add("missing \"client\" section, using defaults");
		}
		if (server.tools == null) {
			server.tools = new ArrayList<>();
		}
		if (server.blocks == null) {
			server.blocks = new Blocks();
		}
		server.blocks.pickaxe = orEmpty(server.blocks.pickaxe);
		server.blocks.shovel = orEmpty(server.blocks.shovel);
		server.blocks.fellable = orEmpty(server.blocks.fellable);
		server.blocks.leaves = orEmpty(server.blocks.leaves);
		server.blocks.blacklist = orEmpty(server.blocks.blacklist);

		server.maxCubeSize = clampOdd("maxCubeSize", server.maxCubeSize, 9, problems);
		server.defaultCubeSize = clampOdd("defaultCubeSize", server.defaultCubeSize, 3, problems);
		if (server.defaultCubeSize > server.maxCubeSize) {
			problems.add("defaultCubeSize " + server.defaultCubeSize
					+ " is above maxCubeSize " + server.maxCubeSize + ", lowering it");
			server.defaultCubeSize = server.maxCubeSize;
		}

		server.toggleSizes = sanitiseToggleSizes(server, problems);

		if (!(server.durabilityChance >= 0.0) || server.durabilityChance > 100.0) {
			problems.add("durabilityChance must be between 0 and 100, was "
					+ server.durabilityChance + ", using 100");
			server.durabilityChance = 100.0;
		}
		if (server.durabilityWarningSound == null) {
			server.durabilityWarningSound = "";
		}

		server.leafDistance = clamp("leafDistance", server.leafDistance, 0, 32, 6, problems);
		server.maxLogs = clamp("maxLogs", server.maxLogs, 1, 8192, 256, problems);
		server.maxLeaves = clamp("maxLeaves", server.maxLeaves, 0, 16384, 1024, problems);
		server.treeRadius = clamp("treeRadius", server.treeRadius, 1, 64, 16, problems);

		server.tools.removeIf(entry -> {
			boolean bad = entry == null || entry.item == null || entry.item.isBlank();
			if (bad) {
				problems.add("dropped a tool entry with no item id");
			}
			return bad;
		});

		String corner = client.hudCorner == null ? "" : client.hudCorner.toUpperCase(Locale.ROOT);
		if (!corner.equals("TOP_LEFT") && !corner.equals("TOP_RIGHT")
				&& !corner.equals("BOTTOM_LEFT") && !corner.equals("BOTTOM_RIGHT")) {
			problems.add("unknown hudCorner \"" + client.hudCorner + "\", using BOTTOM_RIGHT");
			corner = "BOTTOM_RIGHT";
		}
		client.hudCorner = corner;

		return problems;
	}

	/**
	 * The toggle key's sizes, in order, with anything unusable dropped: even
	 * numbers, values outside 1-15, sizes above {@code maxCubeSize} and repeats.
	 * An empty result would leave the key unable to turn anything on, so it
	 * falls back to the one size everybody starts with.
	 */
	private static List<Integer> sanitiseToggleSizes(final Server server, final List<String> problems) {
		List<Integer> kept = new ArrayList<>();
		if (server.toggleSizes != null) {
			for (Integer size : server.toggleSizes) {
				if (size == null || size < 1 || size > 15 || size % 2 == 0 || size > server.maxCubeSize) {
					problems.add("dropped toggleSizes entry " + size
							+ ", must be an odd number from 1 to " + server.maxCubeSize);
				} else if (!kept.contains(size)) {
					kept.add(size);
				}
			}
		}

		if (kept.isEmpty()) {
			problems.add("toggleSizes has nothing usable in it, using " + server.defaultCubeSize);
			kept.add(server.defaultCubeSize);
		}
		return kept;
	}

	private static List<String> orEmpty(final List<String> list) {
		return list == null ? new ArrayList<>() : list;
	}

	private static int clampOdd(final String name, final int value, final int fallback, final List<String> problems) {
		if (value < 1 || value > 15 || value % 2 == 0) {
			problems.add(name + " must be an odd number from 1 to 15, was " + value + ", using " + fallback);
			return fallback;
		}
		return value;
	}

	private static int clamp(String name, int value, int min, int max, int fallback, List<String> problems) {
		if (value < min || value > max) {
			problems.add(name + " must be between " + min + " and " + max + ", was " + value + ", using " + fallback);
			return fallback;
		}
		return value;
	}
}
