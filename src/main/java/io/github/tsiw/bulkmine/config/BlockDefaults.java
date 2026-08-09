package io.github.tsiw.bulkmine.config;

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

	static final List<String> FELLABLE = List.of(
			"minecraft:oak_log",
			"minecraft:spruce_log",
			"minecraft:birch_log",
			"minecraft:jungle_log",
			"minecraft:acacia_log",
			"minecraft:dark_oak_log",
			"minecraft:pale_oak_log",
			"minecraft:cherry_log",
			"minecraft:mangrove_log",
			"minecraft:crimson_stem",
			"minecraft:warped_stem",
			"minecraft:brown_mushroom_block",
			"minecraft:red_mushroom_block",
			"minecraft:mushroom_stem");

	static final List<String> LEAVES = List.of(
			"minecraft:oak_leaves",
			"minecraft:spruce_leaves",
			"minecraft:birch_leaves",
			"minecraft:jungle_leaves",
			"minecraft:acacia_leaves",
			"minecraft:dark_oak_leaves",
			"minecraft:pale_oak_leaves",
			"minecraft:cherry_leaves",
			"minecraft:mangrove_leaves",
			"minecraft:azalea_leaves",
			"minecraft:flowering_azalea_leaves");
}
