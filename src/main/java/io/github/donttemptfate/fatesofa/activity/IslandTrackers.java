package io.github.donttemptfate.fatesofa.activity;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fatesofa.state.TabInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Readers for island trackers: tab-list widgets and chat lines (Crystal Hollows, Mineshaft, Crimson Isle, Galatea, Rift). */
public final class IslandTrackers {
	private IslandTrackers() {}

	static final Pattern KV = Pattern.compile("^(?<k>[^:]+):\\s*(?<v>.+)$");

	/** Crystal Hollows "Crystals" widget: crystal → status text ("Found", "Not Found", "Placed"). */
	public static Map<String, String> crystals(TabInfo t) {
		Map<String, String> out = new LinkedHashMap<>();
		if (t == null) return out;
		for (String l : t.widget("Crystals")) {
			Matcher m = KV.matcher(l);
			if (!m.matches()) continue;
			String v = m.group("v").replace("✔", "").replace("✖", "").trim();
			out.put(m.group("k").replace("Crystal", "").trim(), v.isEmpty() ? (m.group("v").contains("✔") ? "Found" : "Not Found") : v);
		}
		return out;
	}

	/** Crimson Isle reputation widget: faction → reputation. */
	public static Map<String, Double> reputation(TabInfo t) {
		Map<String, Double> out = new LinkedHashMap<>();
		if (t == null) return out;
		for (String l : t.widget("Reputation")) {
			Matcher m = KV.matcher(l);
			if (!m.matches()) continue;
			double v = Fmt.parse(m.group("v").trim().split(" ")[0]);
			if (!Double.isNaN(v)) out.put(m.group("k").replace("Reputation", "").trim(), v);
		}
		return out;
	}

	/** Crimson Isle "Faction Quests" widget: {done, total}. */
	public static int[] factionQuests(TabInfo t) {
		if (t == null) return null;
		List<String> w = t.widget("Faction Quests");
		if (w.isEmpty()) return null;
		int done = 0, total = 0;
		for (String l : w) {
			if (l.contains("✔")) done++;
			if (l.contains("✔") || l.contains("✖")) total++;
		}
		return total == 0 ? null : new int[] {done, total};
	}

	/** "Enigma Souls: 34/52" anywhere on the tab list → {found, total}. */
	public static int[] enigmaSouls(TabInfo t) {
		if (t == null) return null;
		for (String l : t.lines()) {
			Matcher m = ENIGMA_TAB.matcher(l);
			if (m.find()) return new int[] {Integer.parseInt(m.group("a")), Integer.parseInt(m.group("b"))};
		}
		return null;
	}

	static final Pattern ENIGMA_TAB = Pattern.compile("Enigma Souls: (?<a>\\d+)/(?<b>\\d+)");

	/** Odger's trophy fish menu: from an item's lore, how many of Bronze/Silver/Gold/Diamond are caught. */
	public static int trophyTiers(List<String> lore) {
		int n = 0;
		for (String l : lore) {
			String t = l.trim();
			if ((t.startsWith("Bronze") || t.startsWith("Silver") || t.startsWith("Gold") || t.startsWith("Diamond")) && t.contains("✔")) n++;
		}
		return n;
	}

	/** Chat lines OFA counts, by what they mean. */
	public static final Map<String, Pattern> CHAT = new LinkedHashMap<>();

	static {
		CHAT.put("ch_chest", Pattern.compile("^(?:You have successfully picked the lock on this chest!|You uncovered a treasure chest!)$"));
		CHAT.put("corpse", Pattern.compile("^\\s*(?<type>LAPIS|TUNGSTEN|UMBER|VANGUARD) CORPSE LOOT!\\s*$"));
		CHAT.put("mineshaft", Pattern.compile("^WOW! You found a Glacite Mineshaft portal!$"));
		CHAT.put("tree_gift", Pattern.compile("^(?:TREE GIFT!|You received a Tree Gift!|\\s*TREE GIFT\\s*)$"));
		CHAT.put("enigma", Pattern.compile("^SOUL! You unlocked an Enigma Soul!$"));
		CHAT.put("montezuma", Pattern.compile("^You found a piece of Montezuma's soul!.*$"));
	}

	/** Which counter a chat line belongs to and its detail (corpse type), or null. */
	public static String[] chat(String plain) {
		for (var e : CHAT.entrySet()) {
			Matcher m = e.getValue().matcher(plain);
			if (m.matches()) return new String[] {e.getKey(), e.getKey().equals("corpse") ? m.group("type") : ""};
		}
		return null;
	}
}
