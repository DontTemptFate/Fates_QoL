package io.github.donttemptfate.fatesofa.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.donttemptfate.fates.common.text.Strip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** What OFA remembers about one stored item: enough to draw it, search it and value it. */
public record StoredItem(int slot, String itemId, String key, String name, List<String> lore, String model, String profile, int count,
		JsonObject upgrades) {
	/** Without upgrade data (plain items). */
	public StoredItem(int slot, String itemId, String key, String name, List<String> lore, String model, String profile, int count) {
		this(slot, itemId, key, name, lore, model, profile, count, null);
	}

	public String searchText() {
		StringBuilder b = new StringBuilder(Strip.clean(name));
		for (String l : lore) b.append(' ').append(Strip.clean(l));
		if (key != null) b.append(' ').append(key.replace('_', ' '));
		return b.toString().toLowerCase(Locale.ROOT);
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("s", slot);
		o.addProperty("i", itemId);
		if (key != null) o.addProperty("k", key);
		o.addProperty("n", name);
		JsonArray l = new JsonArray();
		lore.forEach(l::add);
		o.add("l", l);
		if (model != null) o.addProperty("m", model);
		if (profile != null) o.addProperty("p", profile);
		o.addProperty("c", count);
		if (upgrades != null) o.add("u", upgrades);
		return o;
	}

	public static StoredItem fromJson(JsonObject o) {
		List<String> lore = new ArrayList<>();
		if (o.has("l")) for (JsonElement e : o.getAsJsonArray("l")) lore.add(e.getAsString());
		return new StoredItem(o.get("s").getAsInt(), o.get("i").getAsString(), o.has("k") ? o.get("k").getAsString() : null,
				o.get("n").getAsString(), List.copyOf(lore), o.has("m") ? o.get("m").getAsString() : null,
				o.has("p") ? o.get("p").getAsString() : null, o.has("c") ? o.get("c").getAsInt() : 1,
				o.has("u") && o.get("u").isJsonObject() ? o.getAsJsonObject("u") : null);
	}
}
