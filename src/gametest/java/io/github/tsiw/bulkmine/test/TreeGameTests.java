package io.github.tsiw.bulkmine.test;

import static io.github.tsiw.bulkmine.test.BulkMineGameTests.config;
import static io.github.tsiw.bulkmine.test.BulkMineGameTests.mine;
import static io.github.tsiw.bulkmine.test.BulkMineGameTests.preparePlayer;

import io.github.tsiw.bulkmine.config.BulkMineConfig;
import io.github.tsiw.bulkmine.config.ConfigManager;
import io.github.tsiw.bulkmine.registry.BlockWhitelist;
import io.github.tsiw.bulkmine.state.PlayerSettings;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * Tree felling: groups, leaves, roots and placed leaves.
 *
 * <p>The first half builds small trees by hand to pin each rule down. The
 * second grows real trees with the game's own generator, because a hand-built
 * column cannot reproduce a giant spruce's height or a mangrove's roots, and
 * those shapes are exactly what went wrong before.
 */
public class TreeGameTests {
	private static final String EMPTY = "fabric-gametest-api-v1:empty";

	// ------------------------------------------------------------ groups

	@GameTest(structure = EMPTY)
	public void aTallTrunkIsFelledToTheTop(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		config().breakLeaves = false;

		// Taller than the old radius of 16, which used to count height too.
		for (int y = 1; y <= 30; y++) {
			helper.setBlock(new BlockPos(2, y, 2), Blocks.SPRUCE_LOG);
		}

		mine(helper, player, new BlockPos(2, 1, 2));

		for (int y = 2; y <= 30; y++) {
			helper.assertBlockNotPresent(Blocks.SPRUCE_LOG, new BlockPos(2, y, 2));
		}
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void fellingLeavesAnotherTreesLeavesAlone(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);

		helper.setBlock(new BlockPos(2, 1, 2), Blocks.OAK_LOG);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.OAK_LOG);
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.OAK_LEAVES);
		helper.setBlock(new BlockPos(1, 2, 2), Blocks.BIRCH_LEAVES);

		mine(helper, player, new BlockPos(2, 1, 2));

		helper.assertBlockNotPresent(Blocks.OAK_LEAVES, new BlockPos(3, 2, 2));
		helper.assertBlockPresent(Blocks.BIRCH_LEAVES, new BlockPos(1, 2, 2));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void aDarkOakNoLongerEatsTheMushroomBesideIt(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);

		helper.setBlock(new BlockPos(2, 1, 2), Blocks.DARK_OAK_LOG);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.DARK_OAK_LOG);
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.DARK_OAK_LEAVES);
		// Touching the canopy, as huge mushrooms in a dark forest often do.
		helper.setBlock(new BlockPos(4, 2, 2), Blocks.RED_MUSHROOM_BLOCK);

		mine(helper, player, new BlockPos(2, 1, 2));

		helper.assertBlockNotPresent(Blocks.DARK_OAK_LEAVES, new BlockPos(3, 2, 2));
		helper.assertBlockPresent(Blocks.RED_MUSHROOM_BLOCK, new BlockPos(4, 2, 2));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void theCustomTreeMixesItsOwnKindsButNoOthers(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		config().blocks.customTree.logs.add("minecraft:stripped_oak_log");
		config().blocks.customTree.logs.add("minecraft:stripped_birch_log");
		config().blocks.customTree.leaves.add("minecraft:hay_block");
		BlockWhitelist.reload(ConfigManager.get());

		helper.setBlock(new BlockPos(2, 1, 2), Blocks.STRIPPED_OAK_LOG);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.STRIPPED_BIRCH_LOG);
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.HAY_BLOCK);
		helper.setBlock(new BlockPos(1, 2, 2), Blocks.OAK_LEAVES);

		mine(helper, player, new BlockPos(2, 1, 2));

		// Two different logs felled together: the custom group draws no lines.
		helper.assertBlockNotPresent(Blocks.STRIPPED_BIRCH_LOG, new BlockPos(2, 2, 2));
		helper.assertBlockNotPresent(Blocks.HAY_BLOCK, new BlockPos(3, 2, 2));
		// But the oak's leaves are not its to take.
		helper.assertBlockPresent(Blocks.OAK_LEAVES, new BlockPos(1, 2, 2));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void aFileTreeNeverEatsTheCustomTree(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		config().blocks.customTree.leaves.add("minecraft:hay_block");
		BlockWhitelist.reload(ConfigManager.get());

		helper.setBlock(new BlockPos(2, 1, 2), Blocks.OAK_LOG);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.OAK_LOG);
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.HAY_BLOCK);

		mine(helper, player, new BlockPos(2, 1, 2));

		helper.assertBlockPresent(Blocks.HAY_BLOCK, new BlockPos(3, 2, 2));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void commandsCannotClaimABlockAFileTreeOwns(final GameTestHelper helper) {
		BulkMineGameTests.resetConfig();
		// What /bulkmine block add checks before touching the custom tree.
		helper.assertTrue(BlockWhitelist.fileTreeOwning(Blocks.OAK_LEAVES.defaultBlockState()) != null,
				"oak leaves belong to the oak tree");
		helper.assertTrue(BlockWhitelist.fileTreeOwning(Blocks.SHROOMLIGHT.defaultBlockState()) != null,
				"shroomlight belongs to the nether fungi");
		helper.assertTrue(BlockWhitelist.fileTreeOwning(Blocks.HAY_BLOCK.defaultBlockState()) == null,
				"hay belongs to no tree");
		helper.succeed();
	}

	// ------------------------------------------------------- placed leaves

	@GameTest(structure = EMPTY)
	public void fellingKeepsPlacedLeaves(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);

		helper.setBlock(new BlockPos(2, 1, 2), Blocks.OAK_LOG);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.OAK_LOG);
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.OAK_LEAVES);
		helper.setBlock(new BlockPos(1, 2, 2), placed(Blocks.OAK_LEAVES));

		mine(helper, player, new BlockPos(2, 1, 2));

		helper.assertBlockNotPresent(Blocks.OAK_LEAVES, new BlockPos(3, 2, 2));
		helper.assertBlockPresent(Blocks.OAK_LEAVES, new BlockPos(1, 2, 2));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void placedLeavesGoWhenNotKeptApart(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		config().distinguishPlacedLeaves = false;

		helper.setBlock(new BlockPos(2, 1, 2), Blocks.OAK_LOG);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.OAK_LOG);
		helper.setBlock(new BlockPos(1, 2, 2), placed(Blocks.OAK_LEAVES));

		mine(helper, player, new BlockPos(2, 1, 2));

		helper.assertBlockNotPresent(Blocks.OAK_LEAVES, new BlockPos(1, 2, 2));
		helper.succeed();
	}

	// ---------------------------------------------------- clearing leaves

	@GameTest(structure = EMPTY)
	public void aHoeClearsGrownLeavesButNotPlacedOnes(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_HOE);

		helper.setBlock(new BlockPos(2, 2, 2), Blocks.OAK_LEAVES);
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.OAK_LEAVES);
		helper.setBlock(new BlockPos(2, 3, 2), placed(Blocks.OAK_LEAVES));

		mine(helper, player, new BlockPos(2, 2, 2));

		helper.assertBlockNotPresent(Blocks.OAK_LEAVES, new BlockPos(3, 2, 2));
		helper.assertBlockPresent(Blocks.OAK_LEAVES, new BlockPos(2, 3, 2));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void startingOnAPlacedLeafTakesBothKinds(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_HOE);

		helper.setBlock(new BlockPos(2, 2, 2), placed(Blocks.OAK_LEAVES));
		helper.setBlock(new BlockPos(3, 2, 2), placed(Blocks.OAK_LEAVES));
		helper.setBlock(new BlockPos(2, 3, 2), Blocks.OAK_LEAVES);

		mine(helper, player, new BlockPos(2, 2, 2));

		helper.assertBlockNotPresent(Blocks.OAK_LEAVES, new BlockPos(3, 2, 2));
		helper.assertBlockNotPresent(Blocks.OAK_LEAVES, new BlockPos(2, 3, 2));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void clearingLeavesNeverGoesDown(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_HOE);

		helper.setBlock(new BlockPos(2, 2, 2), Blocks.OAK_LEAVES);
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.OAK_LEAVES);
		helper.setBlock(new BlockPos(2, 3, 2), Blocks.OAK_LEAVES);

		mine(helper, player, new BlockPos(2, 2, 2));

		helper.assertBlockPresent(Blocks.OAK_LEAVES, new BlockPos(2, 1, 2));
		helper.assertBlockNotPresent(Blocks.OAK_LEAVES, new BlockPos(2, 3, 2));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void shearsClearLeavesToo(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.SHEARS);

		helper.setBlock(new BlockPos(2, 2, 2), Blocks.BIRCH_LEAVES);
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.BIRCH_LEAVES);

		mine(helper, player, new BlockPos(2, 2, 2));

		helper.assertBlockNotPresent(Blocks.BIRCH_LEAVES, new BlockPos(3, 2, 2));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void theWrongToolClearsNoLeaves(final GameTestHelper helper) {
		// An axe is not the game's tool for leaves, and a sword must not be:
		// a missed swing in a fight would otherwise clear the canopy.
		for (net.minecraft.world.item.Item item : List.of(Items.DIAMOND_AXE, Items.DIAMOND_SWORD)) {
			ServerPlayer player = preparePlayer(helper, item);
			helper.setBlock(new BlockPos(2, 2, 2), Blocks.OAK_LEAVES);
			helper.setBlock(new BlockPos(3, 2, 2), Blocks.OAK_LEAVES);

			mine(helper, player, new BlockPos(2, 2, 2));

			helper.assertBlockPresent(Blocks.OAK_LEAVES, new BlockPos(3, 2, 2));
		}
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void anAxeClearsAMushroomCap(final GameTestHelper helper) {
		// Caps are leaves of the mushroom tree, and the axe is their tool.
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);

		helper.setBlock(new BlockPos(2, 2, 2), Blocks.BROWN_MUSHROOM_BLOCK);
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.BROWN_MUSHROOM_BLOCK);

		mine(helper, player, new BlockPos(2, 2, 2));

		helper.assertBlockNotPresent(Blocks.BROWN_MUSHROOM_BLOCK, new BlockPos(3, 2, 2));
		helper.succeed();
	}

	// --------------------------------------------------------------- roots

	@GameTest(structure = EMPTY)
	public void rootsWithNoTrunkClearAsFarOutAsRootRadius(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);

		for (int x = 1; x <= 13; x++) {
			helper.setBlock(new BlockPos(x, 2, 2), Blocks.MANGROVE_ROOTS);
		}
		helper.setBlock(new BlockPos(1, 1, 2), Blocks.MANGROVE_ROOTS);

		mine(helper, player, new BlockPos(1, 2, 2));

		// rootRadius is 10: ten out go, the eleventh stays.
		for (int x = 2; x <= 11; x++) {
			helper.assertBlockNotPresent(Blocks.MANGROVE_ROOTS, new BlockPos(x, 2, 2));
		}
		helper.assertBlockPresent(Blocks.MANGROVE_ROOTS, new BlockPos(12, 2, 2));
		// And never below the root that was hit.
		helper.assertBlockPresent(Blocks.MANGROVE_ROOTS, new BlockPos(1, 1, 2));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void rootsAreFollowedUpWithoutAHeightLimit(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);

		// Further up than the radius, and further than the old six steps.
		for (int y = 1; y <= 14; y++) {
			helper.setBlock(new BlockPos(2, y, 2), Blocks.MANGROVE_ROOTS);
		}
		helper.setBlock(new BlockPos(2, 15, 2), Blocks.MANGROVE_LOG);

		mine(helper, player, new BlockPos(2, 1, 2));

		helper.assertBlockNotPresent(Blocks.MANGROVE_LOG, new BlockPos(2, 15, 2));
		for (int y = 2; y <= 14; y++) {
			helper.assertBlockNotPresent(Blocks.MANGROVE_ROOTS, new BlockPos(2, y, 2));
		}
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void aRootThatReachesTheTrunkFellsTheTree(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);

		helper.setBlock(new BlockPos(2, 0, 2), Blocks.MANGROVE_ROOTS);
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.MANGROVE_ROOTS);
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.MANGROVE_ROOTS);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.MANGROVE_ROOTS);
		for (int y = 3; y <= 7; y++) {
			helper.setBlock(new BlockPos(2, y, 2), Blocks.MANGROVE_LOG);
		}
		helper.setBlock(new BlockPos(3, 7, 2), Blocks.MANGROVE_LEAVES);

		mine(helper, player, new BlockPos(2, 1, 2));

		for (int y = 3; y <= 7; y++) {
			helper.assertBlockNotPresent(Blocks.MANGROVE_LOG, new BlockPos(2, y, 2));
		}
		helper.assertBlockNotPresent(Blocks.MANGROVE_LEAVES, new BlockPos(3, 7, 2));
		helper.assertBlockNotPresent(Blocks.MANGROVE_ROOTS, new BlockPos(2, 2, 2));
		helper.assertBlockNotPresent(Blocks.MANGROVE_ROOTS, new BlockPos(3, 1, 2));
		// The hit root's height is the floor.
		helper.assertBlockPresent(Blocks.MANGROVE_ROOTS, new BlockPos(2, 0, 2));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void cuttingTheTrunkLeavesTheRoots(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);

		helper.setBlock(new BlockPos(2, 1, 2), Blocks.MANGROVE_ROOTS);
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.MANGROVE_ROOTS);
		for (int y = 2; y <= 5; y++) {
			helper.setBlock(new BlockPos(2, y, 2), Blocks.MANGROVE_LOG);
		}

		mine(helper, player, new BlockPos(2, 2, 2));

		helper.assertBlockNotPresent(Blocks.MANGROVE_LOG, new BlockPos(2, 5, 2));
		helper.assertBlockPresent(Blocks.MANGROVE_ROOTS, new BlockPos(2, 1, 2));
		// Even a root level with the cut is left: the trunk never takes roots.
		helper.assertBlockPresent(Blocks.MANGROVE_ROOTS, new BlockPos(3, 2, 2));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void muddyRootsAreSoilNotTree(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);

		helper.setBlock(new BlockPos(2, 1, 2), Blocks.MANGROVE_ROOTS);
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.MUDDY_MANGROVE_ROOTS);
		helper.setBlock(new BlockPos(4, 1, 2), Blocks.MANGROVE_ROOTS);

		mine(helper, player, new BlockPos(2, 1, 2));

		helper.assertBlockPresent(Blocks.MUDDY_MANGROVE_ROOTS, new BlockPos(3, 1, 2));
		// Not a path either: the root beyond it is out of reach. (Diagonals
		// around it are empty, so the only way there is through the mud.)
		helper.assertBlockPresent(Blocks.MANGROVE_ROOTS, new BlockPos(4, 1, 2));
		helper.succeed();
	}

	// ---------------------------------------------------------- going below

	@GameTest(structure = EMPTY)
	public void treeBelowTakesTheStumpAndTheRoots(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		PlayerSettings.setTreeBelow(player, true);

		helper.setBlock(new BlockPos(2, 1, 2), Blocks.MANGROVE_ROOTS);
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.MANGROVE_ROOTS);
		for (int y = 2; y <= 6; y++) {
			helper.setBlock(new BlockPos(2, y, 2), Blocks.MANGROVE_LOG);
		}
		helper.setBlock(new BlockPos(3, 6, 2), Blocks.MANGROVE_LEAVES);

		// Halfway up the trunk.
		mine(helper, player, new BlockPos(2, 4, 2));

		for (int y = 2; y <= 6; y++) {
			helper.assertBlockNotPresent(Blocks.MANGROVE_LOG, new BlockPos(2, y, 2));
		}
		helper.assertBlockNotPresent(Blocks.MANGROVE_LEAVES, new BlockPos(3, 6, 2));
		helper.assertBlockNotPresent(Blocks.MANGROVE_ROOTS, new BlockPos(2, 1, 2));
		helper.assertBlockNotPresent(Blocks.MANGROVE_ROOTS, new BlockPos(3, 1, 2));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void treeBelowFromARootTakesTheRootsUnderIt(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		PlayerSettings.setTreeBelow(player, true);

		helper.setBlock(new BlockPos(2, 0, 2), Blocks.MANGROVE_ROOTS);
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.MANGROVE_ROOTS);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.MANGROVE_ROOTS);
		for (int y = 3; y <= 5; y++) {
			helper.setBlock(new BlockPos(2, y, 2), Blocks.MANGROVE_LOG);
		}

		mine(helper, player, new BlockPos(2, 1, 2));

		helper.assertBlockNotPresent(Blocks.MANGROVE_LOG, new BlockPos(2, 5, 2));
		helper.assertBlockNotPresent(Blocks.MANGROVE_ROOTS, new BlockPos(2, 2, 2));
		// With the setting off, this one stays as the floor.
		helper.assertBlockNotPresent(Blocks.MANGROVE_ROOTS, new BlockPos(2, 0, 2));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void treeBelowClearsLeavesUnderTheHitOne(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_HOE);
		PlayerSettings.setTreeBelow(player, true);

		helper.setBlock(new BlockPos(2, 2, 2), Blocks.OAK_LEAVES);
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.OAK_LEAVES);
		helper.setBlock(new BlockPos(2, 3, 2), Blocks.OAK_LEAVES);

		mine(helper, player, new BlockPos(2, 2, 2));

		helper.assertBlockNotPresent(Blocks.OAK_LEAVES, new BlockPos(2, 1, 2));
		helper.assertBlockNotPresent(Blocks.OAK_LEAVES, new BlockPos(2, 3, 2));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void treeBelowStillLeavesAnotherKindsLogs(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		PlayerSettings.setTreeBelow(player, true);
		config().breakLeaves = false;

		helper.setBlock(new BlockPos(2, 1, 2), Blocks.BIRCH_LOG);
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.OAK_LOG);
		for (int y = 2; y <= 5; y++) {
			helper.setBlock(new BlockPos(2, y, 2), Blocks.OAK_LOG);
		}

		mine(helper, player, new BlockPos(2, 4, 2));

		helper.assertBlockNotPresent(Blocks.OAK_LOG, new BlockPos(2, 2, 2));
		helper.assertBlockNotPresent(Blocks.OAK_LOG, new BlockPos(3, 1, 2));
		helper.assertBlockPresent(Blocks.BIRCH_LOG, new BlockPos(2, 1, 2));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void treeBelowStillKeepsPlacedLeaves(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_HOE);
		PlayerSettings.setTreeBelow(player, true);

		helper.setBlock(new BlockPos(2, 3, 2), Blocks.OAK_LEAVES);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.OAK_LEAVES);
		helper.setBlock(new BlockPos(2, 1, 2), placed(Blocks.OAK_LEAVES));

		mine(helper, player, new BlockPos(2, 3, 2));

		helper.assertBlockNotPresent(Blocks.OAK_LEAVES, new BlockPos(2, 2, 2));
		helper.assertBlockPresent(Blocks.OAK_LEAVES, new BlockPos(2, 1, 2));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void treeBelowRootsWithNoTrunkClearDownwardWithinTheRadius(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		PlayerSettings.setTreeBelow(player, true);

		// A root running down and out, as one does from a mangrove trunk.
		for (int x = 1; x <= 13; x++) {
			helper.setBlock(new BlockPos(x, Math.max(1, 14 - x), 2), Blocks.MANGROVE_ROOTS);
		}

		mine(helper, player, new BlockPos(1, 13, 2));

		for (int x = 2; x <= 11; x++) {
			helper.assertBlockNotPresent(Blocks.MANGROVE_ROOTS, new BlockPos(x, Math.max(1, 14 - x), 2));
		}
		// Still bounded by rootRadius sideways.
		helper.assertBlockPresent(Blocks.MANGROVE_ROOTS, new BlockPos(12, 2, 2));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void cubeBelowLeavesFellingAsItWas(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		PlayerSettings.setCubeBelow(player, true);
		config().breakLeaves = false;

		for (int y = 1; y <= 5; y++) {
			helper.setBlock(new BlockPos(2, y, 2), Blocks.OAK_LOG);
		}

		mine(helper, player, new BlockPos(2, 3, 2));

		helper.assertBlockNotPresent(Blocks.OAK_LOG, new BlockPos(2, 5, 2));
		helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(2, 2, 2));
		helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(2, 1, 2));
		helper.succeed();
	}

	// ----------------------------------------------------------- migration

	@GameTest(structure = EMPTY)
	public void oldFlatListsMigrateIntoTreeGroups(final GameTestHelper helper) {
		// Parsed the way the mod reads the file, so the old keys really arrive.
		String old = """
				{ "server": { "blocks": {
				  "fellable": ["minecraft:oak_log", "minecraft:mushroom_stem",
				               "minecraft:nether_wart_block", "othermod:big_log"],
				  "leaves": ["minecraft:oak_leaves", "othermod:big_leaves"]
				} } }""";
		BulkMineConfig config = new Gson().fromJson(old, BulkMineConfig.class);
		List<String> problems = config.sanitise();

		BulkMineConfig.Blocks blocks = config.server.blocks;
		helper.assertTrue(blocks.fellable == null && blocks.leaves == null, "old lists should be gone");
		// Everything a tree already covers -- nether wart included -- is absorbed...
		helper.assertTrue(blocks.customTree.logs.equals(List.of("othermod:big_log")),
				"custom logs were " + blocks.customTree.logs);
		// ...and only the hand-added extras move over, so nothing is lost.
		helper.assertTrue(blocks.customTree.leaves.equals(List.of("othermod:big_leaves")),
				"custom leaves were " + blocks.customTree.leaves);
		helper.assertTrue(blocks.trees.size() == 12, "default trees should be filled in");
		helper.assertTrue(problems.stream().anyMatch(p -> p.contains("migrated")), "migration was not reported");
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void oldRootDistanceMigratesToRootRadius(final GameTestHelper helper) {
		// The old default of 6 steps fell short, so it becomes the new default.
		BulkMineConfig config = new Gson().fromJson("{ \"server\": { \"rootDistance\": 6 } }", BulkMineConfig.class);
		List<String> problems = config.sanitise();
		helper.assertTrue(config.server.rootRadius == 10, "6 became " + config.server.rootRadius);
		helper.assertTrue(config.server.rootDistance == null, "old key should be gone");
		helper.assertTrue(problems.stream().anyMatch(p -> p.contains("rootRadius")), "migration was not reported");

		// A value picked by hand is kept.
		config = new Gson().fromJson("{ \"server\": { \"rootDistance\": 4 } }", BulkMineConfig.class);
		config.sanitise();
		helper.assertTrue(config.server.rootRadius == 4, "4 became " + config.server.rootRadius);

		// A new file has nothing to migrate.
		config = new Gson().fromJson("{ \"server\": { \"rootRadius\": 6 } }", BulkMineConfig.class);
		problems = config.sanitise();
		helper.assertTrue(config.server.rootRadius == 6, "6 became " + config.server.rootRadius);
		helper.assertTrue(problems.stream().noneMatch(p -> p.contains("rootRadius")), "nothing to migrate");
		helper.succeed();
	}

	// ------------------------------------------------- real, grown trees

	@GameTest(structure = EMPTY, maxTicks = 400)
	public void aGrownGiantSpruceFallsWhole(final GameTestHelper helper) {
		fellGrownTree(helper, 0, TreeFeatures.MEGA_SPRUCE, Blocks.PODZOL, Blocks.SPRUCE_LOG,
				state -> state.is(Blocks.SPRUCE_LEAVES), 20);
	}

	@GameTest(structure = EMPTY, maxTicks = 400)
	public void aGrownGiantJungleTreeFallsWhole(final GameTestHelper helper) {
		fellGrownTree(helper, 1, TreeFeatures.MEGA_JUNGLE_TREE, Blocks.GRASS_BLOCK, Blocks.JUNGLE_LOG,
				state -> state.is(Blocks.JUNGLE_LEAVES), 20);
	}

	@GameTest(structure = EMPTY, maxTicks = 400)
	public void aGrownTallBirchFallsWhole(final GameTestHelper helper) {
		fellGrownTree(helper, 2, TreeFeatures.SUPER_BIRCH_BEES_0002, Blocks.GRASS_BLOCK, Blocks.BIRCH_LOG,
				state -> state.is(Blocks.BIRCH_LEAVES), 10);
	}

	@GameTest(structure = EMPTY, maxTicks = 400)
	public void aGrownDarkOakFallsWhole(final GameTestHelper helper) {
		fellGrownTree(helper, 3, TreeFeatures.DARK_OAK, Blocks.GRASS_BLOCK, Blocks.DARK_OAK_LOG,
				state -> state.is(Blocks.DARK_OAK_LEAVES), 1);
	}

	@GameTest(structure = EMPTY, maxTicks = 400)
	public void aGrownRedMushroomFallsWithItsCap(final GameTestHelper helper) {
		fellGrownTree(helper, 4, TreeFeatures.HUGE_RED_MUSHROOM, Blocks.MYCELIUM, Blocks.MUSHROOM_STEM,
				state -> state.is(Blocks.RED_MUSHROOM_BLOCK), 1);
	}

	@GameTest(structure = EMPTY, maxTicks = 400)
	public void aGrownBrownMushroomFallsWithItsCap(final GameTestHelper helper) {
		fellGrownTree(helper, 5, TreeFeatures.HUGE_BROWN_MUSHROOM, Blocks.MYCELIUM, Blocks.MUSHROOM_STEM,
				state -> state.is(Blocks.BROWN_MUSHROOM_BLOCK), 1);
	}

	@GameTest(structure = EMPTY, maxTicks = 400)
	public void aGrownCrimsonFungusFallsWithItsCap(final GameTestHelper helper) {
		fellGrownTree(helper, 6, TreeFeatures.CRIMSON_FUNGUS_PLANTED, Blocks.CRIMSON_NYLIUM, Blocks.CRIMSON_STEM,
				state -> state.is(Blocks.NETHER_WART_BLOCK) || state.is(Blocks.SHROOMLIGHT), 1);
	}

	@GameTest(structure = EMPTY, maxTicks = 400)
	public void aGrownWarpedFungusFallsWithItsCap(final GameTestHelper helper) {
		fellGrownTree(helper, 7, TreeFeatures.WARPED_FUNGUS_PLANTED, Blocks.WARPED_NYLIUM, Blocks.WARPED_STEM,
				state -> state.is(Blocks.WARPED_WART_BLOCK) || state.is(Blocks.SHROOMLIGHT), 1);
	}

	@GameTest(structure = EMPTY, maxTicks = 400)
	public void aGrownMangroveCutAtTheTrunkKeepsItsRoots(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = site(8);
		grow(helper, level, base, TreeFeatures.MANGROVE, Blocks.MUD, Blocks.MANGROVE_LOG, 1, 1);
		int rootsBefore = find(level, base, state -> state.is(Blocks.MANGROVE_ROOTS)).size();

		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		player.gameMode.destroyBlock(lowest(find(level, base, state -> state.is(Blocks.MANGROVE_LOG))));

		assertNoneLeft(helper, level, base, state -> state.is(Blocks.MANGROVE_LOG), "mangrove logs");
		assertNoneLeft(helper, level, base, state -> state.is(Blocks.MANGROVE_LEAVES), "mangrove leaves");
		int rootsAfter = find(level, base, state -> state.is(Blocks.MANGROVE_ROOTS)).size();
		helper.assertTrue(rootsAfter == rootsBefore,
				"cutting the trunk should leave every root, had " + rootsBefore + ", now " + rootsAfter);
		helper.succeed();
	}

	@GameTest(structure = EMPTY, maxTicks = 400)
	public void aGrownMangroveCutAtARootFallsWholeFromThere(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = site(9);
		grow(helper, level, base, TreeFeatures.MANGROVE, Blocks.MUD, Blocks.MANGROVE_LOG, 1, 1);

		// The root nearest the trunk, so the trunk is in reach.
		List<BlockPos> roots = find(level, base, state -> state.is(Blocks.MANGROVE_ROOTS));
		BlockPos hit = roots.stream().max((a, b) -> Integer.compare(a.getY(), b.getY())).orElseThrow();

		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		player.gameMode.destroyBlock(hit);

		assertNoneLeft(helper, level, base, state -> state.is(Blocks.MANGROVE_LOG), "mangrove logs");
		assertNoneLeft(helper, level, base, state -> state.is(Blocks.MANGROVE_LEAVES), "mangrove leaves");
		long rootsAtOrAbove = find(level, base, state -> state.is(Blocks.MANGROVE_ROOTS)).stream()
				.filter(pos -> pos.getY() >= hit.getY())
				.count();
		helper.assertTrue(rootsAtOrAbove == 0, rootsAtOrAbove + " roots left at or above the hit one");
		helper.succeed();
	}

	@GameTest(structure = EMPTY, maxTicks = 400)
	public void aGrownTallMangroveFallsFromItsFarthestRoot(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = site(10);
		// Roots as wide as the generator makes them. Six out is the most it
		// managed in forty seeds, but the way back from the lowest of them
		// climbs as well, and took more than the old six steps along the roots.
		grow(helper, level, base, TreeFeatures.TALL_MANGROVE, Blocks.MUD, Blocks.MANGROVE_LOG, 1, 6);

		BlockPos trunk = lowest(find(level, base, state -> state.is(Blocks.MANGROVE_LOG)));
		// The farthest root out, and of those the lowest: the longest way back.
		BlockPos hit = find(level, base, state -> state.is(Blocks.MANGROVE_ROOTS)).stream()
				.max(java.util.Comparator.<BlockPos>comparingInt(pos -> spread(trunk, pos))
						.thenComparingInt(pos -> -pos.getY()))
				.orElseThrow();

		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		player.gameMode.destroyBlock(hit);

		assertNoneLeft(helper, level, base, state -> state.is(Blocks.MANGROVE_LOG), "mangrove logs");
		assertNoneLeft(helper, level, base, state -> state.is(Blocks.MANGROVE_LEAVES), "mangrove leaves");
		long rootsAtOrAbove = find(level, base, state -> state.is(Blocks.MANGROVE_ROOTS)).stream()
				.filter(pos -> pos.getY() >= hit.getY())
				.count();
		helper.assertTrue(rootsAtOrAbove == 0, rootsAtOrAbove + " roots left at or above the hit one, "
				+ spread(trunk, hit) + " out from the trunk");
		helper.succeed();
	}

	@GameTest(structure = EMPTY, maxTicks = 400)
	public void aGrownMangroveCutWithTreeBelowLeavesNothing(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = site(11);
		grow(helper, level, base, TreeFeatures.TALL_MANGROVE, Blocks.MUD, Blocks.MANGROVE_LOG, 1, 1);

		List<BlockPos> logs = find(level, base, state -> state.is(Blocks.MANGROVE_LOG));
		BlockPos lowest = lowest(logs);
		// Partway up the trunk, so the stump below the cut has to go as well.
		BlockPos hit = lowest.above(2);

		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		PlayerSettings.setTreeBelow(player, true);
		player.gameMode.destroyBlock(hit);

		assertNoneLeft(helper, level, base, state -> state.is(Blocks.MANGROVE_LOG), "mangrove logs");
		assertNoneLeft(helper, level, base, state -> state.is(Blocks.MANGROVE_LEAVES), "mangrove leaves");
		assertNoneLeft(helper, level, base, state -> state.is(Blocks.MANGROVE_ROOTS), "mangrove roots");
		helper.succeed();
	}

	@GameTest(structure = EMPTY, maxTicks = 400)
	public void aGrownGiantSpruceCutHalfwayWithTreeBelowFallsWhole(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = site(12);
		int height = grow(helper, level, base, TreeFeatures.MEGA_SPRUCE, Blocks.PODZOL, Blocks.SPRUCE_LOG, 20, 0);

		BlockPos hit = lowest(find(level, base, state -> state.is(Blocks.SPRUCE_LOG))).above(height / 2);

		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		PlayerSettings.setTreeBelow(player, true);
		player.gameMode.destroyBlock(hit);

		assertNoneLeft(helper, level, base, state -> state.is(Blocks.SPRUCE_LOG),
				"logs of a " + height + "-high trunk cut at " + (height / 2));
		helper.succeed();
	}

	// ------------------------------------------------------------ plumbing

	/** A leaf as a player would place it: marked, and so never decaying. */
	private static BlockState placed(final Block leaves) {
		return leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	}

	/**
	 * Grows a tree with the game's own generator, cuts its lowest log, and
	 * checks that no log survives and no canopy that was joined to the tree.
	 *
	 * <p>"Joined" is worked out here, before the cut, with a plain flood fill
	 * that has no distance limit -- an independent answer to compare the mod
	 * against. It matters for the nether fungi: the generator scatters a few
	 * wart blocks low around the stem that touch nothing at all, and no rule
	 * that follows the tree can reach those. Every other tree has none.
	 *
	 * @param minTrunk retries other seeds until the trunk is at least this
	 *                 tall, so the giant trees really test the height limit
	 */
	private static void fellGrownTree(
			final GameTestHelper helper, final int index, final ResourceKey<ConfiguredFeature<?, ?>> feature,
			final Block soil, final Block log, final Predicate<BlockState> canopy, final int minTrunk) {
		ServerLevel level = helper.getLevel();
		BlockPos base = site(index);
		int height = grow(helper, level, base, feature, soil, log, minTrunk, 0);

		List<BlockPos> logs = find(level, base, state -> state.is(log));
		java.util.Set<BlockPos> joined = joinedCanopy(level, logs, canopy);

		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		player.gameMode.destroyBlock(lowest(logs));

		assertNoneLeft(helper, level, base, state -> state.is(log), "logs of a " + height + "-high trunk");
		List<BlockPos> left = find(level, base, canopy).stream().filter(joined::contains).toList();
		helper.assertTrue(left.isEmpty(), left.size() + " joined canopy blocks of a " + height
				+ "-high trunk left, e.g. " + left.stream().limit(3).map(pos -> pos.subtract(base).toShortString()).toList());
		helper.succeed();
	}

	/** Every canopy block reachable from the logs through other canopy blocks. */
	private static java.util.Set<BlockPos> joinedCanopy(
			final ServerLevel level, final List<BlockPos> logs, final Predicate<BlockState> canopy) {
		java.util.Set<BlockPos> seen = new java.util.HashSet<>(logs);
		java.util.Set<BlockPos> joined = new java.util.HashSet<>();
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>(logs);
		while (!queue.isEmpty()) {
			BlockPos current = queue.poll();
			for (BlockPos next : BlockPos.betweenClosed(current.offset(-1, -1, -1), current.offset(1, 1, 1))) {
				BlockPos pos = next.immutable();
				if (seen.add(pos) && canopy.test(level.getBlockState(pos))) {
					joined.add(pos);
					queue.add(pos);
				}
			}
		}
		return joined;
	}

	/**
	 * Far from the test grid and from each other, so a 30-block giant can
	 * never reach into another test, and cleared first because the test world
	 * is saved between runs.
	 */
	private static BlockPos site(final int index) {
		return new BlockPos(20000 + index * 96, 100, 20000);
	}

	/**
	 * @param minRootSpread retries other seeds until some root grew at least
	 *                      this far out from the trunk; 0 for no roots at all
	 * @return the height of the trunk that grew
	 */
	private static int grow(
			final GameTestHelper helper, final ServerLevel level, final BlockPos base,
			final ResourceKey<ConfiguredFeature<?, ?>> key, final Block soil, final Block log,
			final int minTrunk, final int minRootSpread) {
		ConfiguredFeature<?, ?> feature = level.registryAccess()
				.lookupOrThrow(Registries.CONFIGURED_FEATURE).getValueOrThrow(key);

		int placed = 0;
		int bestSpread = 0;
		for (long seed = 0; seed < 40; seed++) {
			clear(level, base);
			// Wide enough for a mangrove's roots, which reach up to 8 out.
			for (int dx = -SOIL; dx <= SOIL; dx++) {
				for (int dz = -SOIL; dz <= SOIL; dz++) {
					level.setBlock(base.offset(dx, -1, dz), soil.defaultBlockState(), Block.UPDATE_CLIENTS);
					// Real ground under the soil: mangrove roots grow down through
					// mud until something stops them, and give up if nothing does.
					level.setBlock(base.offset(dx, -2, dz), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}

			if (!feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed), base)) {
				continue;
			}
			List<BlockPos> logs = find(level, base, state -> state.is(log));
			if (logs.isEmpty()) {
				continue;
			}
			int height = logs.stream().mapToInt(BlockPos::getY).max().orElseThrow()
					- lowest(logs).getY() + 1;
			BlockPos trunk = lowest(logs);
			int rootSpread = find(level, base, state -> state.is(Blocks.MANGROVE_ROOTS)).stream()
					.mapToInt(pos -> spread(trunk, pos)).max().orElse(0);
			placed++;
			bestSpread = Math.max(bestSpread, rootSpread);
			if (height >= minTrunk && rootSpread >= minRootSpread) {
				return height;
			}
		}
		helper.fail("could not grow " + key.identifier() + " to " + minTrunk + " blocks"
				+ (minRootSpread == 0 ? "" : " with roots " + minRootSpread + " out")
				+ " (" + placed + " grew, widest roots " + bestSpread + " out)");
		return 0;
	}

	private static final int REACH = 14;
	private static final int SOIL = 10;

	/** How far out from the trunk a root is, horizontally. */
	private static int spread(final BlockPos trunk, final BlockPos pos) {
		return Math.max(Math.abs(pos.getX() - trunk.getX()), Math.abs(pos.getZ() - trunk.getZ()));
	}

	private static void clear(final ServerLevel level, final BlockPos base) {
		BlockState air = Blocks.AIR.defaultBlockState();
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-REACH, -6, -REACH), base.offset(REACH, 44, REACH))) {
			if (!level.getBlockState(pos).isAir()) {
				level.setBlock(pos, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		}
	}

	private static List<BlockPos> find(
			final ServerLevel level, final BlockPos base, final Predicate<BlockState> test) {
		List<BlockPos> found = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-REACH, -6, -REACH), base.offset(REACH, 44, REACH))) {
			if (test.test(level.getBlockState(pos))) {
				found.add(pos.immutable());
			}
		}
		return found;
	}

	private static BlockPos lowest(final List<BlockPos> positions) {
		return positions.stream().min((a, b) -> Integer.compare(a.getY(), b.getY())).orElseThrow();
	}

	private static void assertNoneLeft(
			final GameTestHelper helper, final ServerLevel level, final BlockPos base,
			final Predicate<BlockState> test, final String what) {
		List<BlockPos> left = find(level, base, test);
		helper.assertTrue(left.isEmpty(), left.size() + " " + what + " left, e.g. "
				+ left.stream().limit(3).map(pos -> pos.subtract(base).toShortString()).toList());
	}
}
