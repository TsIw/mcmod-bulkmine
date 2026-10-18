package io.github.tsiw.bulkmine.registry;

import io.github.tsiw.bulkmine.BulkMine;
import io.github.tsiw.bulkmine.Role;
import io.github.tsiw.bulkmine.config.BulkMineConfig;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which blocks bulk breaking may touch, and how.
 *
 * <p>Everything comes from the config -- nothing is baked into the mod -- so a
 * player can drop any single block, naturally generated ones included, by
 * deleting its line. The blacklist overrides all of it.
 *
 * <p>Trees are groups: a log, its leaves and its roots. Felling never crosses
 * from one group into another, which is what stops a dark oak eating the huge
 * mushroom beside it. The command-managed custom group is one more group,
 * looked up last, so a block the file assigns to a tree always wins.
 */
public final class BlockWhitelist {
	private BlockWhitelist() {
	}

	private static volatile Matcher pickaxe = Matcher.EMPTY;
	private static volatile Matcher shovel = Matcher.EMPTY;
	private static volatile Matcher blacklist = Matcher.EMPTY;
	/** File-managed trees in file order, then the custom group. */
	private static volatile List<TreeGroup> groups = List.of();

	public static void reload(final BulkMineConfig config) {
		BulkMineConfig.Blocks blocks = config.server.blocks;
		pickaxe = Matcher.of(blocks.pickaxe, "pickaxe");
		shovel = Matcher.of(blocks.shovel, "shovel");
		blacklist = Matcher.of(blocks.blacklist, "blacklist");

		List<TreeGroup> loaded = new ArrayList<>();
		for (BulkMineConfig.Tree tree : blocks.trees) {
			String label = "trees." + (tree.name.isBlank() ? "?" : tree.name);
			loaded.add(TreeGroup.of(tree, label, false));
		}
		loaded.add(TreeGroup.of(blocks.customTree, "customTree", true));
		groups = List.copyOf(loaded);
	}

	/** The tree this block is a log of, or null. The first group listing it wins. */
	public static TreeGroup groupForLog(final BlockState state) {
		if (blacklist.test(state)) {
			return null;
		}
		for (TreeGroup group : groups) {
			if (group.logs.test(state)) {
				return group;
			}
		}
		return null;
	}

	/** The tree this block is a root of, or null. The first group listing it wins. */
	public static TreeGroup groupForRoot(final BlockState state) {
		if (blacklist.test(state)) {
			return null;
		}
		for (TreeGroup group : groups) {
			if (group.roots.test(state)) {
				return group;
			}
		}
		return null;
	}

	/**
	 * Every tree that lists this block as a leaf. Unlike logs a leaf may belong
	 * to several -- shroomlight grows on both nether fungi -- and clearing from
	 * one follows the leaves of all of them.
	 */
	public static List<TreeGroup> groupsForLeaf(final BlockState state) {
		if (blacklist.test(state)) {
			return List.of();
		}
		List<TreeGroup> found = new ArrayList<>();
		for (TreeGroup group : groups) {
			if (group.leaves.test(state)) {
				found.add(group);
			}
		}
		return found;
	}

	/**
	 * The file-managed tree that already claims this block in any role, or
	 * null. Commands refuse such blocks: adding one to the custom group would
	 * let it reach into that tree.
	 */
	public static TreeGroup fileTreeOwning(final BlockState state) {
		for (TreeGroup group : groups) {
			if (!group.custom && group.contains(state)) {
				return group;
			}
		}
		return null;
	}

	/**
	 * The roles that may break this block in cube mode, or an empty set when it
	 * is off limits. Anything belonging to a tree is excluded even for a tool
	 * that could dig it: trees are felled, so digging beside one never eats it.
	 */
	public static Set<Role> cubeRolesFor(final BlockState state) {
		EnumSet<Role> roles = EnumSet.noneOf(Role.class);
		if (blacklist.test(state)) {
			return roles;
		}
		for (TreeGroup group : groups) {
			if (group.contains(state)) {
				return roles;
			}
		}

		if (pickaxe.test(state)) {
			roles.add(Role.PICKAXE);
		}
		if (shovel.test(state)) {
			roles.add(Role.SHOVEL);
		}
		return roles;
	}

	public static boolean isBlacklisted(final BlockState state) {
		return blacklist.test(state);
	}

	/**
	 * One tree: the blocks felling may follow once it has started on one of
	 * them. Every test also honours the blacklist, so a blacklisted block in
	 * the middle of a tree stops the search there.
	 */
	public static final class TreeGroup {
		private final String label;
		private final boolean custom;
		private final Matcher logs;
		private final Matcher leaves;
		private final Matcher roots;

		private TreeGroup(final String label, final boolean custom,
				final Matcher logs, final Matcher leaves, final Matcher roots) {
			this.label = label;
			this.custom = custom;
			this.logs = logs;
			this.leaves = leaves;
			this.roots = roots;
		}

		static TreeGroup of(final BulkMineConfig.Tree tree, final String label, final boolean custom) {
			return new TreeGroup(label, custom,
					Matcher.of(tree.logs, label + ".logs"),
					Matcher.of(tree.leaves, label + ".leaves"),
					Matcher.of(tree.roots, label + ".roots"));
		}

		public boolean isLog(final BlockState state) {
			return logs.test(state) && !blacklist.test(state);
		}

		public boolean isLeaf(final BlockState state) {
			return leaves.test(state) && !blacklist.test(state);
		}

		public boolean isRoot(final BlockState state) {
			return roots.test(state) && !blacklist.test(state);
		}

		/** For messages: {@code trees.oak}, or {@code customTree}. */
		public String label() {
			return label;
		}

		private boolean contains(final BlockState state) {
			return logs.test(state) || leaves.test(state) || roots.test(state);
		}
	}

	/**
	 * A resolved list of config entries. Concrete block ids are looked up once;
	 * tag references stay as tags and are tested against the live state, since
	 * datapacks can change what a tag contains after this runs.
	 */
	private record Matcher(Set<Block> blocks, List<TagKey<Block>> tags) {
		static final Matcher EMPTY = new Matcher(Set.of(), List.of());

		static Matcher of(final List<String> entries, final String listName) {
			Set<Block> blocks = new HashSet<>();
			List<TagKey<Block>> tags = new ArrayList<>();

			for (String entry : entries) {
				if (entry == null || entry.isBlank()) {
					continue;
				}

				String trimmed = entry.trim();
				if (trimmed.startsWith("#")) {
					Identifier id = Identifier.tryParse(trimmed.substring(1));
					if (id == null) {
						BulkMine.LOGGER.warn("blocks.{}: malformed tag \"{}\"", listName, trimmed);
					} else {
						tags.add(TagKey.create(Registries.BLOCK, id));
					}
					continue;
				}

				Identifier id = Identifier.tryParse(trimmed);
				if (id == null) {
					BulkMine.LOGGER.warn("blocks.{}: malformed block id \"{}\"", listName, trimmed);
					continue;
				}

				Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
				if (block == null) {
					BulkMine.LOGGER.warn("blocks.{}: unknown block \"{}\", skipping", listName, trimmed);
				} else {
					blocks.add(block);
				}
			}

			return new Matcher(blocks, tags);
		}

		boolean test(final BlockState state) {
			if (blocks.contains(state.getBlock())) {
				return true;
			}
			for (TagKey<Block> tag : tags) {
				if (state.is(tag)) {
					return true;
				}
			}
			return false;
		}
	}
}
