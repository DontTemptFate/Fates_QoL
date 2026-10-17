package io.github.donttemptfate.fatesofa.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Parsers for the three price sources. Each returns an immutable table. */
public final class PriceTables {
	private PriceTables() {}

	/** Bazaar quote (Hypixel quick_status): instant buy ≥ instant sell. */
	public record Bz(double instantBuy, double instantSell, long buyVolume, long sellVolume, long buyMovingWeek, long sellMovingWeek) {
	}

	/** Lowest BIN and recent median from the fatesflipper.com public price file. */
	public record Ah(double lbin, double median, double salesPerDay, long lastSaleAt) {
	}

	/** {@code api.hypixel.net/v2/skyblock/bazaar} → product id → quote. */
	public static Map<String, Bz> bazaar(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		if (!root.has("products")) throw new IllegalArgumentException("no products");
		Map<String, Bz> out = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("products").entrySet()) {
			try {
				JsonObject q = e.getValue().getAsJsonObject().getAsJsonObject("quick_status");
				out.put(e.getKey(), new Bz(d(q, "buyPrice"), d(q, "sellPrice"), (long) d(q, "buyVolume"), (long) d(q, "sellVolume"),
						(long) d(q, "buyMovingWeek"), (long) d(q, "sellMovingWeek")));
			} catch (RuntimeException ignored) {
				// skip one odd product
			}
		}
		return Collections.unmodifiableMap(out);
	}

	/** {@code api.hypixel.net/v2/resources/skyblock/items} → item id → NPC sell price (only items that have one). */
	public static Map<String, Double> npc(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		Map<String, Double> out = new HashMap<>();
		for (JsonElement e : root.getAsJsonArray("items")) {
			try {
				JsonObject o = e.getAsJsonObject();
				if (o.has("npc_sell_price")) out.put(o.get("id").getAsString().replace(':', '-'), o.get("npc_sell_price").getAsDouble());
			} catch (RuntimeException ignored) {
				// skip
			}
		}
		return Collections.unmodifiableMap(out);
	}

	/** The public price file: {@code {v:1, generated_at, ah:{KEY:{lbin, median, sales_day, last_sale_at}}}}. */
	public static Map<String, Ah> ah(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		if (!root.has("v") || root.get("v").getAsInt() != 1) throw new IllegalArgumentException("unknown price file version");
		Map<String, Ah> out = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("ah").entrySet()) {
			try {
				JsonObject o = e.getValue().getAsJsonObject();
				out.put(e.getKey(), new Ah(d(o, "lbin"), d(o, "median"), d(o, "sales_day"), (long) d(o, "last_sale_at")));
			} catch (RuntimeException ignored) {
				// skip
			}
		}
		return Collections.unmodifiableMap(out);
	}

	static double d(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? Double.NaN : e.getAsDouble();
	}

	/** Bazaar product id for a NEU key: same id, or {@code ENCHANTMENT_NAME_LEVEL} for a one-enchant book. */
	public static String bazaarId(String key, Map<String, Bz> bz) {
		if (key == null) return null;
		if (bz.containsKey(key)) return key;
		int semi = key.indexOf(';');
		if (semi > 0) {
			String ench = "ENCHANTMENT_" + key.substring(0, semi) + "_" + key.substring(semi + 1);
			if (bz.containsKey(ench)) return ench;
		}
		String dashed = key.replace('-', ':');
		return bz.containsKey(dashed) ? dashed : null;
	}
}
