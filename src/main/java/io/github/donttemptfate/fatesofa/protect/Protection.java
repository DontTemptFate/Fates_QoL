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
	public record Facts(String uuid, String key, String name, double value, int slot, double npcValue) {
		public Facts(String uuid, String key, String name, double value, int slot) {
			this(uuid, key, name, value, slot, Double.NaN);
		}
	}

	/** Ask before selling to an NPC for less than this share of the item's value (0 = off). */
	public volatile double npcMinShare = 0.05;
	/** ...only for items worth at least this much. */
	public volatile double npcMinValue = 100_000;
	/** Items worth at least this much ask for a second click before being dropped (0 = off; separate from the sell threshold). */
	public volatile double dropConfirmValue = 0;
	/** Auction prices under this share of the lowest BIN ask for a second click (0 = off). */
	public volatile double auctionLowShare = 0.5;
	/** Auction prices over this many times the lowest BIN ask too (a stray zero; 0 = off). */
	public volatile double auctionHighTimes = 10;

	private final Set<String> lockedUuids = new LinkedHashSet<>();
	private final Set<String> lockedKeys = new LinkedHashSet<>();
	private final Set<Integer> lockedSlots = new LinkedHashSet<>();

	public volatile boolean enabled = true;
	/** Items worth at least this much ask for a second click before being sold or salvaged (0 = off). */
	public volatile double confirmValue = 5_000_000;
	public volatile long confirmWindowMs = 2_500;
	/** A second click sooner than this is an accidental double-click, not a confirmation. */
	public volatile long confirmMinGapMs = 300;

	/** What a lock stops (the player can allow some, e.g. trading their locked items). */
	public volatile java.util.Set<Action> lockStops = java.util.EnumSet.allOf(Action.class);
	/** What a valuable item asks a second click for. */
	public volatile java.util.Set<Action> confirmFor = java.util.EnumSet.of(Action.NPC_SELL, Action.SALVAGE, Action.DROP);

	private String pendingConfirm;
	private long pendingAt;

	/**
	 * One form for item uuids: lower case, no dashes (as Fates Common 0.3 gives them). Locks saved before
	 * OFA moved onto the library kept Minecraft's dashed form; both match the same item.
	 */
	public static String norm(String uuid) {
		if (uuid == null) return null;
		String u = uuid.replace("-", "").trim().toLowerCase(java.util.Locale.ROOT);
		return u.isEmpty() ? null : u;
	}

	public synchronized Decision decide(Action action, Facts f, long now) {
		if (!enabled || f == null) return Decision.ALLOW;
		String uuid = norm(f.uuid());
		boolean locked = (uuid != null && lockedUuids.contains(uuid)) || (uuid == null && f.key() != null && lockedKeys.contains(f.key()));
		if (locked && lockStops.contains(action)) {
			if (action != Action.TRADE) return new Decision(Verdict.BLOCK, label(f) + " is locked — unlock it to " + action.verb + " it");
			return new Decision(Verdict.BLOCK, label(f) + " is locked");
		}
		String token = action + "|" + (uuid != null ? uuid : f.key()) + "|" + f.slot();
		boolean valuable = confirmValue > 0 && f.value() >= confirmValue;
		if (valuable && confirmFor.contains(action)) return confirm(token, label(f) + " is valuable", action.verb + " it", now);
		if (action == Action.DROP && dropConfirmValue > 0 && f.value() >= dropConfirmValue) return confirm(token, label(f) + " is worth keeping", "drop it", now);
		if (action == Action.NPC_SELL && npcMinShare > 0 && f.value() >= npcMinValue && !Double.isNaN(f.npcValue()) && f.npcValue() < f.value() * npcMinShare) {
			int pct = (int) Math.round(f.npcValue() / f.value() * 100);
			return confirm(token, "The NPC pays only " + pct + "% of what " + label(f) + " is worth", "sell it anyway", now);
		}
		return Decision.ALLOW;
	}

	/**
	 * Creating an auction: {@code price} against the item's lowest BIN. A price far under it, or ten times
	 * over it (an extra zero), needs the same click a second time.
	 */
	public synchronized Decision decideAuction(Facts f, double price, double lowestBin, long now) {
		if (!enabled || f == null || !(price > 0) || !(lowestBin > 0)) return Decision.ALLOW;
		String uuid = norm(f.uuid());
		String token = "AUCTION_PRICE|" + (uuid != null ? uuid : f.key()) + "|" + Math.round(price);
		if (auctionLowShare > 0 && price < lowestBin * auctionLowShare)
			return confirm(token, "That price is only " + Math.round(price / lowestBin * 100) + "% of the lowest BIN", "list it anyway", now);
		if (auctionHighTimes > 0 && price > lowestBin * auctionHighTimes)
			return confirm(token, "That price is " + Math.round(price / lowestBin) + "× the lowest BIN (an extra zero?)", "list it anyway", now);
		return Decision.ALLOW;
	}

	/** First time: CONFIRM with the reason; the same action again inside the window (not a double-click): ALLOW. */
	Decision confirm(String token, String why, String what, long now) {
		if (token.equals(pendingConfirm) && now - pendingAt <= confirmWindowMs) {
			if (now - pendingAt < confirmMinGapMs) return new Decision(Verdict.CONFIRM, why + " — click again (a moment apart) to " + what);
			pendingConfirm = null;
			return Decision.ALLOW;
		}
		pendingConfirm = token;
		pendingAt = now;
		return new Decision(Verdict.CONFIRM, why + " — do it again within " + (confirmWindowMs / 1000.0) + "s to " + what);
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
	public synchronized boolean toggleItem(String rawUuid, String key) {
		String uuid = norm(rawUuid);
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

	public synchronized boolean itemLocked(String rawUuid, String key) {
		String uuid = norm(rawUuid);
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
			if (o.has("uuids")) for (JsonElement x : o.getAsJsonArray("uuids")) {
				String u = norm(x.getAsString()); // older files: dashed form
				if (u != null) lockedUuids.add(u);
			}
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
