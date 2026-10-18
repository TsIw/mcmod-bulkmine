package io.github.tsiw.bulkmine.config;

import java.util.ArrayList;
import java.util.List;

/**
 * The block lists a fresh config starts with, written out as concrete ids.
 *
 * <p>These are only seed values. Once {@code config/bulkmine.json} exists the
 * file is the sole authority, so any of these -- including naturally
 * generated blocks -- can be removed by deleting the line.
 *
 * <p>Selection rule: naturally generated blocks only. Crafted or processed
 * blocks (planks, bricks, polished and chiselled variants) are left out, with
 * cobblestone and dirt paths as the two deliberate exceptions.
 */
final class BlockDefaults {
	private BlockDefaults() {
	}

	static final List<String> PICKAXE = List.of(
			// Overworld stone
			"minecraft:stone",
			"minecraft:granite",
			"minecraft:diorite",
			"minecraft:andesite",
			"minecraft:tuff",
			"minecraft:deepslate",
			"minecraft:calcite",
			"minecraft:dripstone_block",
			"minecraft:pointed_dripstone",
			"minecraft:smooth_basalt",
			"minecraft:cobblestone",
			"minecraft:mossy_cobblestone",
			"minecraft:cobbled_deepslate",
			"minecraft:obsidian",
			"minecraft:sandstone",
			"minecraft:red_sandstone",
			"minecraft:amethyst_block",
			"minecraft:budding_amethyst",
			// Badlands
			"minecraft:terracotta",
			"minecraft:white_terracotta",
			"minecraft:orange_terracotta",
			"minecraft:yellow_terracotta",
			"minecraft:red_terracotta",
			"minecraft:brown_terracotta",
			"minecraft:light_gray_terracotta",
			// Frozen
			"minecraft:ice",
			"minecraft:packed_ice",
			"minecraft:blue_ice",
			// Nether
			"minecraft:netherrack",
			"minecraft:basalt",
			"minecraft:blackstone",
			"minecraft:magma_block",
			"minecraft:crying_obsidian",
			// Sulfur caves (26.2)
			"minecraft:sulfur",
			"minecraft:potent_sulfur",
			"minecraft:sulfur_spike",
			"minecraft:cinnabar",
			// End
			"minecraft:end_stone",
			// Ores
			"minecraft:coal_ore",
			"minecraft:deepslate_coal_ore",
			"minecraft:copper_ore",
			"minecraft:deepslate_copper_ore",
			"minecraft:iron_ore",
			"minecraft:deepslate_iron_ore",
			"minecraft:gold_ore",
			"minecraft:deepslate_gold_ore",
			"minecraft:redstone_ore",
			"minecraft:deepslate_redstone_ore",
			"minecraft:lapis_ore",
			"minecraft:deepslate_lapis_ore",
			"minecraft:emerald_ore",
			"minecraft:deepslate_emerald_ore",
			"minecraft:diamond_ore",
			"minecraft:deepslate_diamond_ore",
			"minecraft:nether_gold_ore",
			"minecraft:nether_quartz_ore",
			"minecraft:ancient_debris");

	static final List<String> SHOVEL = List.of(
			"minecraft:dirt",
			"minecraft:coarse_dirt",
			"minecraft:rooted_dirt",
			"minecraft:grass_block",
			"minecraft:podzol",
			"minecraft:mycelium",
			"minecraft:moss_block",
			"minecraft:mud",
			"minecraft:muddy_mangrove_roots",
			"minecraft:dirt_path",
			"minecraft:sand",
			"minecraft:red_sand",
			"minecraft:gravel",
			"minecraft:clay",
			"minecraft:snow",
			"minecraft:snow_block",
			"minecraft:powder_snow",
			"minecraft:soul_sand",
			"minecraft:soul_soil");

	/**
	 * One entry per kind of tree. Felling only ever touches the blocks of the
	 * tree whose log was cut, so a dark oak no longer chews into the huge
	 * mushroom growing against it, and one oak never strips its birch
	 * neighbour.
	 *
	 * <p>The pairings follow how the game actually grows each tree: azalea
	 * trees grow on oak logs, so their leaves belong with oak. The jungle bush
	 * also grows oak leaves on a jungle log, but those are deliberately left
	 * out of jungle -- it would let every jungle tree reach into the fancy oaks
	 * that share its biome, and a bush's leftover leaves decay on their own.
	 *
	 * <p>Muddy mangrove roots are not here: they are treated as soil and dug
	 * with the shovel cube like mud.
	 */
	static List<BulkMineConfig.Tree> trees() {
		return new ArrayList<>(List.of(
				tree("oak", List.of("minecraft:oak_log"),
						List.of("minecraft:oak_leaves", "minecraft:azalea_leaves",
								"minecraft:flowering_azalea_leaves")),
				tree("spruce", List.of("minecraft:spruce_log"), List.of("minecraft:spruce_leaves")),
				tree("birch", List.of("minecraft:birch_log"), List.of("minecraft:birch_leaves")),
				tree("jungle", List.of("minecraft:jungle_log"), List.of("minecraft:jungle_leaves")),
				tree("acacia", List.of("minecraft:acacia_log"), List.of("minecraft:acacia_leaves")),
				tree("dark_oak", List.of("minecraft:dark_oak_log"), List.of("minecraft:dark_oak_leaves")),
				tree("pale_oak", List.of("minecraft:pale_oak_log"), List.of("minecraft:pale_oak_leaves")),
				tree("cherry", List.of("minecraft:cherry_log"), List.of("minecraft:cherry_leaves")),
				tree("mangrove", List.of("minecraft:mangrove_log"), List.of("minecraft:mangrove_leaves"),
						List.of("minecraft:mangrove_roots")),
				tree("crimson_fungus", List.of("minecraft:crimson_stem"),
						List.of("minecraft:nether_wart_block", "minecraft:shroomlight")),
				tree("warped_fungus", List.of("minecraft:warped_stem"),
						List.of("minecraft:warped_wart_block", "minecraft:shroomlight")),
				tree("huge_mushroom", List.of("minecraft:mushroom_stem"),
						List.of("minecraft:red_mushroom_block", "minecraft:brown_mushroom_block"))));
	}

	private static BulkMineConfig.Tree tree(final String name, final List<String> logs, final List<String> leaves) {
		return tree(name, logs, leaves, List.of());
	}

	private static BulkMineConfig.Tree tree(
			final String name, final List<String> logs, final List<String> leaves, final List<String> roots) {
		BulkMineConfig.Tree tree = new BulkMineConfig.Tree();
		tree.name = name;
		tree.logs = new ArrayList<>(logs);
		tree.leaves = new ArrayList<>(leaves);
		tree.roots = new ArrayList<>(roots);
		return tree;
	}
}
