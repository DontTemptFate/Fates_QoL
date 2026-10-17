package io.github.donttemptfate.fatesofa.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One way to get an item: crafting (3×3), forge, NPC shop, trade, Kat upgrade or mob drop. Inputs
 * are summed per item; {@code slots} keeps the 3×3 layout for crafting so it can be drawn.
 */
public record Recipe(Type type, String output, double outputCount, List<Ingredient> inputs, String[] slots, long durationS, String note) {
	public enum Type { CRAFTING, FORGE, NPC_SHOP, TRADE, KAT, DROPS, OTHER }

	static final String[] GRID = {"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"};

	/** All recipes of one repo item JSON. */
	public static List<Recipe> fromItem(String internalName, JsonObject item) {
		List<Recipe> out = new ArrayList<>();
		if (item.has("recipe") && item.get("recipe").isJsonObject()) {
			Recipe r = crafting(internalName, item.getAsJsonObject("recipe"));
			if (r != null) out.add(r);
		}
		if (item.has("recipes") && item.get("recipes").isJsonArray()) {
			for (JsonElement e : item.getAsJsonArray("recipes")) {
				if (!e.isJsonObject()) continue;
				try {
					Recipe r = one(internalName, e.getAsJsonObject());
					if (r != null) out.add(r);
				} catch (RuntimeException ignored) {
					// one odd recipe never hides the others
				}
			}
		}
		return out;
	}

	static Recipe one(String internalName, JsonObject o) {
		String type = str(o, "type", "crafting");
		String outId = str(o, "overrideOutputId", internalName);
		return switch (type) {
			case "crafting" -> crafting(outId, o);
			case "forge" -> new Recipe(Type.FORGE, outId, num(o, "count", 1), list(o.get("inputs")), null, (long) num(o, "duration", 0), null);
			case "npc_shop" -> {
				Ingredient res = Ingredient.parse(str(o, "result", outId + ":1"));
				yield new Recipe(Type.NPC_SHOP, res.key(), res.count(), list(o.get("cost")), null, 0, str(o, "npc", null));
			}
			case "trade" -> {
				Ingredient res = Ingredient.parse(str(o, "result", outId + ":1"));
				yield new Recipe(Type.TRADE, res.key(), res.count(), list(o.get("cost")), null, 0, null);
			}
			case "katgrade" -> {
				List<Ingredient> in = new ArrayList<>();
				Ingredient pet = Ingredient.parse(str(o, "input", null));
				if (pet != null) in.add(pet);
				in.addAll(list(o.get("items")));
				double coins = num(o, "coins", 0);
				if (coins > 0) in.add(new Ingredient(Ingredient.COINS, coins));
				Ingredient res = Ingredient.parse(str(o, "output", outId + ":1"));
				yield new Recipe(Type.KAT, res.key(), res.count(), merge(in), null, (long) num(o, "time", 0), null);
			}
			case "drops" -> new Recipe(Type.DROPS, outId, 1, List.of(), null, 0, str(o, "name", null));
			default -> null;
		};
	}

	static Recipe crafting(String outId, JsonObject o) {
		String[] slots = new String[9];
		List<Ingredient> in = new ArrayList<>();
		boolean any = false;
		for (int i = 0; i < 9; i++) {
			String s = str(o, GRID[i], "");
			slots[i] = s.isEmpty() ? null : s;
			Ingredient ing = Ingredient.parse(s);
			if (ing != null) {
				in.add(ing);
				any = true;
			}
		}
		if (!any) return null;
		return new Recipe(Type.CRAFTING, outId, num(o, "count", 1), merge(in), slots, 0, null);
	}

	static List<Ingredient> list(JsonElement e) {
		List<Ingredient> out = new ArrayList<>();
		if (e != null && e.isJsonArray()) {
			for (JsonElement x : (JsonArray) e) {
				if (x.isJsonPrimitive()) {
					Ingredient i = Ingredient.parse(x.getAsString());
					if (i != null) out.add(i);
				}
			}
		}
		return merge(out);
	}

	/** Adds up repeated items (eight "GIANT_FRAGMENT_LASER:1" slots → one entry of 8). */
	static List<Ingredient> merge(List<Ingredient> in) {
		Map<String, Double> m = new LinkedHashMap<>();
		for (Ingredient i : in) m.merge(i.key(), i.count(), Double::sum);
		List<Ingredient> out = new ArrayList<>();
		m.forEach((k, v) -> out.add(new Ingredient(k, v)));
		return List.copyOf(out);
	}

	static String str(JsonObject o, String k, String def) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
	}

	static double num(JsonObject o, String k, double def) {
		JsonElement e = o.get(k);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsDouble() : def;
		} catch (NumberFormatException ex) {
			return def;
		}
	}
}
