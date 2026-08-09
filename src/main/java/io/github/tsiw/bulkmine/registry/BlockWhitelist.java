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
 * Which blocks bulk breaking may touch, and which role is needed.
 *
 * <p>Everything comes from the config -- nothing is baked into the mod -- so a
 * player can drop any single block, naturally generated ones included, by
 * deleting its line. The blacklist overrides all of it.
 */
public final class BlockWhitelist {
	private BlockWhitelist() {
	}

	private static volatile Matcher pickaxe = Matcher.EMPTY;
	private static volatile Matcher shovel = Matcher.EMPTY;
	private static volatile Matcher fellable = Matcher.EMPTY;
	private static volatile Matcher leaves = Matcher.EMPTY;
	private static volatile Matcher blacklist = Matcher.EMPTY;

	public static void reload(final BulkMineConfig config) {
		BulkMineConfig.Blocks blocks = config.server.blocks;
		pickaxe = Matcher.of(blocks.pickaxe, "pickaxe");
		shovel = Matcher.of(blocks.shovel, "shovel");
		fellable = Matcher.of(blocks.fellable, "fellable");
		leaves = Matcher.of(blocks.leaves, "leaves");
		blacklist = Matcher.of(blocks.blacklist, "blacklist");
	}

	/** True when this block is felled as a tree rather than mined as a cube. */
	public static boolean isFellable(final BlockState state) {
		return !blacklist.test(state) && fellable.test(state);
	}

	/** True when this block is cleared as canopy around a felled tree. */
	public static boolean isLeaf(final BlockState state) {
		return !blacklist.test(state) && leaves.test(state);
	}

	/**
	 * The roles that may break this block in cube mode, or an empty set when it
	 * is off limits. Logs are deliberately excluded even for an axe: they
	 * belong to felling, so digging beside a tree never eats the trunk.
	 */
	public static Set<Role> cubeRolesFor(final BlockState state) {
		EnumSet<Role> roles = EnumSet.noneOf(Role.class);
		if (blacklist.test(state) || fellable.test(state)) {
			return roles;
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
