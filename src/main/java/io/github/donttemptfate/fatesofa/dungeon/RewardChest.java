package io.github.donttemptfate.fatesofa.dungeon;

import io.github.donttemptfate.fates.common.text.Fmt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dungeon / Kuudra reward chests: what opening one costs (coins and keys) and essence rewards. */
public final class RewardChest {
	private RewardChest() {}

	public static final Pattern TITLE = Pattern.compile("^(?:(?:Wood|Gold|Diamond|Emerald|Obsidian|Bedrock) Chest|Paid Chest|Free Chest)$");
	static final Pattern COINS = Pattern.compile("^(?<n>[\\d,.]+[kKmM]?) Coins$");
	static final Pattern ESSENCE = Pattern.compile("^(?<type>\\w+) Essence x(?<n>[\\d,]+)$");
	static final Pattern ITEM_COUNT = Pattern.compile("^(?<name>.+?)(?: x(?<n>\\d+))?$");

	/** The cost of opening: coins and other items (keys), by name and amount. */
	public record Cost(double coins, List<String[]> items) {
	}

	/** From the open button's lore: the lines after "Cost" ("25,000 Coins", "Dungeon Chest Key"), up to a blank line. */
	public static Cost cost(List<String> lore) {
		double coins = 0;
		List<String[]> items = new ArrayList<>();
		boolean in = false;
		for (String raw : lore) {
			String l = raw.trim();
			if (l.equalsIgnoreCase("Cost") || l.equalsIgnoreCase("Cost:")) {
				in = true;
				continue;
			}
			if (!in) continue;
			if (l.isEmpty()) break;
			Matcher c = COINS.matcher(l);
			if (c.matches()) {
				coins += Fmt.parse(c.group("n"));
				continue;
			}
			if (l.toLowerCase(Locale.ROOT).contains("free") || l.startsWith("Click")) continue;
			Matcher m = ITEM_COUNT.matcher(l);
			if (m.matches()) items.add(new String[] {m.group("name"), m.group("n") == null ? "1" : m.group("n")});
		}
		return in ? new Cost(coins, items) : null;
	}

	/** "Wither Essence x3" → {"ESSENCE_WITHER", "3"}; null otherwise. */
	public static String[] essence(String name) {
		Matcher m = ESSENCE.matcher(name.trim());
		return m.matches() ? new String[] {"ESSENCE_" + m.group("type").toUpperCase(Locale.ROOT), m.group("n").replace(",", "")} : null;
	}
}
