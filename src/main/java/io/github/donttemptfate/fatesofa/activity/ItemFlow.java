package io.github.donttemptfate.fatesofa.activity;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fatesofa.core.ActivitySession;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Items coming in: what entered your sacks (from the "[Sacks]" message's hover text) and what appeared in your
 * inventory (from comparing it tick to tick). Keeps a short log and per-item totals per hour. Display only.
 */
public final class ItemFlow {
	/** One change: +amount of an item, from where. {@code key} may be null for sack lines (names only). */
	public record Change(String name, String key, int amount, String source, long atMs) {
	}

	static final Pattern SACK_LINE = Pattern.compile("^\\s*(?<sign>[+-])(?<n>[\\d,]+) (?<name>.+?) \\((?<sack>[^)]*Sacks?)\\)\\s*$");
	public static final Pattern SACKS_MSG = Pattern.compile("^\\[Sacks] [+-][\\d,]+ items?.*");

	/** The "+N Item (X Sack)" lines of a sacks hover text (added items only). */
	public static List<Change> sackLines(String hover, long now) {
		List<Change> out = new ArrayList<>();
		if (hover == null) return out;
		for (String line : hover.split("\n")) {
			Matcher m = SACK_LINE.matcher(line);
			if (!m.matches() || !m.group("sign").equals("+")) continue;
			out.add(new Change(m.group("name").trim(), null, (int) Fmt.parse(m.group("n")), "sacks", now));
		}
		return out;
	}

	/** What grew between two inventory snapshots (key → count); keys that shrank or vanished are ignored. */
	public static Map<String, Integer> grew(Map<String, Integer> before, Map<String, Integer> after) {
		Map<String, Integer> out = new LinkedHashMap<>();
		if (before == null) return out;
		for (var e : after.entrySet()) {
			int d = e.getValue() - before.getOrDefault(e.getKey(), 0);
			if (d > 0) out.put(e.getKey(), d);
		}
		return out;
	}

	/** Totals for one item. */
	public static final class Total {
		public final String name;
		public String key;
		public int amount;
		public long lastAt;
		public final ActivitySession session = new ActivitySession(5 * 60_000);

		Total(String name) {
			this.name = name;
		}
	}

	private final Deque<Change> log = new ArrayDeque<>();
	private final Map<String, Total> totals = new HashMap<>();
	public final ActivitySession session = new ActivitySession(5 * 60_000);

	public synchronized void add(Change c) {
		log.addFirst(c);
		while (log.size() > 50) log.removeLast();
		Total t = totals.computeIfAbsent(c.name().toLowerCase(java.util.Locale.ROOT), k -> new Total(c.name()));
		if (c.key() != null) t.key = c.key();
		t.amount += c.amount();
		t.lastAt = Math.max(t.lastAt, c.atMs());
		t.session.add("n", c.amount(), c.atMs());
		session.activity(c.atMs());
	}

	/** Changes newer than {@code maxAgeMs}, newest first, at most {@code n}. */
	public synchronized List<Change> recent(long now, long maxAgeMs, int n) {
		List<Change> out = new ArrayList<>();
		for (Change c : log) {
			if (now - c.atMs() > maxAgeMs || out.size() >= n) break;
			out.add(c);
		}
		return out;
	}

	public synchronized Total total(String name) {
		return totals.get(name.toLowerCase(java.util.Locale.ROOT));
	}

	public synchronized List<Total> totals() {
		return new ArrayList<>(totals.values());
	}

	public synchronized void reset() {
		log.clear();
		totals.clear();
		session.reset();
	}
}
