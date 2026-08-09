package io.github.tsiw.bulkmine.registry;

import io.github.tsiw.bulkmine.BulkMine;
import io.github.tsiw.bulkmine.Role;
import io.github.tsiw.bulkmine.config.BulkMineConfig;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Which tools count as which {@link Role}.
 *
 * <p>Vanilla pickaxes, axes and shovels are always recognised through their
 * item tags. Anything else -- a paxel from another mod, say -- is opt-in via
 * the config or {@code /bulkmine tool add}, which keeps this mod from
 * depending on any particular tool mod being installed.
 */
public final class ToolRegistry {
	private ToolRegistry() {
	}

	private static volatile Map<Item, Set<Role>> configured = Map.of();

	/** Resolves the config's item ids; unknown ids are warned about and skipped. */
	public static void reload(final BulkMineConfig config) {
		Map<Item, Set<Role>> resolved = new HashMap<>();

		for (BulkMineConfig.ToolEntry entry : config.server.tools) {
			Identifier id = Identifier.tryParse(entry.item);
			if (id == null) {
				BulkMine.LOGGER.warn("Ignoring tool with malformed id \"{}\"", entry.item);
				continue;
			}

			Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
			if (item == null) {
				BulkMine.LOGGER.warn("Ignoring unknown tool \"{}\" -- is the mod that adds it installed?", entry.item);
				continue;
			}

			Set<Role> roles = resolved.computeIfAbsent(item, key -> EnumSet.noneOf(Role.class));
			for (String name : entry.roles) {
				Role role = Role.byName(name);
				if (role == null) {
					BulkMine.LOGGER.warn("Ignoring unknown role \"{}\" on tool \"{}\"", name, entry.item);
				} else {
					roles.add(role);
				}
			}
		}

		resolved.values().removeIf(Set::isEmpty);
		configured = resolved;
	}

	/** Every role this stack can act as. Empty means bulk breaking stays off. */
	public static Set<Role> rolesOf(final ItemStack stack) {
		EnumSet<Role> roles = EnumSet.noneOf(Role.class);
		if (stack.isEmpty()) {
			return roles;
		}

		if (stack.is(ItemTags.PICKAXES)) {
			roles.add(Role.PICKAXE);
		}
		if (stack.is(ItemTags.AXES)) {
			roles.add(Role.AXE);
		}
		if (stack.is(ItemTags.SHOVELS)) {
			roles.add(Role.SHOVEL);
		}

		Set<Role> extra = configured.get(stack.getItem());
		if (extra != null) {
			roles.addAll(extra);
		}
		return roles;
	}

	/** Config-registered tools only, for {@code /bulkmine tool list}. */
	public static Map<Item, Set<Role>> configuredTools() {
		return new LinkedHashMap<>(configured);
	}
}
