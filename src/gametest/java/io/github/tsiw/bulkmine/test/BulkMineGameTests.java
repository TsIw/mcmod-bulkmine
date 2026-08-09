package io.github.tsiw.bulkmine.test;

import io.github.tsiw.bulkmine.Role;
import io.github.tsiw.bulkmine.config.BulkMineConfig;
import io.github.tsiw.bulkmine.config.ConfigManager;
import io.github.tsiw.bulkmine.config.ConfigWriter;
import io.github.tsiw.bulkmine.mining.CubeMiner;
import io.github.tsiw.bulkmine.registry.BlockWhitelist;
import io.github.tsiw.bulkmine.registry.ToolRegistry;
import io.github.tsiw.bulkmine.state.PlayerSettings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Covers the server-side rules end to end: shape, whitelist, roles, felling,
 * durability and drop placement.
 *
 * <p>Breaks go through {@code ServerPlayerGameMode.destroyBlock}, the real
 * path, rather than firing the Fabric events by hand. That matters for tool
 * wear: vanilla spends its own durability point at a specific moment relative
 * to the break events, and only the real path gets that ordering right.
 */
public class BulkMineGameTests {
	private static final String EMPTY = "fabric-gametest-api-v1:empty";

	// ------------------------------------------------------------ cube shape

	@GameTest(structure = EMPTY)
	public void cubeLeavesTheLayerBelowIntact(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_PICKAXE);
		BlockPos origin = new BlockPos(3, 3, 3);
		fill(helper, origin, 1, Blocks.STONE);

		mine(helper, player, origin);

		// The 3x3 slice under the cursor is untouched...
		forEachOffset(1, (dx, dz) -> helper.assertBlockPresent(Blocks.STONE, origin.offset(dx, -1, dz)));
		// ...while the cursor's own layer and the one above it are gone.
		forEachOffset(1, (dx, dz) -> {
			helper.assertBlockNotPresent(Blocks.STONE, origin.offset(dx, 0, dz));
			helper.assertBlockNotPresent(Blocks.STONE, origin.offset(dx, 1, dz));
		});
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void cubeShapeIsEighteenPositions(final GameTestHelper helper) {
		List<BlockPos> positions = CubeMiner.collect(BlockPos.ZERO, 3);
		// 3x3x2 minus the origin, which vanilla already broke.
		helper.assertTrue(positions.size() == 17, "expected 17 extra positions, got " + positions.size());
		helper.assertTrue(positions.stream().noneMatch(pos -> pos.getY() < 0), "cube reached below the cursor");
		helper.assertTrue(CubeMiner.collect(BlockPos.ZERO, 5).size() == 74, "5x5x3 should be 74 extra positions");
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void cubeSkipsBlocksOffTheWhitelist(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_PICKAXE);
		BlockPos origin = new BlockPos(3, 3, 3);
		fill(helper, origin, 1, Blocks.STONE);

		BlockPos crafted = origin.offset(1, 0, 1);
		helper.setBlock(crafted, Blocks.OAK_PLANKS);

		mine(helper, player, origin);

		helper.assertBlockPresent(Blocks.OAK_PLANKS, crafted);
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void pickaxeDoesNotDigDirt(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_PICKAXE);
		BlockPos origin = new BlockPos(3, 3, 3);
		fill(helper, origin, 1, Blocks.STONE);

		BlockPos dirt = origin.offset(1, 0, 0);
		helper.setBlock(dirt, Blocks.DIRT);

		mine(helper, player, origin);

		helper.assertBlockPresent(Blocks.DIRT, dirt);
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void multiRoleToolCrossesCategories(final GameTestHelper helper) {
		// Stand in for a paxel: one item registered as pickaxe, axe and shovel.
		// It has to be a pickaxe rather than, say, a hoe, because a real paxel
		// declares itself correct for all four mineable tags and blocks the
		// tool cannot harvest are now skipped.
		ServerPlayer player = preparePlayer(helper, Items.NETHERITE_PICKAXE);
		registerTool(Items.NETHERITE_PICKAXE, Role.ALL);
		BlockPos origin = new BlockPos(3, 3, 3);
		fill(helper, origin, 1, Blocks.STONE);

		BlockPos dirt = origin.offset(1, 0, 0);
		helper.setBlock(dirt, Blocks.DIRT);

		mine(helper, player, origin);

		helper.assertBlockNotPresent(Blocks.DIRT, dirt);
		helper.assertBlockNotPresent(Blocks.STONE, origin.offset(-1, 0, 0));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void cubeNeverEatsLogs(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.NETHERITE_PICKAXE);
		registerTool(Items.NETHERITE_PICKAXE, Role.ALL);
		BlockPos origin = new BlockPos(3, 3, 3);
		fill(helper, origin, 1, Blocks.STONE);

		BlockPos log = origin.offset(1, 1, 1);
		helper.setBlock(log, Blocks.OAK_LOG);

		mine(helper, player, origin);

		// Logs belong to felling, so digging beside a tree leaves the trunk.
		helper.assertBlockPresent(Blocks.OAK_LOG, log);
		helper.succeed();
	}

	// --------------------------------------------------------------- felling

	@GameTest(structure = EMPTY)
	public void fellingGoesUpwardsOnly(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		config().breakLeaves = false;

		BlockPos below = new BlockPos(3, 1, 3);
		BlockPos origin = new BlockPos(3, 2, 3);
		helper.setBlock(below, Blocks.OAK_LOG);
		for (int y = 2; y <= 6; y++) {
			helper.setBlock(new BlockPos(3, y, 3), Blocks.OAK_LOG);
		}

		mine(helper, player, origin);

		helper.assertBlockPresent(Blocks.OAK_LOG, below);
		for (int y = 3; y <= 6; y++) {
			helper.assertBlockNotPresent(Blocks.OAK_LOG, new BlockPos(3, y, 3));
		}
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void fellingFollowsDiagonals(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		config().breakLeaves = false;

		BlockPos origin = new BlockPos(2, 2, 2);
		BlockPos diagonal = new BlockPos(3, 3, 3);
		helper.setBlock(origin, Blocks.OAK_LOG);
		helper.setBlock(diagonal, Blocks.OAK_LOG);

		mine(helper, player, origin);

		helper.assertBlockNotPresent(Blocks.OAK_LOG, diagonal);
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void fellingIgnoresOtherSpecies(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		config().breakLeaves = false;

		BlockPos origin = new BlockPos(3, 2, 3);
		BlockPos birch = new BlockPos(3, 3, 3);
		helper.setBlock(origin, Blocks.OAK_LOG);
		helper.setBlock(birch, Blocks.BIRCH_LOG);

		mine(helper, player, origin);

		helper.assertBlockPresent(Blocks.BIRCH_LOG, birch);
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void fellingClearsNearbyLeaves(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		config().breakLeaves = true;
		config().leafDistance = 2;

		BlockPos origin = new BlockPos(3, 2, 3);
		helper.setBlock(origin, Blocks.OAK_LOG);

		BlockPos near = new BlockPos(4, 2, 3);
		BlockPos alsoNear = new BlockPos(5, 2, 3);
		BlockPos tooFar = new BlockPos(6, 2, 3);
		helper.setBlock(near, Blocks.OAK_LEAVES);
		helper.setBlock(alsoNear, Blocks.OAK_LEAVES);
		helper.setBlock(tooFar, Blocks.OAK_LEAVES);

		mine(helper, player, origin);

		helper.assertBlockNotPresent(Blocks.OAK_LEAVES, near);
		helper.assertBlockNotPresent(Blocks.OAK_LEAVES, alsoNear);
		helper.assertBlockPresent(Blocks.OAK_LEAVES, tooFar);
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void fellingRespectsTheLogCap(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_AXE);
		config().breakLeaves = false;
		config().maxLogs = 2;

		BlockPos origin = new BlockPos(3, 1, 3);
		for (int y = 1; y <= 6; y++) {
			helper.setBlock(new BlockPos(3, y, 3), Blocks.OAK_LOG);
		}

		mine(helper, player, origin);

		long left = 0;
		for (int y = 2; y <= 6; y++) {
			if (helper.getBlockState(new BlockPos(3, y, 3)).is(Blocks.OAK_LOG)) {
				left++;
			}
		}
		helper.assertTrue(left == 3, "cap of 2 should have left 3 of the 5 logs above, left " + left);
		helper.succeed();
	}

	// ------------------------------------------------------- drops, tool wear

	@GameTest(structure = EMPTY)
	public void dropsLandOnTheOriginWhenAggregated(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_PICKAXE);
		config().aggregateDrops = true;

		BlockPos origin = new BlockPos(3, 3, 3);
		fill(helper, origin, 1, Blocks.COBBLESTONE);

		mine(helper, player, origin);

		// Item entities are not visible until the next tick, so retry rather
		// than assert immediately.
		helper.succeedWhen(() -> {
			helper.assertItemEntityPresent(Items.COBBLESTONE, origin, 1.0);
			helper.assertItemEntityNotPresent(Items.COBBLESTONE, origin.offset(1, 1, 1), 0.4);
		});
	}

	@GameTest(structure = EMPTY)
	public void toolWithTwoDurabilitySurvivesOnOne(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_PICKAXE);
		ItemStack tool = player.getMainHandItem();
		// Two points left, far fewer than the 17 blocks about to be broken.
		tool.setDamageValue(tool.getMaxDamage() - 2);

		BlockPos origin = new BlockPos(3, 3, 3);
		fill(helper, origin, 1, Blocks.STONE);

		mine(helper, player, origin);

		ItemStack after = player.getMainHandItem();
		helper.assertFalse(after.isEmpty(), "the tool should not have broken");
		int remaining = after.getMaxDamage() - after.getDamageValue();
		helper.assertTrue(remaining == 1, "expected 1 durability left, got " + remaining);

		// And the sweep still completed despite running out of durability.
		helper.assertBlockNotPresent(Blocks.STONE, origin.offset(1, 1, 1));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void toolWithOneDurabilityBreaksButSweepFinishes(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_PICKAXE);
		ItemStack tool = player.getMainHandItem();
		// Its very last point: vanilla spends it on the origin block.
		tool.setDamageValue(tool.getMaxDamage() - 1);

		BlockPos origin = new BlockPos(3, 3, 3);
		fill(helper, origin, 1, Blocks.STONE);

		mine(helper, player, origin);

		helper.assertTrue(player.getMainHandItem().isEmpty(), "the tool should have broken");
		// Breaking the tool must not cut the sweep short.
		forEachOffset(1, (dx, dz) -> {
			helper.assertBlockNotPresent(Blocks.STONE, origin.offset(dx, 0, dz));
			helper.assertBlockNotPresent(Blocks.STONE, origin.offset(dx, 1, dz));
		});
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void cubeSizeIsPerPlayer(final GameTestHelper helper) {
		ServerPlayer small = preparePlayer(helper, Items.DIAMOND_PICKAXE);
		PlayerSettings.setCubeSize(small, 1);
		BlockPos smallOrigin = new BlockPos(2, 2, 2);
		fill(helper, smallOrigin, 1, Blocks.STONE);
		mine(helper, small, smallOrigin);
		// Size 1 is the origin alone, so its neighbour survives.
		helper.assertBlockPresent(Blocks.STONE, smallOrigin.offset(1, 0, 0));

		ServerPlayer big = helper.makeMockServerPlayerInLevel();
		big.getAbilities().instabuild = false;
		big.onUpdateAbilities();
		big.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
		PlayerSettings.setEnabled(big, true);
		PlayerSettings.setCubeSize(big, 3);

		BlockPos bigOrigin = new BlockPos(5, 5, 5);
		fill(helper, bigOrigin, 1, Blocks.STONE);
		mine(helper, big, bigOrigin);
		helper.assertBlockNotPresent(Blocks.STONE, bigOrigin.offset(1, 0, 0));

		// The first player's size was untouched by the second player's change.
		helper.assertTrue(PlayerSettings.cubeSize(small) == 1,
				"expected size 1, got " + PlayerSettings.cubeSize(small));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void blacklistBeatsTheWhitelist(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_PICKAXE);
		config().blocks.blacklist.add("minecraft:diamond_ore");
		BlockWhitelist.reload(ConfigManager.get());

		BlockPos origin = new BlockPos(3, 3, 3);
		fill(helper, origin, 1, Blocks.STONE);
		BlockPos ore = origin.offset(1, 0, 0);
		helper.setBlock(ore, Blocks.DIAMOND_ORE);

		mine(helper, player, origin);

		// diamond_ore is on the pickaxe list, but the blacklist wins.
		helper.assertBlockPresent(Blocks.DIAMOND_ORE, ore);
		helper.assertBlockNotPresent(Blocks.STONE, origin.offset(-1, 0, 0));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void removingABlockFromTheConfigStopsItBeingMined(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_PICKAXE);
		// Nothing is hard-coded: dropping the line is enough to exclude it.
		config().blocks.pickaxe.remove("minecraft:andesite");
		BlockWhitelist.reload(ConfigManager.get());

		BlockPos origin = new BlockPos(3, 3, 3);
		fill(helper, origin, 1, Blocks.STONE);
		BlockPos andesite = origin.offset(1, 0, 0);
		helper.setBlock(andesite, Blocks.ANDESITE);

		mine(helper, player, origin);

		helper.assertBlockPresent(Blocks.ANDESITE, andesite);
		helper.succeed();
	}

	// --------------------------------------------------- tool strong enough

	@GameTest(structure = EMPTY)
	public void tooWeakAToolSkipsTheBlocksItCannotHarvest(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.STONE_PICKAXE);

		BlockPos origin = new BlockPos(3, 3, 3);
		fill(helper, origin, 1, Blocks.STONE);
		BlockPos ore = origin.offset(1, 0, 0);
		helper.setBlock(ore, Blocks.DIAMOND_ORE);

		mine(helper, player, origin);

		// Stone drops for a stone pickaxe; diamond ore would drop nothing, so
		// destroying it in bulk would just waste it.
		helper.assertBlockPresent(Blocks.DIAMOND_ORE, ore);
		helper.assertBlockNotPresent(Blocks.STONE, origin.offset(-1, 0, 0));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void tooWeakAToolOnTheCursorBlockSweepsNothing(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.STONE_PICKAXE);

		BlockPos origin = new BlockPos(3, 3, 3);
		fill(helper, origin, 1, Blocks.DIAMOND_ORE);

		mine(helper, player, origin);

		// The game still breaks what the player aimed at...
		helper.assertBlockNotPresent(Blocks.DIAMOND_ORE, origin);
		// ...but nothing else goes with it.
		helper.assertBlockPresent(Blocks.DIAMOND_ORE, origin.offset(1, 0, 0));
		helper.assertBlockPresent(Blocks.DIAMOND_ORE, origin.offset(0, 1, 0));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void requireCorrectToolCanBeTurnedOff(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.STONE_PICKAXE);
		config().requireCorrectTool = false;

		BlockPos origin = new BlockPos(3, 3, 3);
		fill(helper, origin, 1, Blocks.STONE);
		BlockPos ore = origin.offset(1, 0, 0);
		helper.setBlock(ore, Blocks.DIAMOND_ORE);

		mine(helper, player, origin);

		helper.assertBlockNotPresent(Blocks.DIAMOND_ORE, ore);
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void aStrongEnoughToolStillClearsTheOre(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.IRON_PICKAXE);

		BlockPos origin = new BlockPos(3, 3, 3);
		fill(helper, origin, 1, Blocks.STONE);
		BlockPos ore = origin.offset(1, 0, 0);
		helper.setBlock(ore, Blocks.DIAMOND_ORE);

		mine(helper, player, origin);

		helper.assertBlockNotPresent(Blocks.DIAMOND_ORE, ore);
		helper.succeed();
	}

	// ------------------------------------------------------------- gating

	@GameTest(structure = EMPTY)
	public void nothingHappensWhenToggledOff(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_PICKAXE);
		PlayerSettings.setEnabled(player, false);

		BlockPos origin = new BlockPos(3, 3, 3);
		fill(helper, origin, 1, Blocks.STONE);

		mine(helper, player, origin);

		helper.assertBlockPresent(Blocks.STONE, origin.offset(1, 0, 0));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void unregisteredToolDoesNothing(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.STICK);
		BlockPos origin = new BlockPos(3, 3, 3);
		fill(helper, origin, 1, Blocks.STONE);

		mine(helper, player, origin);

		helper.assertBlockPresent(Blocks.STONE, origin.offset(1, 0, 0));
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void protectionVetoIsHonoured(final GameTestHelper helper) {
		ServerPlayer player = preparePlayer(helper, Items.DIAMOND_PICKAXE);
		BlockPos origin = new BlockPos(3, 3, 3);
		BlockPos protectedPos = helper.absolutePos(origin.offset(1, 0, 0));
		fill(helper, origin, 1, Blocks.STONE);

		Guard.protectedPos = protectedPos;
		try {
			mine(helper, player, origin);
		} finally {
			Guard.protectedPos = null;
		}

		helper.assertBlockPresent(Blocks.STONE, origin.offset(1, 0, 0));
		helper.assertBlockNotPresent(Blocks.STONE, origin.offset(-1, 0, 0));
		helper.succeed();
	}

	// ------------------------------------------------------- persisted state

	@GameTest(structure = EMPTY)
	public void attachmentsExistBeforeAnyPlayerDataIsRead(final GameTestHelper helper) {
		// Recorded at server start by AttachmentProbe, which is the only moment
		// this is observable -- see that class for why.
		helper.assertTrue(AttachmentProbe.registeredBeforeServerStart,
				"attachment types were not registered during mod init, so saved "
						+ "per-player settings would be discarded on world load");
		helper.succeed();
	}

	// ------------------------------------------------------------ config i/o

	@GameTest(structure = EMPTY)
	public void shippedDefaultConfigMatchesTheWriter(final GameTestHelper helper) throws IOException {
		String rendered = ConfigWriter.render(new BulkMineConfig());
		String shipped;
		try (InputStream in = BulkMineGameTests.class.getResourceAsStream("/default-config.json")) {
			helper.assertTrue(in != null, "default-config.json is missing from the jar");
			shipped = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		helper.assertTrue(normalise(shipped).equals(normalise(rendered)),
				"default-config.json has drifted from ConfigWriter.render()");
		helper.succeed();
	}

	@GameTest(structure = EMPTY)
	public void writtenConfigKeepsCommentsAndValues(final GameTestHelper helper) {
		BulkMineConfig config = new BulkMineConfig();
		config.server.defaultCubeSize = 5;
		config.server.tools.add(new BulkMineConfig.ToolEntry("example:paxel", List.of("pickaxe", "axe", "shovel")));
		config.server.blocks.blacklist.add("minecraft:gravel");
		config.client.hudCorner = "TOP_LEFT";

		String rendered = ConfigWriter.render(config);

		helper.assertTrue(rendered.contains("//  SERVER"), "server section comment was lost");
		helper.assertTrue(rendered.contains("//  CLIENT"), "client section comment was lost");
		helper.assertTrue(rendered.contains("\"defaultCubeSize\": 5,"), "defaultCubeSize was not written");
		helper.assertTrue(rendered.contains("\"example:paxel\""), "tool entry was not written");
		helper.assertTrue(rendered.contains("\"blacklist\": [\n        \"minecraft:gravel\"\n      ]"),
				"blacklist entry was not written");
		helper.assertTrue(rendered.contains("\"hudCorner\": \"TOP_LEFT\""), "client value was not written");
		// The whole point of the rework: block lists live in the file, not in code.
		helper.assertTrue(rendered.contains("\"minecraft:ancient_debris\""), "block lists were not written out");
		helper.succeed();
	}

	// ------------------------------------------------------------- plumbing

	/** Fresh defaults plus a toggled-on player holding {@code item}. */
	private static ServerPlayer preparePlayer(final GameTestHelper helper, final net.minecraft.world.item.Item item) {
		resetConfig();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();

		// The mock player hard-codes gameMode() to CREATIVE, which would skip
		// drops and tool wear entirely. Clear the instant-build ability, which
		// is what both vanilla and this mod actually test against.
		player.getAbilities().instabuild = false;
		player.onUpdateAbilities();

		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(item));
		PlayerSettings.setEnabled(player, true);
		return player;
	}

	private static BulkMineConfig.Server config() {
		return ConfigManager.get().server;
	}

	private static void resetConfig() {
		// Replaces the live config without touching the file on disk.
		ConfigManager.replace(new BulkMineConfig());
		ToolRegistry.reload(ConfigManager.get());
		BlockWhitelist.reload(ConfigManager.get());
	}

	private static void registerTool(final net.minecraft.world.item.Item item, final Set<Role> roles) {
		config().tools.add(new BulkMineConfig.ToolEntry(
				net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(),
				roles.stream().map(Role::serialized).toList()));
		ToolRegistry.reload(ConfigManager.get());
	}

	private static void fill(final GameTestHelper helper, final BlockPos centre, final int radius, final Block block) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					helper.setBlock(centre.offset(dx, dy, dz), block);
				}
			}
		}
	}

	/** Breaks the block the way the game does, so event ordering is real. */
	private static void mine(final GameTestHelper helper, final ServerPlayer player, final BlockPos relative) {
		player.gameMode.destroyBlock(helper.absolutePos(relative));
	}

	private static void forEachOffset(final int radius, final OffsetAction action) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				action.accept(dx, dz);
			}
		}
	}

	private static String normalise(final String text) {
		return text.replace("\r\n", "\n").stripTrailing();
	}

	@FunctionalInterface
	private interface OffsetAction {
		void accept(int dx, int dz);
	}

	/**
	 * Stands in for a claim or protection mod. Registered once; inert unless a
	 * test sets {@link #protectedPos}.
	 */
	public static final class Guard {
		static BlockPos protectedPos;

		private Guard() {
		}

		public static void install() {
			PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
					protectedPos == null || !protectedPos.equals(pos));
		}
	}

	static {
		Guard.install();
	}
}
