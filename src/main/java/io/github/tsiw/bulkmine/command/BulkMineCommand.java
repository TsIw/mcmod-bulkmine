package io.github.tsiw.bulkmine.command;

import io.github.tsiw.bulkmine.Role;
import io.github.tsiw.bulkmine.config.BulkMineConfig;
import io.github.tsiw.bulkmine.config.ConfigManager;
import io.github.tsiw.bulkmine.registry.ToolRegistry;
import io.github.tsiw.bulkmine.state.PlayerSettings;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * {@code /bulkmine} -- per-player settings anyone can use, plus whitelist
 * management for whoever runs the server, so nobody has to hunt for another
 * mod's item ids by hand.
 */
public final class BulkMineCommand {
	private BulkMineCommand() {
	}

	private static final DynamicCommandExceptionType BAD_ROLE =
			new DynamicCommandExceptionType(name -> Component.translatable("bulkmine.command.error.bad_role", name));
	private static final SimpleCommandExceptionType EMPTY_HAND =
			new SimpleCommandExceptionType(Component.translatable("bulkmine.command.error.empty_hand"));
	private static final DynamicCommandExceptionType NOT_A_BLOCK =
			new DynamicCommandExceptionType(name -> Component.translatable("bulkmine.command.error.not_a_block", name));
	private static final DynamicCommandExceptionType BAD_SIZE =
			new DynamicCommandExceptionType(max -> Component.translatable("bulkmine.command.error.bad_size", max));

	/** Offers {@code all}, single roles, and continuations after a separator. */
	private static final SuggestionProvider<CommandSourceStack> ROLE_SUGGESTIONS = (context, builder) -> {
		String remaining = builder.getRemaining();
		int separator = Math.max(remaining.lastIndexOf('|'), remaining.lastIndexOf(','));
		String prefix = separator < 0 ? "" : remaining.substring(0, separator + 1);
		String partial = remaining.substring(separator + 1).toLowerCase(Locale.ROOT);

		if (prefix.isEmpty() && "all".startsWith(partial)) {
			builder.suggest("all");
		}
		for (Role role : Role.values()) {
			if (role.serialized().startsWith(partial)) {
				builder.suggest(prefix + role.serialized());
			}
		}
		return builder.buildFuture();
	};

	public static void register(final CommandDispatcher<CommandSourceStack> dispatcher, final CommandBuildContext build) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("bulkmine");

		// Per-player, so no permission gate: this is the player's own setup.
		// There is no "toggle" here on purpose -- the key does the same job and
		// more, since it picks the size as well.
		root.then(Commands.literal("config")
				.executes(BulkMineCommand::showConfig)
				.then(Commands.literal("size")
						.then(Commands.argument("size", IntegerArgumentType.integer(1, 15))
								.executes(BulkMineCommand::setSize))));

		root.then(Commands.literal("tool")
				.requires(BulkMineCommand::canManage)
				.then(Commands.literal("add")
						.then(Commands.literal("hand")
								.then(rolesArgument().executes(context -> addTool(
										context, heldItem(context).getItem(), roles(context)))))
						.then(Commands.argument("item", ResourceArgument.resource(build, Registries.ITEM))
								.then(rolesArgument().executes(context -> addTool(
										context, itemArgument(context), roles(context))))))
				.then(Commands.literal("remove")
						.then(Commands.literal("hand")
								.executes(context -> removeTool(context, heldItem(context).getItem(), Role.ALL)))
						.then(Commands.argument("item", ResourceArgument.resource(build, Registries.ITEM))
								.executes(context -> removeTool(context, itemArgument(context), Role.ALL))
								.then(rolesArgument().executes(context -> removeTool(
										context, itemArgument(context), roles(context))))))
				.then(Commands.literal("list").executes(BulkMineCommand::listTools)));

		root.then(Commands.literal("block")
				.requires(BulkMineCommand::canManage)
				.then(Commands.literal("add")
						.then(Commands.literal("hand")
								.then(rolesArgument().executes(context -> addBlock(
										context, heldBlock(context), roles(context)))))
						.then(Commands.argument("block", ResourceArgument.resource(build, Registries.BLOCK))
								.then(rolesArgument().executes(context -> addBlock(
										context, blockArgument(context), roles(context))))))
				.then(Commands.literal("remove")
						.then(Commands.literal("hand")
								.executes(context -> removeBlock(context, heldBlock(context))))
						.then(Commands.argument("block", ResourceArgument.resource(build, Registries.BLOCK))
								.executes(context -> removeBlock(context, blockArgument(context)))))
				.then(Commands.literal("deny")
						.then(Commands.literal("hand")
								.executes(context -> denyBlock(context, heldBlock(context), true)))
						.then(Commands.argument("block", ResourceArgument.resource(build, Registries.BLOCK))
								.executes(context -> denyBlock(context, blockArgument(context), true))))
				.then(Commands.literal("allow")
						.then(Commands.literal("hand")
								.executes(context -> denyBlock(context, heldBlock(context), false)))
						.then(Commands.argument("block", ResourceArgument.resource(build, Registries.BLOCK))
								.executes(context -> denyBlock(context, blockArgument(context), false))))
				.then(Commands.literal("list").executes(BulkMineCommand::listBlocks)));

		root.then(Commands.literal("reload")
				.requires(BulkMineCommand::canManage)
				.executes(BulkMineCommand::reload));

		dispatcher.register(root);
	}

	/**
	 * Single players are their own operator, so gating on a permission level
	 * would lock these commands away in any world without cheats enabled.
	 */
	private static boolean canManage(final CommandSourceStack source) {
		MinecraftServer server = source.getServer();
		if (server != null && server.isSingleplayer()) {
			return true;
		}
		return Commands.LEVEL_GAMEMASTERS.check(source.permissions());
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> rolesArgument() {
		return Commands.argument("roles", StringArgumentType.word()).suggests(ROLE_SUGGESTIONS);
	}

	// ------------------------------------------------------ per-player state

	private static int setSize(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		int size = IntegerArgumentType.getInteger(context, "size");
		int max = ConfigManager.get().server.maxCubeSize;

		if (size % 2 == 0 || size > max) {
			throw BAD_SIZE.create(max);
		}

		PlayerSettings.setCubeSize(player, size);
		context.getSource().sendSuccess(() -> Component.translatable("bulkmine.command.size.set", size), false);
		return size;
	}

	/** Your own settings first, then the server values that constrain them. */
	private static int showConfig(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		BulkMineConfig.Server server = ConfigManager.get().server;
		CommandSourceStack source = context.getSource();

		source.sendSuccess(() -> Component.translatable("bulkmine.command.config.header"), false);
		line(source, "bulk mining", PlayerSettings.isEnabled(player) ? "on" : "off");
		line(source, "cube size", String.valueOf(PlayerSettings.cubeSize(player)));

		source.sendSuccess(() -> Component.translatable("bulkmine.command.config.server"), false);
		line(source, "enabled", String.valueOf(server.enabled));
		line(source, "defaultCubeSize", String.valueOf(server.defaultCubeSize));
		line(source, "toggleSizes", server.toggleSizes.toString());
		line(source, "maxCubeSize", String.valueOf(server.maxCubeSize));
		line(source, "breakLeaves", server.breakLeaves + " (distance " + server.leafDistance + ")");
		line(source, "maxLogs / maxLeaves", server.maxLogs + " / " + server.maxLeaves);
		line(source, "treeRadius", String.valueOf(server.treeRadius));
		line(source, "aggregateDrops", String.valueOf(server.aggregateDrops));
		line(source, "allowWhileSneaking", String.valueOf(server.allowWhileSneaking));
		line(source, "requireCorrectTool", String.valueOf(server.requireCorrectTool));
		line(source, "durabilityChance", server.durabilityChance + "%");
		line(source, "durabilityWarningSound",
				server.durabilityWarningSound.isBlank() ? "(none)" : server.durabilityWarningSound);
		return 1;
	}

	private static void line(final CommandSourceStack source, final String label, final String value) {
		source.sendSuccess(() -> Component.literal("  " + label + ": " + value), false);
	}

	// ---------------------------------------------------------------- tools

	private static int addTool(final CommandContext<CommandSourceStack> context, final Item item, final Set<Role> roles) {
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		BulkMineConfig config = ConfigManager.get();

		Set<Role> merged = EnumSet.copyOf(roles);
		config.server.tools.stream()
				.filter(entry -> id.equals(entry.item))
				.findFirst()
				.ifPresent(entry -> entry.roles.stream().map(Role::byName)
						.filter(java.util.Objects::nonNull).forEach(merged::add));
		config.server.tools.removeIf(entry -> id.equals(entry.item));
		config.server.tools.add(new BulkMineConfig.ToolEntry(id, serialise(merged)));

		persist();
		context.getSource().sendSuccess(
				() -> Component.translatable("bulkmine.command.tool.added", id, describe(merged)), true);
		return 1;
	}

	private static int removeTool(final CommandContext<CommandSourceStack> context, final Item item, final Set<Role> roles) {
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		BulkMineConfig config = ConfigManager.get();

		BulkMineConfig.ToolEntry existing = config.server.tools.stream()
				.filter(entry -> id.equals(entry.item))
				.findFirst()
				.orElse(null);
		if (existing == null) {
			context.getSource().sendFailure(Component.translatable("bulkmine.command.tool.not_registered", id));
			return 0;
		}

		existing.roles.removeIf(name -> {
			Role role = Role.byName(name);
			return role != null && roles.contains(role);
		});
		if (existing.roles.isEmpty()) {
			config.server.tools.remove(existing);
		}

		persist();
		context.getSource().sendSuccess(() -> Component.translatable("bulkmine.command.tool.removed", id), true);
		return 1;
	}

	private static int listTools(final CommandContext<CommandSourceStack> context) {
		Map<Item, Set<Role>> tools = ToolRegistry.configuredTools();
		if (tools.isEmpty()) {
			context.getSource().sendSuccess(() -> Component.translatable("bulkmine.command.tool.list.empty"), false);
			return 0;
		}

		context.getSource().sendSuccess(
				() -> Component.translatable("bulkmine.command.tool.list.header", tools.size()), false);
		tools.forEach((item, roles) -> line(context.getSource(),
				BuiltInRegistries.ITEM.getKey(item).toString(), describe(roles)));
		return tools.size();
	}

	// --------------------------------------------------------------- blocks

	private static int addBlock(final CommandContext<CommandSourceStack> context, final Block block, final Set<Role> roles) {
		String id = BuiltInRegistries.BLOCK.getKey(block).toString();
		BulkMineConfig.Blocks blocks = ConfigManager.get().server.blocks;

		blocks.blacklist.remove(id);
		for (Role role : Role.values()) {
			List<String> list = listFor(blocks, role);
			if (roles.contains(role)) {
				if (!list.contains(id)) {
					list.add(id);
				}
			} else {
				list.remove(id);
			}
		}

		persist();
		context.getSource().sendSuccess(
				() -> Component.translatable("bulkmine.command.block.added", id, describe(roles)), true);
		return 1;
	}

	private static int removeBlock(final CommandContext<CommandSourceStack> context, final Block block) {
		String id = BuiltInRegistries.BLOCK.getKey(block).toString();
		BulkMineConfig.Blocks blocks = ConfigManager.get().server.blocks;

		boolean found = false;
		for (Role role : Role.values()) {
			found |= listFor(blocks, role).remove(id);
		}
		found |= blocks.leaves.remove(id);

		if (!found) {
			context.getSource().sendFailure(Component.translatable("bulkmine.command.block.not_listed", id));
			return 0;
		}

		persist();
		context.getSource().sendSuccess(() -> Component.translatable("bulkmine.command.block.removed", id), true);
		return 1;
	}

	private static int denyBlock(final CommandContext<CommandSourceStack> context, final Block block, final boolean deny) {
		String id = BuiltInRegistries.BLOCK.getKey(block).toString();
		List<String> blacklist = ConfigManager.get().server.blocks.blacklist;

		if (deny) {
			if (!blacklist.contains(id)) {
				blacklist.add(id);
			}
		} else {
			blacklist.remove(id);
		}

		persist();
		context.getSource().sendSuccess(() -> Component.translatable(
				deny ? "bulkmine.command.block.denied" : "bulkmine.command.block.allowed", id), true);
		return 1;
	}

	private static int listBlocks(final CommandContext<CommandSourceStack> context) {
		BulkMineConfig.Blocks blocks = ConfigManager.get().server.blocks;
		CommandSourceStack source = context.getSource();

		source.sendSuccess(() -> Component.translatable("bulkmine.command.block.list.header"), false);
		line(source, "pickaxe", blocks.pickaxe.size() + " entries");
		line(source, "shovel", blocks.shovel.size() + " entries");
		line(source, "fellable", blocks.fellable.size() + " entries");
		line(source, "leaves", blocks.leaves.size() + " entries");
		line(source, "blacklist", blocks.blacklist.isEmpty()
				? "(empty)" : String.join(", ", blocks.blacklist));
		source.sendSuccess(() -> Component.translatable("bulkmine.command.block.list.hint"), false);
		return 1;
	}

	/** Leaves are not a {@link Role}; only the three mining lists map to one. */
	private static List<String> listFor(final BulkMineConfig.Blocks blocks, final Role role) {
		return switch (role) {
			case PICKAXE -> blocks.pickaxe;
			case SHOVEL -> blocks.shovel;
			case AXE -> blocks.fellable;
		};
	}

	// ---------------------------------------------------------------- misc

	private static int reload(final CommandContext<CommandSourceStack> context) {
		ConfigManager.load();
		ToolRegistry.reload(ConfigManager.get());
		io.github.tsiw.bulkmine.registry.BlockWhitelist.reload(ConfigManager.get());
		context.getSource().sendSuccess(() -> Component.translatable("bulkmine.command.reloaded"), true);
		return 1;
	}

	private static void persist() {
		ConfigManager.save();
		ToolRegistry.reload(ConfigManager.get());
		io.github.tsiw.bulkmine.registry.BlockWhitelist.reload(ConfigManager.get());
	}

	// ------------------------------------------------------------- plumbing

	private static ItemStack heldItem(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ItemStack held = context.getSource().getPlayerOrException().getMainHandItem();
		if (held.isEmpty()) {
			throw EMPTY_HAND.create();
		}
		return held;
	}

	private static Block heldBlock(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ItemStack held = heldItem(context);
		if (!(held.getItem() instanceof BlockItem blockItem)) {
			throw NOT_A_BLOCK.create(BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
		}
		return blockItem.getBlock();
	}

	private static Item itemArgument(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Holder.Reference<Item> holder = ResourceArgument.getResource(context, "item", Registries.ITEM);
		return holder.value();
	}

	private static Block blockArgument(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Holder.Reference<Block> holder = ResourceArgument.getResource(context, "block", Registries.BLOCK);
		return holder.value();
	}

	/** Accepts {@code all}, a single role, or roles joined by {@code |} or {@code ,}. */
	private static Set<Role> roles(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String raw = StringArgumentType.getString(context, "roles");
		EnumSet<Role> parsed = EnumSet.noneOf(Role.class);

		for (String token : raw.split("[|,]")) {
			String trimmed = token.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.equalsIgnoreCase("all")) {
				parsed.addAll(Role.ALL);
				continue;
			}

			Role role = Role.byName(trimmed);
			if (role == null) {
				throw BAD_ROLE.create(trimmed);
			}
			parsed.add(role);
		}

		if (parsed.isEmpty()) {
			throw BAD_ROLE.create(raw);
		}
		return parsed;
	}

	private static List<String> serialise(final Set<Role> roles) {
		List<String> names = new ArrayList<>();
		for (Role role : Role.values()) {
			if (roles.contains(role)) {
				names.add(role.serialized());
			}
		}
		return names;
	}

	private static String describe(final Set<Role> roles) {
		return roles.size() == Role.ALL.size()
				? "all"
				: serialise(roles).stream().collect(Collectors.joining("|"));
	}
}
