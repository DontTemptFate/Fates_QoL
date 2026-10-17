package io.github.donttemptfate.fatesofa.waypoints;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Waypoints: the player's own (per island, kept) and shared ones from chat ("x: 1, y: 2, z: 3", kept for a
 * few minutes). Only positions people typed; nothing about the world is revealed.
 */
public final class Waypoints {
	public record Waypoint(String name, int x, int y, int z, int color, String island, long expiresAt) {
		public boolean temporary() {
			return expiresAt > 0;
		}
	}

	static final Pattern COORDS = Pattern.compile("(?i)x:\\s*(?<x>-?\\d{1,7})(?:\\.\\d+)?,?\\s*y:\\s*(?<y>-?\\d{1,4})(?:\\.\\d+)?,?\\s*z:\\s*(?<z>-?\\d{1,7})(?:\\.\\d+)?");
	static final Pattern SENDER = Pattern.compile("^(?:Party > |Guild > |Co-op > )?(?:\\[[^]]+] )?(?<name>[A-Za-z0-9_]{2,16})(?: \\[[^]]+])?: ");

	private final List<Waypoint> list = new ArrayList<>();

	public synchronized void add(Waypoint w) {
		list.removeIf(o -> o.name().equalsIgnoreCase(w.name()) && o.island().equals(w.island()) && o.temporary() == w.temporary());
		list.add(w);
	}

	public synchronized boolean remove(String name, String island) {
		return list.removeIf(o -> o.name().equalsIgnoreCase(name) && o.island().equals(island));
	}

	public synchronized void clear(String island) {
		list.removeIf(o -> o.island().equals(island));
	}

	/** Waypoints to show on this island now (expired ones dropped). */
	public synchronized List<Waypoint> on(String island, long now) {
		list.removeIf(w -> w.temporary() && w.expiresAt() < now);
		List<Waypoint> out = new ArrayList<>();
		for (Waypoint w : list) if (w.island().equals(island)) out.add(w);
		return out;
	}

	/** A shared position in a chat line ("Fate: x: 12, y: 70, z: -40"), as a temporary waypoint; else null. */
	public static Waypoint fromChat(String plain, String island, long now, long keepMs) {
		Matcher m = COORDS.matcher(plain);
		if (!m.find()) return null;
		Matcher s = SENDER.matcher(plain);
		String who = s.find() ? s.group("name") : "Shared";
		return new Waypoint(who, Integer.parseInt(m.group("x")), Integer.parseInt(m.group("y")), Integer.parseInt(m.group("z")), 0xFFFF55, island,
				now + keepMs);
	}

	public synchronized JsonArray toJson() {
		JsonArray a = new JsonArray();
		for (Waypoint w : list) {
			if (w.temporary()) continue;
			JsonObject o = new JsonObject();
			o.addProperty("n", w.name());
			o.addProperty("x", w.x());
			o.addProperty("y", w.y());
			o.addProperty("z", w.z());
			o.addProperty("c", w.color());
			o.addProperty("i", w.island());
			a.add(o);
		}
		return a;
	}

	public synchronized void fromJson(JsonElement e) {
		list.removeIf(w -> !w.temporary());
		if (e == null || !e.isJsonArray()) return;
		for (JsonElement x : e.getAsJsonArray()) {
			try {
				JsonObject o = x.getAsJsonObject();
				list.add(new Waypoint(o.get("n").getAsString(), o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(),
						o.get("c").getAsInt(), o.get("i").getAsString().toUpperCase(Locale.ROOT), 0));
			} catch (RuntimeException ignored) {
				// skip a broken entry
			}
		}
	}
}
