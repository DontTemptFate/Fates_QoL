package io.github.donttemptfate.fatesofa.config;

import io.github.donttemptfate.fatesofa.core.Category;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * First-launch setup: the player picks what they mostly do, and the matching categories' features
 * are switched on with their defaults. Later changes are always the player's own; a preset is only
 * applied when the player picks it.
 */
public final class Presets {
	private Presets() {}

	/** Always part of every preset. */
	public static final Set<Category> BASE = EnumSet.of(Category.GENERAL, Category.HUD, Category.ITEMS, Category.STORAGE,
			Category.PROTECTION, Category.CHAT, Category.PLANNER, Category.PERFORMANCE);

	public static final Map<String, Set<Category>> PRESETS = new LinkedHashMap<>();

	static {
		PRESETS.put("Minimal", EnumSet.of(Category.GENERAL, Category.HUD, Category.PROTECTION, Category.PERFORMANCE));
		PRESETS.put("General", EnumSet.noneOf(Category.class));
		PRESETS.put("Farming", EnumSet.of(Category.FARMING));
		PRESETS.put("Mining", EnumSet.of(Category.MINING));
		PRESETS.put("Fishing", EnumSet.of(Category.FISHING));
		PRESETS.put("Combat & Slayer", EnumSet.of(Category.COMBAT, Category.SLAYER, Category.KUUDRA));
		PRESETS.put("Dungeons", EnumSet.of(Category.DUNGEONS));
		PRESETS.put("Everything", EnumSet.allOf(Category.class));
	}

	/** Categories a preset switches on. */
	public static Set<Category> categories(String preset) {
		Set<Category> extra = PRESETS.get(preset);
		if (extra == null) return BASE;
		if (preset.equals("Minimal")) return extra;
		Set<Category> all = EnumSet.copyOf(BASE);
		all.addAll(extra);
		return all;
	}
}
