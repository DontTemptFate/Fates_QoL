package io.github.donttemptfate.fatesofa.protect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Item and slot protection: decides whether one of the player's own actions on an item goes ahead.
 * It can only stop or ask to confirm the player's click; it never makes one. Locked items are known by
 * their SkyBlock uuid (or by item key for stackables without one), so a lock follows the item.
 */
public final class Protection {
	public enum Action {
		DROP("drop"),
		NPC_SELL("sell to an NPC"),
		SALVAGE("salvage"),
		TRADE("put in a trade"),
		AUCTION("list on the auction house"),
		BAZAAR("sell on the bazaar");

		public final String verb;

		Action(String verb) {
			this.verb = verb;
		}
	}

	public enum Verdict { ALLOW, BLOCK, CONFIRM }

	public record Decision(Verdict verdict, String reason) {
		static final Decision ALLOW = new Decision(Verdict.ALLOW, null);
	}

	/** What protection needs to know about the item under the click. */
	public record Facts(String uuid, String key, String name, double value, int slot) {
	}

	private final Set<String> lockedUuids = new LinkedHashSet<>();
	private final Set<String> lockedKeys = new LinkedHashSet<>();
	private final Set<Integer> lockedSlots = new LinkedHashSet<>();

	public volatile boolean enabled = true;
	/** Items worth at least this much ask for a second click before being sold or salvaged (0 = off). */
	public volatile double confirmValue = 5_000_000;
	public volatile long confirmWindowMs = 2_500;
	/** A second click sooner than this is an accidental double-click, not a confirmation. */
	public volatile long confirmMinGapMs = 300;

	private String pendingConfirm;
	private long pendingAt;

	public synchronized Decision decide(Action action, Facts f, long now) {
		if (!enabled || f == null) return Decision.ALLOW;
		boolean locked = (f.uuid() != null && lockedUuids.contains(f.uuid())) || (f.uuid() == null && f.key() != null && lockedKeys.contains(f.key()));
		if (locked && action != Action.TRADE) {
			return new Decision(Verdict.BLOCK, label(f) + " is locked — unlock it to " + action.verb + " it");
		}
		if (locked) return new Decision(Verdict.BLOCK, label(f) + " is locked");
		boolean valuable = confirmValue > 0 && f.value() >= confirmValue;
		if (valuable && (action == Action.NPC_SELL || action == Action.SALVAGE || action == Action.DROP)) {
			String token = action + "|" + (f.uuid() != null ? f.uuid() : f.key()) + "|" + f.slot();
			if (token.equals(pendingConfirm) && now - pendingAt <= confirmWindowMs) {
				if (now - pendingAt < confirmMinGapMs) return new Decision(Verdict.CONFIRM, label(f) + " is valuable — click again (a moment apart) to " + action.verb + " it");
				pendingConfirm = null;
				return Decision.ALLOW;
			}
			pendingConfirm = token;
			pendingAt = now;
			return new Decision(Verdict.CONFIRM, label(f) + " is valuable — do it again within " + (confirmWindowMs / 1000.0) + "s to " + action.verb + " it");
		}
		return Decision.ALLOW;
	}

	/**
	 * What putting one of your own items into this window does, from its title and its fixed buttons. Null when the
	 * window is somewhere locked items may go (chests, storage, wardrobe, pets…). Locks stop selling, dropping, trading
	 * and listing; moving is fine.
	 */
	public static Action actionFor(String title, java.util.Collection<String> buttons) {
		String t = title == null ? "" : title.trim();
		String low = t.toLowerCase(java.util.Locale.ROOT);
		if (low.contains("salvage")) return Action.SALVAGE;
		for (String b : buttons) if (b.equals("Sell Item") || b.startsWith("Buyback")) return Action.NPC_SELL;
		if (low.contains("auction")) return Action.AUCTION;
		if (low.contains("bazaar")) return Action.BAZAAR;
		if ((t.startsWith("You ") && t.length() > 4 && t.substring(4).trim().length() > 0) || low.contains("trade")) return Action.TRADE;
		return null;
	}

	static String label(Facts f) {
		return f.name() == null || f.name().isBlank() ? "This item" : f.name();
	}

	// ---- locks --------------------------------------------------------------------------------------

	/** Locks or unlocks an item; returns true when it is now locked. Needs a uuid or a key. */
	public synchronized boolean toggleItem(String uuid, String key) {
		if (uuid != null) {
			if (!lockedUuids.remove(uuid)) {
				lockedUuids.add(uuid);
				return true;
			}
			return false;
		}
		if (key == null) return false;
		if (!lockedKeys.remove(key)) {
			lockedKeys.add(key);
			return true;
		}
		return false;
	}

	public synchronized boolean toggleSlot(int slot) {
		if (!lockedSlots.remove(slot)) {
			lockedSlots.add(slot);
			return true;
		}
		return false;
	}

	public synchronized boolean itemLocked(String uuid, String key) {
		return uuid != null ? lockedUuids.contains(uuid) : key != null && lockedKeys.contains(key);
	}

	public synchronized boolean slotLocked(int slot) {
		return lockedSlots.contains(slot);
	}

	public synchronized Set<Integer> lockedSlots() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(lockedSlots));
	}

	public synchronized int lockedItemCount() {
		return lockedUuids.size() + lockedKeys.size();
	}

	public synchronized JsonObject toJson() {
		JsonObject o = new JsonObject();
		JsonArray u = new JsonArray();
		lockedUuids.forEach(u::add);
		JsonArray k = new JsonArray();
		lockedKeys.forEach(k::add);
		JsonArray s = new JsonArray();
		lockedSlots.forEach(s::add);
		o.add("uuids", u);
		o.add("keys", k);
		o.add("slots", s);
		return o;
	}

	public synchronized void fromJson(JsonElement e) {
		lockedUuids.clear();
		lockedKeys.clear();
		lockedSlots.clear();
		if (e == null || !e.isJsonObject()) return;
		JsonObject o = e.getAsJsonObject();
		try {
			if (o.has("uuids")) for (JsonElement x : o.getAsJsonArray("uuids")) lockedUuids.add(x.getAsString());
			if (o.has("keys")) for (JsonElement x : o.getAsJsonArray("keys")) lockedKeys.add(x.getAsString());
			if (o.has("slots")) for (JsonElement x : o.getAsJsonArray("slots")) {
				int v = x.getAsInt();
				if (v >= 0 && v < 64) lockedSlots.add(v);
			}
		} catch (RuntimeException ignored) {
			// keep what was read
		}
	}
}
