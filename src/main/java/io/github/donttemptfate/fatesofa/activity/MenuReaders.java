package io.github.donttemptfate.fatesofa.activity;

import io.github.donttemptfate.fatescommon.text.Fmt;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Readers for numbers in SkyBlock menu item lore (collections, minions, the chocolate factory). Pure text in, values out. */
public final class MenuReaders {
	private MenuReaders() {}

	// ---- collections --------------------------------------------------------------------------------

	/** "Progress to Wheat IX: 45.3%" then "12,345/25,000": the collection, next tier, current and needed. */
	public record Collection(String name, String nextTier, double current, double needed) {
	}

	static final Pattern PROGRESS_TO = Pattern.compile("^Progress to (?<name>.+?) (?<tier>[IVXLC]+): (?<pct>[\\d.]+)%");
	static final Pattern AMOUNTS = Pattern.compile("(?<cur>[\\d,.]+[kKmMbB]?)/(?<max>[\\d,.]+[kKmMbB]?)");

	public static Collection collection(List<String> lore) {
		for (int i = 0; i < lore.size(); i++) {
			Matcher m = PROGRESS_TO.matcher(lore.get(i).trim());
			if (!m.find()) continue;
			for (int j = i; j < Math.min(lore.size(), i + 3); j++) {
				Matcher a = AMOUNTS.matcher(lore.get(j));
				if (a.find()) return new Collection(m.group("name"), m.group("tier"), Fmt.parse(a.group("cur")), Fmt.parse(a.group("max")));
			}
		}
		return null;
	}

	// ---- minions ------------------------------------------------------------------------------------

	static final Pattern MINION_TITLE = Pattern.compile("^(?<type>.+?) Minion (?<tier>[IVX]+)$");
	static final Pattern TIME_LEFT = Pattern.compile("(?i)(?:time remaining|time left|duration):?\\s*(?<t>(?:\\d+[dhms]\\s*)+)");

	/** "Snow Minion XI" → {"Snow", "XI"}; null when the title isn't a minion. */
	public static String[] minion(String title) {
		Matcher m = MINION_TITLE.matcher(title.trim());
		return m.matches() ? new String[] {m.group("type"), m.group("tier")} : null;
	}

	/** "Time Remaining: 1d 4h 20m" → milliseconds; -1 when not found. */
	public static long timeLeft(List<String> lore) {
		for (String l : lore) {
			Matcher m = TIME_LEFT.matcher(l);
			if (m.find()) return duration(m.group("t"));
		}
		return -1;
	}

	static long duration(String s) {
		long ms = 0;
		Matcher m = Pattern.compile("(\\d+)([dhms])").matcher(s);
		while (m.find()) {
			long n = Long.parseLong(m.group(1));
			ms += switch (m.group(2)) {
				case "d" -> n * 86_400_000L;
				case "h" -> n * 3_600_000L;
				case "m" -> n * 60_000L;
				default -> n * 1000L;
			};
		}
		return ms;
	}

	// ---- chocolate factory --------------------------------------------------------------------------

	/** An upgrade: chocolate per second it adds and what it costs. */
	public record Upgrade(double gainPerSecond, double cost) {
		public double costPerChocolate() {
			return gainPerSecond > 0 ? cost / gainPerSecond : Double.MAX_VALUE;
		}
	}

	static final Pattern CPS_GAIN = Pattern.compile("\\+(?<g>[\\d,.]+) Chocolate per second");
	static final Pattern COST_LINE = Pattern.compile("^(?<c>[\\d,.]+[kKmMbB]?) Chocolate$");

	/** From an upgrade's lore: "+2 Chocolate per second" and a "Cost" line followed by "1,234 Chocolate". */
	public static Upgrade upgrade(List<String> lore) {
		double gain = Double.NaN, cost = Double.NaN;
		boolean afterCost = false;
		for (String raw : lore) {
			String l = raw.trim();
			Matcher g = CPS_GAIN.matcher(l);
			if (g.find() && Double.isNaN(gain)) gain = Fmt.parse(g.group("g"));
			if (l.equalsIgnoreCase("Cost") || l.startsWith("Cost:")) {
				afterCost = true;
				Matcher c = COST_LINE.matcher(l.replaceFirst("(?i)^cost:\\s*", ""));
				if (c.matches()) cost = Fmt.parse(c.group("c"));
				continue;
			}
			if (afterCost && Double.isNaN(cost)) {
				Matcher c = COST_LINE.matcher(l);
				if (c.matches()) cost = Fmt.parse(c.group("c"));
			}
		}
		return Double.isNaN(gain) || Double.isNaN(cost) ? null : new Upgrade(gain, cost);
	}

	static final Pattern CPS_NOW = Pattern.compile("(?i)per second:?\\s*(?<v>[\\d,.]+)");

	/** The factory's current chocolate per second from the main item ("Per Second: 12,345"); NaN when not shown. */
	public static double perSecond(List<String> lore) {
		for (String l : lore) {
			Matcher m = CPS_NOW.matcher(l);
			if (m.find()) return Fmt.parse(m.group("v"));
		}
		return Double.NaN;
	}
}
