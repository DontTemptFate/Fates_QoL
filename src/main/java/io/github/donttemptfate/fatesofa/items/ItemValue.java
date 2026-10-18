package io.github.donttemptfate.fatesofa.items;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * An item's value with its upgrades: the clean item's price plus what was applied to it (enchant books,
 * stars, recombobulator, potato books, gemstones, reforge stone, scrolls, Art of War/Peace, drill parts,
 * dye/skin, boosters, pet item). Built from the item's own data (ExtraAttributes) and public prices only.
 * Each part is labelled, so the tooltip can show where the value comes from. Display only.
 */
public final class ItemValue {
	private ItemValue() {}

	/** Upgrade categories the player can count or leave out. */
	public static final Map<String, String> CATEGORIES = new LinkedHashMap<>();

	static {
		CATEGORIES.put("enchants", "Enchantments");
		CATEGORIES.put("stars", "Stars and master stars");
		CATEGORIES.put("recomb", "Recombobulator");
		CATEGORIES.put("potato", "Hot / Fuming potato books");
		CATEGORIES.put("gems", "Gemstones");
		CATEGORIES.put("reforge", "Reforge stone");
		CATEGORIES.put("scrolls", "Ability and power scrolls");
		CATEGORIES.put("war_peace", "Art of War / Peace");
		CATEGORIES.put("drill", "Drill parts and coatings");
		CATEGORIES.put("cosmetic", "Dyes and skins");
		CATEGORIES.put("boosters", "Other boosters (books, singularity, enrichment…)");
		CATEGORIES.put("pet_item", "Pet held item");
	}

	/** One part of the value: its category, a label ("Sharpness VI", "5 Hot Potato Books") and coins. */
	public record Part(String category, String label, double value) {
	}

	/** The clean item's price plus its parts. {@code base} is NaN when the clean item has no price. */
	public record Breakdown(String key, double base, List<Part> parts) {
		public double upgrades() {
			double t = 0;
			for (Part p : parts) t += p.value();
			return t;
		}

		public double total() {
			return (Double.isNaN(base) ? 0 : base) + upgrades();
		}

		/** Parts added up by category, biggest first. */
		public List<Part> byCategory() {
			Map<String, Double> m = new LinkedHashMap<>();
			for (Part p : parts) m.merge(p.category(), p.value(), Double::sum);
			List<Part> out = new ArrayList<>();
			for (var e : m.entrySet()) out.add(new Part(e.getKey(), CATEGORIES.getOrDefault(e.getKey(), e.getKey()), e.getValue()));
			out.sort((a, b) -> Double.compare(b.value(), a.value()));
			return out;
		}
	}

	/** Price of one item by NEU key; NaN when unknown. */
	public interface Prices {
		double of(String key);
	}

	/**
	 * Reference data from the NEU repository: essence needed per star (essencecosts.json) and which stone
	 * gives which reforge (reforgestones.json). Either may be empty: those parts are then left out.
	 */
	public record Refs(Map<String, Essence> essence, Map<String, String> stoneForReforge) {
		public static final Refs EMPTY = new Refs(Map.of(), Map.of());

		/** essencecosts.json: {"HYPERION": {"type": "Wither", "1": 150, "2": 300, ...}, ...}. */
		public static Map<String, Essence> essence(JsonObject json) {
			Map<String, Essence> out = new HashMap<>();
			if (json == null) return out;
			for (var e : json.entrySet()) {
				if (!e.getValue().isJsonObject()) continue;
				JsonObject o = e.getValue().getAsJsonObject();
				if (!o.has("type")) continue;
				Map<Integer, Integer> per = new HashMap<>();
				for (var c : o.entrySet()) {
					try {
						per.put(Integer.parseInt(c.getKey()), c.getValue().getAsInt());
					} catch (RuntimeException ignored) {
						// "type", "items" and other non-star keys
					}
				}
				out.put(e.getKey(), new Essence(o.get("type").getAsString(), Map.copyOf(per)));
			}
			return out;
		}

		/** reforgestones.json: {"WITHER_BLOOD": {"reforgeName": "Withered", ...}, ...} → "withered" → "WITHER_BLOOD". */
		public static Map<String, String> stones(JsonObject json) {
			Map<String, String> out = new HashMap<>();
			if (json == null) return out;
			for (var e : json.entrySet()) {
				if (!e.getValue().isJsonObject()) continue;
				JsonObject o = e.getValue().getAsJsonObject();
				if (o.has("reforgeName")) out.put(reforgeId(o.get("reforgeName").getAsString()), e.getKey());
			}
			return out;
		}
	}

	/** Essence of one type needed for each star level (1..). */
	public record Essence(String type, Map<Integer, Integer> perStar) {
	}

	static final String[] MASTER_STARS = {"FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR"};
	static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	/** Item-data keys the valuation reads (storage keeps only these, so stored items can be valued later). */
	public static final Set<String> KEYS = Set.of("id", "enchantments", "upgrade_level", "dungeon_item_level", "rarity_upgrades", "hot_potato_count",
			"gems", "modifier", "ability_scroll", "power_ability_scroll", "art_of_war_count", "artOfPeaceApplied", "drill_part_engine",
			"drill_part_fuel_tank", "drill_part_upgrade_module", "divan_powder_coating", "dye_item", "skin", "talisman_enrichment",
			"mana_disintegrator_count", "wood_singularity_count", "jalapeno_count", "farming_for_dummies_count", "tuned_transmission",
			"ethermerge", "polarvoid", "stats_book", "petInfo", "runes", "attributes");

	/** The part of an item's data the valuation needs (small enough to store with every stored item). */
	public static JsonObject trim(JsonObject data) {
		if (data == null) return null;
		JsonObject out = new JsonObject();
		for (String k : KEYS) if (data.has(k)) out.add(k, data.get(k).deepCopy());
		return out.size() <= 1 ? null : out;
	}

	/**
	 * Values an item. {@code base}: the clean item's price (the caller picks it: pets by level, books by
	 * enchant); {@code count}: stack size (upgrades count once per item, so only base × count for stacks).
	 * {@code counted}: the categories to include; {@code share}: fraction of upgrade prices to count (0–1).
	 */
	public static Breakdown of(JsonObject data, String key, double base, int count, Prices prices, Refs refs, Set<String> counted, double share) {
		List<Part> parts = new ArrayList<>();
		if (data == null) return new Breakdown(key, base * Math.max(1, count), List.of());
		String id = str(data, "id");
		boolean book = "ENCHANTED_BOOK".equals(id);
		// enchantments (a book's own enchant is its base price, not an upgrade)
		JsonObject ench = obj(data, "enchantments");
		if (ench != null && !book && counted.contains("enchants")) {
			for (var e : ench.entrySet()) {
				int lvl = asInt(e.getValue());
				String name = e.getKey().toUpperCase(Locale.ROOT);
				add(parts, "enchants", pretty(name) + " " + roman(lvl), prices.of(name + ";" + lvl), share);
			}
		}
		// stars: essence for the first five (or as many as the item's essence table lists), master stars after that
		int stars = Math.max(num(data, "upgrade_level"), num(data, "dungeon_item_level"));
		if (stars > 0 && counted.contains("stars")) {
			Essence es = refs.essence().get(id);
			int essence = 0;
			int master = 0;
			for (int i = 1; i <= stars; i++) {
				if (es != null && es.perStar().containsKey(i)) essence += es.perStar().get(i);
				else if (i > 5 && i <= 10) master++;
			}
			if (essence > 0) add(parts, "stars", essence + " " + es.type() + " essence", prices.of("ESSENCE_" + es.type().toUpperCase(Locale.ROOT)) * essence, share);
			for (int i = 0; i < master; i++) add(parts, "stars", pretty(MASTER_STARS[i]), prices.of(MASTER_STARS[i]), share);
		}
		if (num(data, "rarity_upgrades") > 0 && counted.contains("recomb") && !"PET".equals(id))
			add(parts, "recomb", "Recombobulator", prices.of("RECOMBOBULATOR_3000"), share);
		int potato = num(data, "hot_potato_count");
		if (potato > 0 && counted.contains("potato")) {
			int hot = Math.min(10, potato), fuming = Math.max(0, potato - 10);
			add(parts, "potato", hot + " Hot Potato Book" + (hot > 1 ? "s" : ""), prices.of("HOT_POTATO_BOOK") * hot, share);
			if (fuming > 0) add(parts, "potato", fuming + " Fuming Potato Book" + (fuming > 1 ? "s" : ""), prices.of("FUMING_POTATO_BOOK") * fuming, share);
		}
		JsonObject gems = obj(data, "gems");
		if (gems != null && counted.contains("gems")) {
			for (var e : gems.entrySet()) {
				String slot = e.getKey();
				if (slot.endsWith("_gem") || slot.equals("unlocked_slots")) continue;
				String quality = e.getValue().isJsonObject() ? str(e.getValue().getAsJsonObject(), "quality")
						: e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : null;
				if (quality == null) continue;
				String type = gems.has(slot + "_gem") ? gems.get(slot + "_gem").getAsString() : slot.replaceAll("_\\d+$", "");
				if (Set.of("COMBAT", "OFFENSIVE", "DEFENSIVE", "UNIVERSAL", "MINING", "CHISEL").contains(type)) continue; // type unknown
				String gemKey = quality.toUpperCase(Locale.ROOT) + "_" + type.toUpperCase(Locale.ROOT) + "_GEM";
				add(parts, "gems", pretty(quality) + " " + pretty(type), prices.of(gemKey), share);
			}
		}
		String modifier = str(data, "modifier");
		if (modifier != null && counted.contains("reforge")) {
			String stone = refs.stoneForReforge().get(reforgeId(modifier));
			if (stone != null) add(parts, "reforge", pretty(modifier) + " (" + pretty(stone) + ")", prices.of(stone), share);
		}
		if (counted.contains("scrolls")) {
			JsonArray scrolls = data.has("ability_scroll") && data.get("ability_scroll").isJsonArray() ? data.getAsJsonArray("ability_scroll") : null;
			if (scrolls != null) for (JsonElement s : scrolls) add(parts, "scrolls", pretty(s.getAsString()), prices.of(s.getAsString()), share);
			String power = str(data, "power_ability_scroll");
			if (power != null) add(parts, "scrolls", pretty(power), prices.of(power), share);
		}
		if (counted.contains("war_peace")) {
			if (num(data, "art_of_war_count") > 0) add(parts, "war_peace", "The Art of War", prices.of("THE_ART_OF_WAR"), share);
			if (num(data, "artOfPeaceApplied") > 0) add(parts, "war_peace", "The Art of Peace", prices.of("THE_ART_OF_PEACE"), share);
		}
		if (counted.contains("drill")) {
			for (String k : new String[] {"drill_part_engine", "drill_part_fuel_tank", "drill_part_upgrade_module"}) {
				String part = str(data, k);
				if (part != null) add(parts, "drill", pretty(part), prices.of(part.toUpperCase(Locale.ROOT)), share);
			}
			if (num(data, "divan_powder_coating") > 0) add(parts, "drill", "Divan's Powder Coating", prices.of("DIVAN_POWDER_COATING"), share);
		}
		if (counted.contains("cosmetic")) {
			String dye = str(data, "dye_item");
			if (dye != null) add(parts, "cosmetic", pretty(dye), prices.of(dye), share);
			String skin = str(data, "skin");
			if (skin != null && !"PET".equals(id)) add(parts, "cosmetic", pretty(skin), prices.of(skin), share);
		}
		if (counted.contains("boosters")) {
			counted(parts, data, "mana_disintegrator_count", "MANA_DISINTEGRATOR", "Mana Disintegrator", prices, share);
			counted(parts, data, "wood_singularity_count", "WOOD_SINGULARITY", "Wood Singularity", prices, share);
			counted(parts, data, "jalapeno_count", "JALAPENO_BOOK", "Jalapeno Book", prices, share);
			counted(parts, data, "farming_for_dummies_count", "FARMING_FOR_DUMMIES", "Farming for Dummies", prices, share);
			counted(parts, data, "tuned_transmission", "TRANSMISSION_TUNER", "Transmission Tuner", prices, share);
			counted(parts, data, "polarvoid", "POLARVOID_BOOK", "Polarvoid Book", prices, share);
			if (num(data, "ethermerge") > 0) add(parts, "boosters", "Etherwarp Conduit", prices.of("ETHERWARP_CONDUIT"), share);
			if (data.has("stats_book")) add(parts, "boosters", "Book of Stats", prices.of("BOOK_OF_STATS"), share);
			String enrich = str(data, "talisman_enrichment");
			if (enrich != null) add(parts, "boosters", "Enrichment: " + pretty(enrich), prices.of("TALISMAN_ENRICHMENT_" + enrich.toUpperCase(Locale.ROOT)), share);
		}
		if ("PET".equals(id)) {
			JsonObject pet = petInfo(data);
			if (pet != null) {
				String held = str(pet, "heldItem");
				if (held != null && counted.contains("pet_item")) add(parts, "pet_item", pretty(held), prices.of(held), share);
				String skin = str(pet, "skin");
				if (skin != null && counted.contains("cosmetic")) add(parts, "cosmetic", "Skin: " + pretty(skin), prices.of("PET_SKIN_" + skin), share);
			}
		}
		parts.sort((a, b) -> Double.compare(b.value(), a.value()));
		return new Breakdown(key, Double.isNaN(base) ? Double.NaN : base * Math.max(1, count), Collections.unmodifiableList(parts));
	}

	static void counted(List<Part> parts, JsonObject data, String field, String key, String label, Prices prices, double share) {
		int n = num(data, field);
		if (n > 0) add(parts, "boosters", (n > 1 ? n + " " : "") + label + (n > 1 ? "s" : ""), prices.of(key) * n, share);
	}

	/** Adds a part if its price is known and positive. */
	static void add(List<Part> parts, String cat, String label, double value, double share) {
		if (Double.isNaN(value) || value <= 0) return;
		parts.add(new Part(cat, label, value * share));
	}

	static String reforgeId(String s) {
		return s.toLowerCase(Locale.ROOT).replace("'", "").replace(' ', '_');
	}

	public static JsonObject petInfo(JsonObject data) {
		JsonElement e = data.get("petInfo");
		if (e == null) return null;
		try {
			if (e.isJsonObject()) return e.getAsJsonObject();
			return com.google.gson.JsonParser.parseString(e.getAsString()).getAsJsonObject();
		} catch (RuntimeException ex) {
			return null;
		}
	}

	/** "ULTIMATE_WISE" → "Ultimate Wise". */
	public static String pretty(String id) {
		String[] w = id.toLowerCase(Locale.ROOT).split("[_ ]+");
		StringBuilder b = new StringBuilder();
		for (String s : w) {
			if (s.isEmpty()) continue;
			if (b.length() > 0) b.append(' ');
			b.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
		}
		return b.toString();
	}

	static String roman(int n) {
		return n >= 0 && n < ROMAN.length ? ROMAN[n] : Integer.toString(n);
	}

	public static String str(JsonObject o, String k) {
		JsonElement e = o == null ? null : o.get(k);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	static JsonObject obj(JsonObject o, String k) {
		JsonElement e = o == null ? null : o.get(k);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	static int num(JsonObject o, String k) {
		JsonElement e = o == null ? null : o.get(k);
		if (e == null || !e.isJsonPrimitive()) return 0;
		try {
			return e.getAsJsonPrimitive().isBoolean() ? (e.getAsBoolean() ? 1 : 0) : e.getAsInt();
		} catch (RuntimeException ex) {
			return 0;
		}
	}

	static int asInt(JsonElement e) {
		try {
			return e.getAsInt();
		} catch (RuntimeException ex) {
			return 0;
		}
	}
}
