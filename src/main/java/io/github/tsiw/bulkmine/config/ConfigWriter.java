package io.github.tsiw.bulkmine.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    "enabled": ${enabled},

    // Starting size for a player who has not picked one yet. Raising this
    // moves everyone who is still on it.
    "defaultCubeSize": ${defaultCubeSize},

    // Ceiling on what a player may set. Odd numbers 1-15.
    "maxCubeSize": ${maxCubeSize},

    // The sizes the toggle key steps through, in order. Pressing it walks the
    // list and then turns bulk mining off, so [3, 5] gives on-3, on-5, off.
    // A size set by hand with "/bulkmine config size" is not a step of the
    // cycle, so the next press turns off from there.
    "toggleSizes": ${toggleSizes},

    // Clear the tree's own leaves as well when felling it.
    "breakLeaves": ${breakLeaves},

    // How far leaves are followed, in blocks: from the felled logs, or from
    // the leaf that was hit when clearing leaves on their own.
    "leafDistance": ${leafDistance},

    // Keep leaves a player placed apart from naturally grown ones. When true,
    // a swing that starts on anything natural never reaches placed leaves --
    // logs and roots always count as natural, since the game cannot tell --
    // while a swing that starts on a placed leaf takes both kinds.
    // Set false to treat every leaf alike.
    "distinguishPlacedLeaves": ${distinguishPlacedLeaves},

    // How far roots are followed from a root that was hit, in blocks. If a
    // log of the same tree is reached, the whole tree is felled instead,
    // down to the height of that root.
    "rootDistance": ${rootDistance},

    // Safety caps for tree felling. treeRadius is horizontal only.
    "maxLogs": ${maxLogs},
    "maxLeaves": ${maxLeaves},
    "maxRoots": ${maxRoots},
    "treeRadius": ${treeRadius},

    // Spawn every drop at the block you broke instead of where each block was.
    "aggregateDrops": ${aggregateDrops},

    // Keep working while the player is sneaking.
    "allowWhileSneaking": ${allowWhileSneaking},

    // Skip blocks the held tool cannot get drops from, the way a stone
    // pickaxe gets nothing from diamond ore. The block under the cursor is
    // still broken by the game itself; only the sweep stops.
    // Set false to break them anyway, dropping nothing.
    "requireCorrectTool": ${requireCorrectTool},

    // Percent chance that one of the EXTRA blocks costs durability, rolled per
    // block the way Unbreaking is. 100 charges for every block, 50 for about
    // half of them, 0 for none. Decimals are allowed.
    // The block under the cursor is charged for by the game itself and is not
    // affected. Unbreaking still applies on top of this.
    "durabilityChance": ${durabilityChance},

    // Played to the miner alone when a swing stops because the tool is down to
    // its last point of durability, alongside the action bar message.
    // Any sound id works; "" for silence.
    "durabilityWarningSound": ${durabilityWarningSound},

    // Extra tools, on top of the vanilla #minecraft:pickaxes / axes / shovels
    // which are always recognised. Add other mods' tools in game with
    //   /bulkmine tool add hand all
    // Roles: pickaxe, axe, shovel.
    "tools": ${tools},

    // Every block bulk breaking may touch, spelled out. An entry is either a
    // block id ("minecraft:stone") or a tag reference ("#c:ores"). Delete a
    // line to stop that block being included -- naturally generated blocks
    // are listed individually so any of them can be dropped.
    "blocks": {
      // Mined as a cube by a pickaxe-role / shovel-role tool.
      // In game: /bulkmine block add|remove <block> pickaxe|shovel
      "pickaxe": ${pickaxe},

      "shovel": ${shovel},

      // One entry per kind of tree. Cutting a log with an axe-role tool fells
      // that tree and clears its own leaves, and nothing belonging to another
      // tree. Hitting a leaf with the tool the game considers right for it
      // (or shears) clears the connected leaves at or above it. Hitting a
      // root clears the roots at or above it, or the whole tree if they lead
      // to its trunk. Edit here only -- commands never change these.
      "trees": ${trees},

      // Your own additions, all treated as ONE tree: its logs and leaves are
      // felled together whatever kind they are, but never reach into the trees
      // above. Blocks that belong to a tree above cannot be added here.
      // Roots can be listed by hand.
      // In game: /bulkmine block add|remove <block> log|leaves
      "customTree": ${customTree},

      // Skipped even when a list above includes it. Highest priority.
      // In game: /bulkmine block deny|allow <block>
      "blacklist": ${blacklist}
    }
  },

  // ============================================================
  //  CLIENT  -- read only by each player's own client. Never sent
  //             to the server, and unused on a dedicated server.
  // ============================================================
  "client": {
    // TOP_LEFT / TOP_RIGHT / BOTTOM_LEFT / BOTTOM_RIGHT
    "hudCorner": "${hudCorner}"
  }
}
""";

	public static String render(final BulkMineConfig config) {
		BulkMineConfig.Server s = config.server;
		// Named rather than %n$s: the template has grown past twenty values,
		// and positional numbers had to be shifted by hand on every insert.
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("enabled", s.enabled);
		values.put("defaultCubeSize", s.defaultCubeSize);
		values.put("maxCubeSize", s.maxCubeSize);
		values.put("toggleSizes", inlineIntArray(s.toggleSizes));
		values.put("breakLeaves", s.breakLeaves);
		values.put("leafDistance", s.leafDistance);
		values.put("distinguishPlacedLeaves", s.distinguishPlacedLeaves);
		values.put("rootDistance", s.rootDistance);
		values.put("maxLogs", s.maxLogs);
		values.put("maxLeaves", s.maxLeaves);
		values.put("maxRoots", s.maxRoots);
		values.put("treeRadius", s.treeRadius);
		values.put("aggregateDrops", s.aggregateDrops);
		values.put("allowWhileSneaking", s.allowWhileSneaking);
		values.put("requireCorrectTool", s.requireCorrectTool);
		values.put("durabilityChance", s.durabilityChance);
		values.put("durabilityWarningSound", quote(s.durabilityWarningSound));
		values.put("tools", renderTools(s.tools));
		values.put("pickaxe", renderIds(s.blocks.pickaxe, 8));
		values.put("shovel", renderIds(s.blocks.shovel, 8));
		values.put("trees", renderTrees(s.blocks.trees));
		values.put("customTree", renderTree(s.blocks.customTree, false, 6));
		values.put("blacklist", renderIds(s.blocks.blacklist, 8));
		values.put("hudCorner", config.client.hudCorner);
		return fill(TEMPLATE, values);
	}

	/** Swaps each {@code ${name}} for its value, and refuses to leave one unfilled. */
	private static String fill(final String template, final Map<String, Object> values) {
		String out = template;
		for (Map.Entry<String, Object> entry : values.entrySet()) {
			String token = "${" + entry.getKey() + "}";
			if (!out.contains(token)) {
				throw new IllegalStateException("config template has no " + token);
			}
			out = out.replace(token, String.valueOf(entry.getValue()));
		}
		if (out.contains("${")) {
			throw new IllegalStateException("config template has an unfilled value");
		}
		return out;
	}

	/** One object per tree with each list inline, so a tree reads as one unit. */
	private static String renderTrees(final List<BulkMineConfig.Tree> trees) {
		if (trees.isEmpty()) {
			return "[]";
		}
		String body = trees.stream()
				.map(tree -> "        " + renderTree(tree, true, 8))
				.collect(Collectors.joining(",\n"));
		return "[\n" + body + "\n      ]";
	}

	/**
	 * A tree as an object, its fields one per line two spaces in from the
	 * opening brace, which sits {@code indent} spaces in.
	 */
	private static String renderTree(final BulkMineConfig.Tree tree, final boolean named, final int indent) {
		String pad = " ".repeat(indent + 2);
		StringBuilder out = new StringBuilder("{\n");
		if (named) {
			out.append(pad).append("\"name\": ").append(quote(tree.name)).append(",\n");
		}
		out.append(pad).append("\"logs\": ").append(inlineStringArray(tree.logs)).append(",\n");
		out.append(pad).append("\"leaves\": ").append(inlineStringArray(tree.leaves)).append(",\n");
		out.append(pad).append("\"roots\": ").append(inlineStringArray(tree.roots)).append("\n");
		return out.append(" ".repeat(indent)).append("}").toString();
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
	 *
	 * @param indent spaces before each line of ids; the closing bracket sits
	 *               two to the left of them
	 */
	private static String renderIds(final List<String> ids, final int indent) {
		if (ids.isEmpty()) {
			return "[]";
		}

		String pad = " ".repeat(indent);
		StringBuilder out = new StringBuilder("[\n").append(pad);
		int column = indent;
		for (int i = 0; i < ids.size(); i++) {
			String token = quote(ids.get(i)) + (i == ids.size() - 1 ? "" : ",");
			if (column > indent && column + token.length() > WRAP_COLUMN) {
				out.append("\n").append(pad);
				column = indent;
			} else if (column > indent) {
				out.append(' ');
				column++;
			}
			out.append(token);
			column += token.length();
		}
		return out.append("\n").append(" ".repeat(indent - 2)).append("]").toString();
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
