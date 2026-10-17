package io.github.donttemptfate.fates.common.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;
import java.util.Map;

/**
 * The key an item is known by in the NEU item repository and in price lists ("internal name"),
 * computed from the item's SkyBlock data (its {@code minecraft:custom_data}, the old ExtraAttributes).
 *
 * <ul>
 *   <li>most items: their {@code id}, with ':' written as '-' ({@code INK_SACK:3} → {@code INK_SACK-3})</li>
 *   <li>pets: {@code TYPE;tier} ({@code ENDER_DRAGON;4} = legendary)</li>
 *   <li>enchanted books with one enchant: {@code NAME;level} ({@code ULTIMATE_WISE;5})</li>
 *   <li>runes: {@code NAME_RUNE;level}</li>
 * </ul>
 * Never derived from the display name.
 */
public final class ItemKey {
	private ItemKey() {}

	public static final String[] TIERS = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL"};

	/** NEU-style key, or null when the data has no SkyBlock id. */
	public static String of(JsonObject data) {
		if (data == null) return null;
		String id = str(data, "id");
		if (id == null || id.isEmpty()) return null;
		switch (id) {
			case "PET" -> {
				JsonObject pet = petInfo(data);
				if (pet != null) {
					String type = str(pet, "type");
					int tier = tierIndex(str(pet, "tier"));
					if (type != null && tier >= 0) return type + ";" + tier;
				}
			}
			case "ENCHANTED_BOOK" -> {
				JsonObject ench = obj(data, "enchantments");
				if (ench != null && ench.size() == 1) {
					Map.Entry<String, JsonElement> e = ench.entrySet().iterator().next();
					return e.getKey().toUpperCase(Locale.ROOT) + ";" + e.getValue().getAsInt();
				}
			}
			case "RUNE", "UNIQUE_RUNE" -> {
				JsonObject runes = obj(data, "runes");
				if (runes != null && runes.size() == 1) {
					Map.Entry<String, JsonElement> e = runes.entrySet().iterator().next();
					return e.getKey().toUpperCase(Locale.ROOT) + "_RUNE;" + e.getValue().getAsInt();
				}
			}
			default -> {
			}
		}
		return id.replace(':', '-');
	}

	/** The pet's data: Hypixel stores it as a JSON string inside the item data. */
	public static JsonObject petInfo(JsonObject data) {
		JsonElement e = data.get("petInfo");
		if (e == null) return null;
		try {
			if (e.isJsonObject()) return e.getAsJsonObject();
			if (e.isJsonPrimitive()) return JsonParser.parseString(e.getAsString()).getAsJsonObject();
		} catch (RuntimeException ignored) {
			// broken pet data: treated as no pet
		}
		return null;
	}

	public static int tierIndex(String tier) {
		if (tier == null) return -1;
		for (int i = 0; i < TIERS.length; i++) if (TIERS[i].equalsIgnoreCase(tier)) return i;
		return -1;
	}

	public static String str(JsonObject o, String k) {
		JsonElement e = o == null ? null : o.get(k);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	public static JsonObject obj(JsonObject o, String k) {
		JsonElement e = o == null ? null : o.get(k);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	public static long num(JsonObject o, String k, long def) {
		JsonElement e = o == null ? null : o.get(k);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsLong() : def;
		} catch (NumberFormatException ex) {
			return def;
		}
	}
}
