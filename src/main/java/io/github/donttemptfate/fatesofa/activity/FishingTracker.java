package io.github.donttemptfate.fatesofa.activity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.donttemptfate.fatesofa.core.ActivitySession;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fishing session: sea creatures caught (from their catch messages, SkyHanni-REPO's SeaCreatures data),
 * double hooks, trophy fish. Display only.
 */
public final class FishingTracker {
	public record Creature(String name, String rarity, String island) {
	}

	static final Pattern DOUBLE_HOOK = Pattern.compile("^It's a Double Hook!.*");
	public static final Pattern TROPHY_PATTERN = Pattern.compile("^TROPHY FISH! You caught an? (?<name>.+?) (?<tier>BRONZE|SILVER|GOLD|DIAMOND)\\.?$");
	static final Pattern TROPHY = TROPHY_PATTERN;

	private final Map<String, Creature> byMessage = new HashMap<>();
	private final Map<String, Integer> creatures = new LinkedHashMap<>();
	private final Map<String, Integer> trophies = new LinkedHashMap<>();
	private boolean doubleHook;
	private int doubleHooks;
	private long lastCatchAt;
	private Creature lastRare;
	public final ActivitySession session = new ActivitySession(3 * 60_000);

	/** SkyHanni-REPO SeaCreatures.json: island → {sea_creatures: {Name: {chat_message, rarity}}}. */
	public synchronized void load(JsonObject seaCreatures) {
		byMessage.clear();
		if (seaCreatures == null) return;
		for (Map.Entry<String, JsonElement> island : seaCreatures.entrySet()) {
			if (!island.getValue().isJsonObject()) continue;
			JsonObject sc = island.getValue().getAsJsonObject().getAsJsonObject("sea_creatures");
			if (sc == null) continue;
			for (Map.Entry<String, JsonElement> c : sc.entrySet()) {
				JsonObject o = c.getValue().getAsJsonObject();
				if (!o.has("chat_message")) continue;
				String rarity = o.has("rarity") ? o.get("rarity").getAsString() : "COMMON";
				byMessage.put(o.get("chat_message").getAsString().trim(), new Creature(c.getKey(), rarity, island.getKey()));
			}
		}
	}

	public synchronized int knownCreatures() {
		return byMessage.size();
	}

	/** Returns the creature caught by this message, or null. */
	public synchronized Creature chat(String plain, long now) {
		String t = plain.trim();
		if (DOUBLE_HOOK.matcher(t).matches()) {
			doubleHook = true;
			doubleHooks++;
			return null;
		}
		Matcher tm = TROPHY.matcher(t);
		if (tm.matches()) {
			trophies.merge(tm.group("name") + " " + tm.group("tier"), 1, Integer::sum);
			session.add("trophy", 1, now);
			return null;
		}
		Creature c = byMessage.get(t);
		if (c == null) return null;
		int n = doubleHook && now - lastCatchAt < 2_000 ? 1 : 1;
		creatures.merge(c.name(), n, Integer::sum);
		session.add("creatures", n, now);
		lastCatchAt = now;
		doubleHook = false;
		if (!c.rarity().equals("COMMON") && !c.rarity().equals("UNCOMMON")) lastRare = c;
		return c;
	}

	/** The rarity of a creature by name (from the loaded data), or null. */
	public synchronized String rarityOf(String name) {
		for (Creature c : byMessage.values()) if (c.name().equals(name)) return c.rarity();
		return null;
	}

	public synchronized Map<String, Integer> creatures() {
		return new LinkedHashMap<>(creatures);
	}

	public synchronized Map<String, Integer> trophies() {
		return new LinkedHashMap<>(trophies);
	}

	public synchronized int doubleHooks() {
		return doubleHooks;
	}

	public synchronized int total() {
		int t = 0;
		for (int v : creatures.values()) t += v;
		return t;
	}

	public synchronized void reset() {
		creatures.clear();
		trophies.clear();
		doubleHooks = 0;
		session.reset();
	}
}
