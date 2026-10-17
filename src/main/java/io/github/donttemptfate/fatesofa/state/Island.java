package io.github.donttemptfate.fatesofa.state;

import io.github.donttemptfate.fates.common.text.Strip;

import java.util.Locale;

/**
 * SkyBlock islands. Known by the Hypixel Mod API's {@code mode} and by the tab list's "Area:" name;
 * new islands Hypixel adds show up as {@link #OTHER} with their name kept, so nothing breaks.
 */
public enum Island {
	PRIVATE_ISLAND("Private Island", "dynamic"),
	HUB("Hub", "hub"),
	FARMING_ISLANDS("The Farming Islands", "farming_1", "The Barn", "Mushroom Desert"),
	PARK("The Park", "foraging_1"),
	GOLD_MINE("Gold Mine", "mining_1"),
	DEEP_CAVERNS("Deep Caverns", "mining_2"),
	DWARVEN_MINES("Dwarven Mines", "mining_3"),
	CRYSTAL_HOLLOWS("Crystal Hollows", "crystal_hollows"),
	MINESHAFT("Mineshaft", "mineshaft", "Glacite Mineshafts"),
	SPIDERS_DEN("Spider's Den", "combat_1"),
	THE_END("The End", "combat_3"),
	CRIMSON_ISLE("Crimson Isle", "crimson_isle"),
	KUUDRA("Kuudra", "kuudra", "Kuudra's Hollow"),
	GARDEN("Garden", "garden", "The Garden"),
	DUNGEON_HUB("Dungeon Hub", "dungeon_hub"),
	DUNGEON("Catacombs", "dungeon", "The Catacombs"),
	RIFT("The Rift", "rift"),
	JERRYS_WORKSHOP("Jerry's Workshop", "winter"),
	DARK_AUCTION("Dark Auction", "dark_auction"),
	GALATEA("Galatea", "foraging_2"),
	BACKWATER_BAYOU("Backwater Bayou", "fishing_1"),
	LOTUS_ATOLL("Lotus Atoll", "fishing_2"),
	TORRHUS_CANYON("Torrhus Canyon", "foraging_3"),
	OTHER("Other", "");

	public final String display;
	final String mode;
	final String[] aliases;

	Island(String display, String mode, String... aliases) {
		this.display = display;
		this.mode = mode;
		this.aliases = aliases;
	}

	/** From the Hypixel Mod API location packet's mode ("hub", "mining_3", ...). */
	public static Island fromMode(String mode) {
		if (mode == null || mode.isEmpty()) return null;
		String m = mode.toLowerCase(Locale.ROOT);
		for (Island i : values()) if (!i.mode.isEmpty() && i.mode.equals(m)) return i;
		return OTHER;
	}

	/** From the tab list ("Area: Dwarven Mines", "Dungeon: Catacombs") or any island name. */
	public static Island fromName(String name) {
		if (name == null || name.isBlank()) return null;
		String k = Strip.key(name);
		for (Island i : values()) {
			if (i == OTHER) continue;
			if (Strip.key(i.display).equals(k)) return i;
			for (String a : i.aliases) if (Strip.key(a).equals(k)) return i;
		}
		return OTHER;
	}

	public boolean isMining() {
		return this == GOLD_MINE || this == DEEP_CAVERNS || this == DWARVEN_MINES || this == CRYSTAL_HOLLOWS || this == MINESHAFT;
	}

	public boolean isGalatea() {
		return this == GALATEA || this == TORRHUS_CANYON;
	}
}
