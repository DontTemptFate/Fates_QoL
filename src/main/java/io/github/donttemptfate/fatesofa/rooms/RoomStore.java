package io.github.donttemptfate.fatesofa.rooms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Secrets and routes per room cell (by {@link RoomFingerprint} id), in room offsets ({@link RoomGrid#toRoom}).
 * Used for what this player learned, for imported share files and for the shared pack from GitHub. The
 * same JSON format is the share file ("ofa-rooms").
 *
 * <p>Nothing here identifies a player: a contributor is a random id made on first use.
 */
public final class RoomStore {
	public enum Kind {
		CHEST("Chest", 0xFFAA00), ITEM("Item", 0x55FFFF), BAT("Bat", 0x55FF55), ESSENCE("Essence", 0xAA55FF), LEVER("Lever", 0xFFFF55),
		OTHER("Secret", 0xAAAAAA);

		public final String label;
		public final int rgb;

		Kind(String label, int rgb) {
			this.label = label;
			this.rgb = rgb;
		}

		static Kind parse(String s) {
			try {
				return valueOf(s.toUpperCase(Locale.ROOT));
			} catch (RuntimeException e) {
				return null;
			}
		}
	}

	public static final class Secret {
		public double x, y, z;
		public Kind kind;
		/** Who saw it (contributor ids); empty in the shared pack, which carries a count instead. */
		public final Set<String> by = new LinkedHashSet<>();
		public int shared;

		Secret(double x, double y, double z, Kind kind) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.kind = kind;
		}

		/** How many different players have seen it. */
		public int seen() {
			return Math.max(by.size(), shared);
		}

		/** The same secret: close, or near a guess (a guess is the finder's feet, so allow more room). */
		boolean matches(double ox, double oy, double oz, Kind k) {
			if (dist2(ox, oy, oz) <= SAME * SAME) return true;
			if (kind != Kind.OTHER && k != Kind.OTHER) return false;
			double dx = x - ox, dz = z - oz;
			return dx * dx + dz * dz <= GUESS * GUESS && Math.abs(y - oy) <= 4.5;
		}

		double dist2(double ox, double oy, double oz) {
			double dx = x - ox, dy = y - oy, dz = z - oz;
			return dx * dx + dy * dy + dz * dz;
		}
	}

	/** A walked path through the room: points in room offsets from the cell it started in. */
	public record Route(long ms, List<double[]> points, String by) {
	}

	public static final class Room {
		public final List<Secret> secrets = new ArrayList<>();
		public Route route;
	}

	public static final String TYPE = "ofa-rooms";
	public static final int MAX_ROOMS = 4000, MAX_SECRETS = 60, MAX_POINTS = 600, MAX_BY = 64;
	/** Two finds this close (blocks) are the same secret. */
	public static final double SAME = 1.6;
	/** Sideways distance at which a guessed position (kind OTHER) still matches. */
	public static final double GUESS = 2.6;
	static final Pattern ID = Pattern.compile("^[0-9a-f]{16}$");
	static final Pattern WHO = Pattern.compile("^[A-Za-z0-9:_-]{1,48}$");

	private final Map<String, Room> rooms = new LinkedHashMap<>();
	private boolean dirty;

	public synchronized Room room(String id) {
		return rooms.get(id);
	}

	/** A copy of a room's secrets (empty if unknown); safe while another thread adds. */
	public synchronized List<Secret> secrets(String id) {
		Room r = rooms.get(id);
		return r == null ? List.of() : List.copyOf(r.secrets);
	}

	public synchronized Route route(String id) {
		Room r = rooms.get(id);
		return r == null ? null : r.route;
	}

	public synchronized int roomCount() {
		return rooms.size();
	}

	public synchronized int secretCount() {
		int n = 0;
		for (Room r : rooms.values()) n += r.secrets.size();
		return n;
	}

	public synchronized int routeCount() {
		int n = 0;
		for (Room r : rooms.values()) if (r.route != null) n++;
		return n;
	}

	public synchronized boolean dirty() {
		return dirty;
	}

	public synchronized void saved() {
		dirty = false;
	}

	/** Records a find. Returns true when it is a secret this store didn't know. */
	public synchronized boolean addSecret(String id, double x, double y, double z, Kind kind, String who) {
		if (!ID.matcher(id).matches()) return false;
		Room r = rooms.get(id);
		if (r == null) {
			if (rooms.size() >= MAX_ROOMS) return false;
			rooms.put(id, r = new Room());
		}
		dirty = true;
		for (Secret s : r.secrets) {
			if (s.matches(x, y, z, kind)) {
				if (who != null && s.by.size() < MAX_BY) s.by.add(who);
				if (s.kind == Kind.OTHER && kind != Kind.OTHER) { // a precise find replaces a guess from the player's position
					s.kind = kind;
					s.x = x;
					s.y = y;
					s.z = z;
				}
				return false;
			}
		}
		if (r.secrets.size() >= MAX_SECRETS) return false;
		Secret s = new Secret(x, y, z, kind);
		if (who != null) s.by.add(who);
		r.secrets.add(s);
		return true;
	}

	/** Keeps the route if it's this room's first or fastest. */
	public synchronized boolean offerRoute(String id, Route route) {
		if (!ID.matcher(id).matches() || route.points().size() < 2) return false;
		Room r = rooms.get(id);
		if (r == null) {
			if (rooms.size() >= MAX_ROOMS) return false;
			rooms.put(id, r = new Room());
		}
		if (r.route != null && r.route.ms() <= route.ms()) return false;
		r.route = new Route(route.ms(), List.copyOf(route.points().subList(0, Math.min(MAX_POINTS, route.points().size()))), route.by());
		dirty = true;
		return true;
	}

	/** Adds everything from {@code other} (an imported share file). Returns the number of new secrets. */
	public synchronized int merge(RoomStore other) {
		int added = 0;
		for (Map.Entry<String, Room> e : other.snapshot().entrySet()) {
			for (Secret s : e.getValue().secrets) {
				boolean isNew = false;
				if (s.by.isEmpty()) isNew = addSecret(e.getKey(), s.x, s.y, s.z, s.kind, null);
				for (String w : s.by) isNew |= addSecret(e.getKey(), s.x, s.y, s.z, s.kind, w);
				if (isNew) added++;
			}
			if (e.getValue().route != null) offerRoute(e.getKey(), e.getValue().route);
		}
		return added;
	}

	/** Only what {@code who} saw or walked themselves: the share file, so nobody passes on others' finds as theirs. */
	public synchronized RoomStore own(String who) {
		RoomStore out = new RoomStore();
		for (Map.Entry<String, Room> e : rooms.entrySet()) {
			for (Secret s : e.getValue().secrets) if (s.by.contains(who)) out.addSecret(e.getKey(), s.x, s.y, s.z, s.kind, who);
			Route r = e.getValue().route;
			if (r != null && who.equals(r.by())) out.offerRoute(e.getKey(), r);
		}
		return out;
	}

	synchronized Map<String, Room> snapshot() {
		return new LinkedHashMap<>(rooms);
	}

	// ---- JSON ----

	static double round(double v) {
		return Math.round(v * 10) / 10.0;
	}

	public synchronized JsonObject toJson(String contributor) {
		JsonObject o = new JsonObject();
		o.addProperty("type", TYPE);
		o.addProperty("v", 1);
		if (contributor != null) o.addProperty("contributor", contributor);
		JsonObject rs = new JsonObject();
		for (Map.Entry<String, Room> e : rooms.entrySet()) {
			JsonObject r = new JsonObject();
			JsonArray ss = new JsonArray();
			for (Secret s : e.getValue().secrets) {
				JsonArray a = new JsonArray();
				a.add(round(s.x));
				a.add(round(s.y));
				a.add(round(s.z));
				a.add(s.kind.name());
				if (!s.by.isEmpty()) {
					JsonArray by = new JsonArray();
					for (String w : s.by) by.add(w);
					a.add(by);
				} else a.add(s.shared);
				ss.add(a);
			}
			r.add("s", ss);
			Route rt = e.getValue().route;
			if (rt != null) {
				JsonObject ro = new JsonObject();
				ro.addProperty("ms", rt.ms());
				if (rt.by() != null) ro.addProperty("by", rt.by());
				JsonArray pts = new JsonArray();
				for (double[] p : rt.points()) {
					JsonArray a = new JsonArray();
					a.add(round(p[0]));
					a.add(round(p[1]));
					a.add(round(p[2]));
					pts.add(a);
				}
				ro.add("p", pts);
				r.add("r", ro);
			}
			rs.add(e.getKey(), r);
		}
		o.add("rooms", rs);
		return o;
	}

	/** Reads a store, skipping anything malformed or out of range. Null if it isn't an OFA rooms file at all. */
	public static RoomStore fromJson(JsonElement el) {
		if (el == null || !el.isJsonObject()) return null;
		JsonObject o = el.getAsJsonObject();
		if (!o.has("type") || !TYPE.equals(str(o.get("type"))) || !o.has("rooms") || !o.get("rooms").isJsonObject()) return null;
		RoomStore st = new RoomStore();
		for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("rooms").entrySet()) {
			if (!ID.matcher(e.getKey()).matches() || !e.getValue().isJsonObject()) continue;
			JsonObject r = e.getValue().getAsJsonObject();
			if (r.has("s") && r.get("s").isJsonArray()) {
				for (JsonElement se : r.getAsJsonArray("s")) {
					if (!se.isJsonArray() || se.getAsJsonArray().size() < 4) continue;
					JsonArray a = se.getAsJsonArray();
					double x = num(a.get(0)), y = num(a.get(1)), z = num(a.get(2));
					Kind k = Kind.parse(str(a.get(3)));
					if (k == null || !inRoom(x, y, z)) continue;
					if (a.size() > 4 && a.get(4).isJsonArray()) {
						boolean any = false;
						for (JsonElement w : a.get(4).getAsJsonArray()) {
							String who = str(w);
							if (who != null && WHO.matcher(who).matches()) {
								st.addSecret(e.getKey(), x, y, z, k, who);
								any = true;
							}
						}
						if (!any) st.addSecret(e.getKey(), x, y, z, k, null);
					} else {
						st.addSecret(e.getKey(), x, y, z, k, null);
						int n = a.size() > 4 ? (int) Math.max(0, Math.min(10_000, num(a.get(4)))) : 0;
						Room room = st.rooms.get(e.getKey());
						if (room != null) for (Secret s : room.secrets) if (s.matches(x, y, z, k)) s.shared = Math.max(s.shared, n);
					}
				}
			}
			if (r.has("r") && r.get("r").isJsonObject()) {
				JsonObject ro = r.getAsJsonObject("r");
				long ms = (long) num(ro.get("ms"));
				String by = ro.has("by") ? str(ro.get("by")) : null;
				if (by != null && !WHO.matcher(by).matches()) by = null;
				List<double[]> pts = new ArrayList<>();
				if (ro.has("p") && ro.get("p").isJsonArray()) {
					for (JsonElement pe : ro.getAsJsonArray("p")) {
						if (pts.size() >= MAX_POINTS) break;
						if (!pe.isJsonArray() || pe.getAsJsonArray().size() < 3) continue;
						JsonArray a = pe.getAsJsonArray();
						double x = num(a.get(0)), y = num(a.get(1)), z = num(a.get(2));
						if (inRoute(x, y, z)) pts.add(new double[] {x, y, z});
					}
				}
				if (ms > 1000 && ms < 3_600_000 && pts.size() >= 2) st.offerRoute(e.getKey(), new Route(ms, pts, by));
			}
		}
		st.dirty = false;
		return st;
	}

	/** A secret lies inside its own 31-block cell. */
	static boolean inRoom(double x, double y, double z) {
		return Math.abs(x) <= 16 && Math.abs(z) <= 16 && y >= 0 && y <= 255;
	}

	/** A route may cross into the other cells of a big room (up to four cells long). */
	static boolean inRoute(double x, double y, double z) {
		return Math.abs(x) <= 130 && Math.abs(z) <= 130 && y >= 0 && y <= 255;
	}

	static double num(JsonElement e) {
		try {
			double d = e.getAsDouble();
			return Double.isFinite(d) ? d : Double.NaN;
		} catch (RuntimeException ex) {
			return Double.NaN;
		}
	}

	static String str(JsonElement e) {
		try {
			return e.isJsonPrimitive() ? e.getAsString() : null;
		} catch (RuntimeException ex) {
			return null;
		}
	}
}
