package io.github.tsiw.bulkmine;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * What a tool is good for, and what a whitelisted block needs to be broken.
 * A tool may hold several roles at once -- that is how paxels from other mods
 * end up mining stone and dirt in the same swing.
 */
public enum Role {
	PICKAXE,
	AXE,
	SHOVEL;

	public static final Set<Role> ALL = EnumSet.allOf(Role.class);

	public String serialized() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Null rather than an exception -- callers turn this into a user-facing message. */
	public static Role byName(String name) {
		for (Role role : values()) {
			if (role.serialized().equals(name.toLowerCase(Locale.ROOT))) {
				return role;
			}
		}
		return null;
	}
}
