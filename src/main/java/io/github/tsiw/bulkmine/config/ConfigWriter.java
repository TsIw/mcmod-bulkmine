package io.github.tsiw.bulkmine.config;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders {@code config/bulkmine.json} from a fixed, commented template.
 *
 * <p>Every write goes through here rather than through Gson's serialiser, so
 * editing the config with {@code /bulkmine tool add} keeps the comments that
 * separate the server section from the client section. The shipped
 * {@code default-config.json} resource must stay identical to what this
 * produces for a default {@link BulkMineConfig} -- a game test asserts it.
 *
 * <p>Deliberately ASCII-only. The file is read outside the game, where the
 * console or editor may not be on UTF-8, and mojibake in a config people are
 * meant to hand-edit is worse than plain English.
 */
public final class ConfigWriter {
	private ConfigWriter() {
	}

	/** Roughly how wide a wrapped id list is allowed to get. */
	private static final int WRAP_COLUMN = 96;

	private static final String TEMPLATE = """
{
  // ============================================================
  //  SERVER  -- read by the server. In singleplayer that is your
  //             own world; on a remote server, that server's copy
  //             wins and this section is ignored.
  // ============================================================
  "server": {
    // Master switch for the whole feature.
    "enabled": %1$s,

    // Starting size for a player who has not picked one yet. Raising this
    // moves everyone who is still on it.
    "defaultCubeSize": %2$s,

    // Ceiling on what a player may set. Odd numbers 1-15.
    "maxCubeSize": %3$s,

    // The sizes the toggle key steps through, in order. Pressing it walks the
    // list and then turns bulk mining off, so [3, 5] gives on-3, on-5, off.
    // A size set by hand with "/bulkmine config size" is not a step of the
    // cycle, so the next press turns off from there.
    "toggleSizes": %4$s,

    // Clear leaves as well when felling a tree.
    "breakLeaves": %5$s,

    // How far leaves are followed from the felled logs, in blocks.
    "leafDistance": %6$s,

    // Safety caps for tree felling.
    "maxLogs": %7$s,
    "maxLeaves": %8$s,
    "treeRadius": %9$s,

    // Spawn every drop at the block you broke instead of where each block was.
    "aggregateDrops": %10$s,

    // Keep working while the player is sneaking.
    "allowWhileSneaking": %11$s,

    // Skip blocks the held tool cannot get drops from, the way a stone
    // pickaxe gets nothing from diamond ore. The block under the cursor is
    // still broken by the game itself; only the sweep stops.
    // Set false to break them anyway, dropping nothing.
    "requireCorrectTool": %12$s,

    // Percent chance that one of the EXTRA blocks costs durability, rolled per
    // block the way Unbreaking is. 100 charges for every block, 50 for about
    // half of them, 0 for none. Decimals are allowed.
    // The block under the cursor is charged for by the game itself and is not
    // affected. Unbreaking still applies on top of this.
    "durabilityChance": %13$s,

    // Played to the miner alone when a swing stops because the tool is down to
    // its last point of durability, alongside the action bar message.
    // Any sound id works; "" for silence.
    "durabilityWarningSound": %14$s,

    // Extra tools, on top of the vanilla #minecraft:pickaxes / axes / shovels
    // which are always recognised. Add other mods' tools in game with
    //   /bulkmine tool add hand all
    // Roles: pickaxe, axe, shovel.
    "tools": %15$s,

    // Every block bulk breaking may touch, spelled out. An entry is either a
    // block id ("minecraft:stone") or a tag reference ("#c:ores"). Delete a
    // line to stop that block being included -- naturally generated blocks
    // are listed individually so any of them can be dropped.
    //   pickaxe  -- mined as a cube by a pickaxe-role tool
    //   shovel   -- mined as a cube by a shovel-role tool
    //   fellable -- felled as a tree by an axe-role tool
    //   leaves   -- cleared around a felled tree
    // In game: /bulkmine block add|remove <block> <roles>
    "blocks": {
      "pickaxe": %16$s,

      "shovel": %17$s,

      "fellable": %18$s,

      "leaves": %19$s,

      // Skipped even when a list above includes it. Highest priority.
      // In game: /bulkmine block deny|allow <block>
      "blacklist": %20$s
    }
  },

  // ============================================================
  //  CLIENT  -- read only by each player's own client. Never sent
  //             to the server, and unused on a dedicated server.
  // ============================================================
  "client": {
    // TOP_LEFT / TOP_RIGHT / BOTTOM_LEFT / BOTTOM_RIGHT
    "hudCorner": "%21$s"
  }
}
""";

	public static String render(final BulkMineConfig config) {
		BulkMineConfig.Server s = config.server;
		return TEMPLATE.formatted(
				s.enabled,
				s.defaultCubeSize,
				s.maxCubeSize,
				inlineIntArray(s.toggleSizes),
				s.breakLeaves,
				s.leafDistance,
				s.maxLogs,
				s.maxLeaves,
				s.treeRadius,
				s.aggregateDrops,
				s.allowWhileSneaking,
				s.requireCorrectTool,
				s.durabilityChance,
				quote(s.durabilityWarningSound),
				renderTools(s.tools),
				renderIds(s.blocks.pickaxe),
				renderIds(s.blocks.shovel),
				renderIds(s.blocks.fellable),
				renderIds(s.blocks.leaves),
				renderIds(s.blocks.blacklist),
				config.client.hudCorner);
	}

	/** {@code []}, or one {@code { "item": ..., "roles": [...] }} per line. */
	private static String renderTools(final List<BulkMineConfig.ToolEntry> tools) {
		if (tools.isEmpty()) {
			return "[]";
		}

		String body = tools.stream()
				.map(entry -> "      { \"item\": " + quote(entry.item)
						+ ", \"roles\": " + inlineStringArray(entry.roles) + " }")
				.collect(Collectors.joining(",\n"));
		return "[\n" + body + "\n    ]";
	}

	/**
	 * Block ids wrapped to a readable width. These lists run to dozens of
	 * entries, and one id per line would bury the settings above them.
	 */
	private static String renderIds(final List<String> ids) {
		if (ids.isEmpty()) {
			return "[]";
		}

		StringBuilder out = new StringBuilder("[\n        ");
		int column = 8;
		for (int i = 0; i < ids.size(); i++) {
			String token = quote(ids.get(i)) + (i == ids.size() - 1 ? "" : ",");
			if (column > 8 && column + token.length() > WRAP_COLUMN) {
				out.append("\n        ");
				column = 8;
			} else if (column > 8) {
				out.append(' ');
				column++;
			}
			out.append(token);
			column += token.length();
		}
		return out.append("\n      ]").toString();
	}

	/** Short enough to stay on one line, unlike the block id lists. */
	private static String inlineIntArray(final List<Integer> values) {
		return values.stream().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
	}

	private static String inlineStringArray(final List<String> values) {
		return values.stream().map(ConfigWriter::quote).collect(Collectors.joining(", ", "[", "]"));
	}

	/** Ids and enum names only, so escaping quotes and backslashes is enough. */
	private static String quote(final String value) {
		return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
	}
}
