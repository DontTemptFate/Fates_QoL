package io.github.donttemptfate.fatesofa.planner;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.donttemptfate.fatescommon.skyblock.Rarity;
import io.github.donttemptfate.fatesofa.data.ItemRepo;
import io.github.donttemptfate.fatesofa.data.RepoItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Accessory helper: which accessories you don't have yet (counting owned upgrades), what each would add
 * in Magical Power, and the coins per MP — cheapest first. Tiers from the NEU repo's talisman upgrade list.
 */
public final class Accessories {
	public record Suggestion(String key, String name, Rarity rarity, int mpGain, double price, boolean craftable) {
		public double coinsPerMp() {
			return mpGain <= 0 || Double.isNaN(price) ? Double.MAX_VALUE : price / mpGain;
		}
	}

	public static int mp(Rarity r) {
		if (r == null) return 0;
		return switch (r) {
			case COMMON, SPECIAL -> 3;
			case UNCOMMON, VERY_SPECIAL -> 5;
			case RARE -> 8;
			case EPIC -> 12;
			case LEGENDARY -> 16;
			case MYTHIC, DIVINE -> 22;
			default -> 0;
		};
	}

	/** The accessory's rarity, from the repo item's last rarity line, or null when it isn't an accessory. */
	public static Rarity accessoryRarity(RepoItem it) {
		for (int i = it.lore().size() - 1; i >= 0 && i >= it.lore().size() - 2; i--) {
			String l = it.lore().get(i);
			if (!l.contains("ACCESSORY") && !l.contains("HATCESSORY")) continue;
			return Rarity.fromLine(l);
		}
		return null;
	}

	/** Upgrade chains: item → items it can become (NEU misc.json talisman_upgrades). */
	public static Map<String, List<String>> upgrades(JsonObject misc) {
		Map<String, List<String>> out = new HashMap<>();
		if (misc == null || !misc.has("talisman_upgrades")) return out;
		for (Map.Entry<String, JsonElement> e : misc.getAsJsonObject("talisman_upgrades").entrySet()) {
			List<String> up = new ArrayList<>();
			for (JsonElement x : e.getValue().getAsJsonArray()) up.add(x.getAsString());
			out.put(e.getKey(), up);
		}
		return out;
	}

	public static Set<String> ignored(JsonObject misc) {
		Set<String> out = new HashSet<>();
		if (misc != null && misc.has("ignored_talisman")) for (JsonElement x : misc.getAsJsonArray("ignored_talisman")) out.add(x.getAsString());
		return out;
	}

	public static List<Suggestion> missing(ItemRepo repo, Set<String> owned, Map<String, List<String>> upgrades, Set<String> ignored,
			ToDoubleFunction<String> price, boolean tradeable) {
		Map<String, Rarity> all = new HashMap<>();
		for (RepoItem it : repo.all()) {
			Rarity r = accessoryRarity(it);
			if (r != null && !ignored.contains(it.key())) all.put(it.key(), r);
		}
		// lower tiers of each item (reverse of the upgrade list)
		Map<String, List<String>> lower = new HashMap<>();
		upgrades.forEach((base, ups) -> ups.forEach(u -> lower.computeIfAbsent(u, k -> new ArrayList<>()).add(base)));
		List<Suggestion> out = new ArrayList<>();
		for (Map.Entry<String, Rarity> e : all.entrySet()) {
			String key = e.getKey();
			if (owned.contains(key)) continue;
			boolean ownsBetter = false;
			for (String up : upgrades.getOrDefault(key, List.of())) if (owned.contains(up)) ownsBetter = true;
			if (ownsBetter) continue;
			int have = 0;
			for (String lo : lower.getOrDefault(key, List.of())) if (owned.contains(lo)) have = Math.max(have, mp(all.get(lo)));
			int gain = mp(e.getValue()) - have;
			if (gain <= 0) continue;
			RepoItem it = repo.get(key);
			boolean craftable = it != null && !it.recipes().isEmpty();
			double p = tradeable ? price.applyAsDouble(key) : Double.NaN;
			if (!tradeable && !craftable) continue;
			out.add(new Suggestion(key, it == null ? key : it.plainName(), e.getValue(), gain, p, craftable));
		}
		out.sort(Comparator.comparingDouble(Suggestion::coinsPerMp).thenComparing(Suggestion::name));
		return out;
	}
}
