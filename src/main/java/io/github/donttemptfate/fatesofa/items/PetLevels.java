package io.github.donttemptfate.fatesofa.items;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pet levels from experience, using the NEU repo's pets.json tables (rarity offsets, custom pets like Golden Dragon). */
public final class PetLevels {
	public record Level(int level, int maxLevel, double expIntoLevel, double expForLevel, double expToMax) {
		public double progress() {
			return expForLevel <= 0 ? 1 : Math.min(1, expIntoLevel / expForLevel);
		}
	}

	private final List<Long> levels;
	private final Map<String, Integer> offsets;
	private final Map<String, JsonObject> custom;

	public PetLevels(JsonObject pets) {
		levels = new ArrayList<>();
		for (JsonElement e : pets.getAsJsonArray("pet_levels")) levels.add(e.getAsLong());
		offsets = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : pets.getAsJsonObject("pet_rarity_offset").entrySet()) offsets.put(e.getKey(), e.getValue().getAsInt());
		custom = new HashMap<>();
		if (pets.has("custom_pet_leveling")) {
			for (Map.Entry<String, JsonElement> e : pets.getAsJsonObject("custom_pet_leveling").entrySet()) {
				if (e.getValue().isJsonObject()) custom.put(e.getKey(), e.getValue().getAsJsonObject());
			}
		}
	}

	public Level level(String type, String tier, double exp) {
		JsonObject c = custom.get(type);
		int offset = offsets.getOrDefault(tier, 0);
		if (c != null && c.has("rarity_offset") && c.getAsJsonObject("rarity_offset").has(tier)) {
			offset = c.getAsJsonObject("rarity_offset").get(tier).getAsInt();
		}
		int maxLevel = c != null && c.has("max_level") ? c.get("max_level").getAsInt() : 100;
		List<Long> table = new ArrayList<>(levels.subList(Math.min(offset, levels.size()), Math.min(levels.size(), offset + 99)));
		if (c != null && c.has("pet_levels")) {
			JsonArray extra = c.getAsJsonArray("pet_levels");
			int type1 = c.has("type") ? c.get("type").getAsInt() : 0;
			if (type1 == 1) for (JsonElement e : extra) table.add(e.getAsLong());
			else {
				table.clear();
				for (JsonElement e : extra) table.add(e.getAsLong());
			}
		}
		if (table.size() > maxLevel - 1) table = new ArrayList<>(table.subList(0, Math.max(0, maxLevel - 1)));
		int level = 1;
		double left = exp;
		double total = 0;
		for (long l : table) total += l;
		for (int i = 0; i < table.size() && level < maxLevel; i++) {
			if (left < table.get(i)) {
				double toMax = total;
				for (int k = 0; k < i; k++) toMax -= table.get(k);
				return new Level(level, maxLevel, left, table.get(i), Math.max(0, toMax - left));
			}
			left -= table.get(i);
			level++;
		}
		return new Level(level, maxLevel, 0, 0, 0);
	}
}
