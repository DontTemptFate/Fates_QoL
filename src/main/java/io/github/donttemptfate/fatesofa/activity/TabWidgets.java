package io.github.donttemptfate.fatesofa.activity;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fatesofa.state.TabInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the tab-list widgets OFA shows on the HUD: commissions, powders, visitors, pests, composter. */
public final class TabWidgets {
	private TabWidgets() {}

	public record Commission(String name, String progress, boolean done) {
	}

	static final Pattern KV = Pattern.compile("^(?<k>[^:]+):\\s*(?<v>.+)$");

	public static List<Commission> commissions(TabInfo t) {
		List<Commission> out = new ArrayList<>();
		if (t == null) return out;
		for (String l : t.widget("Commissions")) {
			Matcher m = KV.matcher(l);
			if (!m.matches()) continue;
			String v = m.group("v").trim();
			out.add(new Commission(m.group("k").trim(), v, v.equalsIgnoreCase("DONE")));
		}
		return out;
	}

	/** Powder amounts ("Mithril: 123,456") → type → amount. */
	public static Map<String, Double> powders(TabInfo t) {
		Map<String, Double> out = new LinkedHashMap<>();
		if (t == null) return out;
		for (String l : t.widget("Powders")) {
			Matcher m = KV.matcher(l);
			if (!m.matches()) continue;
			double v = Fmt.parse(m.group("v").trim().split(" ")[0]);
			if (!Double.isNaN(v)) out.put(m.group("k").trim(), v);
		}
		return out;
	}

	/** Garden visitors waiting. */
	public static List<String> visitors(TabInfo t) {
		List<String> out = new ArrayList<>();
		if (t == null) return out;
		for (String l : t.widget("Visitors")) {
			if (l.startsWith("Next Visitor") || l.contains(":")) continue;
			out.add(l);
		}
		return out;
	}

	/** Garden pests: alive count and the infested plots, from the "Pests" widget. */
	public record Pests(int alive, String plots) {
	}

	public static Pests pests(TabInfo t) {
		if (t == null) return null;
		List<String> w = t.widget("Pests");
		if (w.isEmpty()) return null;
		int alive = 0;
		String plots = null;
		for (String l : w) {
			Matcher m = KV.matcher(l);
			if (!m.matches()) continue;
			String k = m.group("k").trim(), v = m.group("v").trim();
			if (k.equalsIgnoreCase("Alive")) alive = (int) Math.max(0, Fmt.parse(v.split(" ")[0]));
			if (k.toLowerCase().contains("plot")) plots = v;
		}
		return new Pests(alive, plots);
	}

	/** The composter's lines ("Time Left: 12m", "Organic Matter: …"), as shown. */
	public static List<String> composter(TabInfo t) {
		return t == null ? List.of() : t.widget("Composter");
	}
}
