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

	/** Options a preset also sets (on top of switching features on): the alerts that suit that kind of play. */
	public static final Map<String, Map<String, Object>> TWEAKS = new LinkedHashMap<>();

	static {
		TWEAKS.put("Farming", Map.of("garden.visitorAlert.on", true, "garden.jacobAlert.on", true));
		TWEAKS.put("Mining", Map.of("mining.doneAlert.on", true));
		TWEAKS.put("Fishing", Map.of("fishing.trophy.on", true, "fishing.double.on", true));
		TWEAKS.put("Combat & Slayer", Map.of("slayer.slain.on", true, "statbars.lowAlert.on", true, "kuudra.suppliesAlert.on", true));
		TWEAKS.put("Dungeons", Map.of("dungeon.cryptsAlert.on", true));
		TWEAKS.put("Everything", Map.of("events.soon.on", true, "garden.visitorAlert.on", true, "fishing.trophy.on", true));
	}

	/** Applies a preset's option tweaks; returns how many were set (unknown keys are skipped). */
	@SuppressWarnings("unchecked")
	public static int applyTweaks(String preset, Settings settings) {
		int n = 0;
		for (Map.Entry<String, Object> e : TWEAKS.getOrDefault(preset, Map.of()).entrySet()) {
			Setting<?> s = settings.find(e.getKey());
			if (s == null || s.def == null || !s.def.getClass().isInstance(e.getValue())) continue;
			settings.set((Setting<Object>) s, e.getValue());
			n++;
		}
		return n;
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
