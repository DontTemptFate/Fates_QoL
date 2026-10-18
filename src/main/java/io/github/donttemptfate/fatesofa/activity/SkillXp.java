package io.github.donttemptfate.fatesofa.activity;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fatesofa.core.ActivitySession;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill experience from SkyBlock's action bar ("+12.5 Farming (1,234,567/2,000,000)" or "(45.2%)"): XP gained
 * per skill, per hour, progress and the time to the next level. Display only.
 */
public final class SkillXp {
	public static final List<String> SKILLS = List.of("Farming", "Mining", "Combat", "Foraging", "Fishing", "Enchanting", "Alchemy",
			"Carpentry", "Runecrafting", "Social", "Taming", "Hunting");
	static final Pattern GAIN = Pattern.compile("\\+(?<amt>[\\d,.]+) (?<skill>" + String.join("|", SKILLS) + ") \\((?<prog>[^)]+)\\)");
	static final Pattern ABS = Pattern.compile("(?<cur>[\\d,.]+[kKmMbB]?)/(?<max>[\\d,.]+[kKmMbB]?)");
	static final Pattern PCT = Pattern.compile("(?<pct>[\\d.]+)%");

	/** One gain: amount, and progress (current/needed when shown, else percent; NaN when unknown). */
	public record Gain(String skill, double amount, double current, double needed, double percent) {
	}

	public static Gain parse(String actionBar) {
		if (actionBar == null) return null;
		Matcher m = GAIN.matcher(actionBar);
		if (!m.find()) return null;
		double amt = Fmt.parse(m.group("amt"));
		String prog = m.group("prog");
		Matcher a = ABS.matcher(prog);
		if (a.find()) {
			double cur = Fmt.parse(a.group("cur")), max = Fmt.parse(a.group("max"));
			return new Gain(m.group("skill"), amt, cur, max, max > 0 ? cur / max * 100 : Double.NaN);
		}
		Matcher p = PCT.matcher(prog);
		return new Gain(m.group("skill"), amt, Double.NaN, Double.NaN, p.find() ? Double.parseDouble(p.group("pct")) : Double.NaN);
	}

	/** Per-skill session numbers. */
	public static final class Skill {
		public final String name;
		public final ActivitySession session = new ActivitySession(90_000);
		public double gained;
		public Gain last;
		public long lastAt;

		Skill(String name) {
			this.name = name;
		}

		public double perHour() {
			return session.perHour(gained);
		}

		/** Milliseconds to the next level at this hour's rate, or -1 when unknown. */
		public long toLevelMs() {
			if (last == null || Double.isNaN(last.needed()) || Double.isNaN(last.current())) return -1;
			double ph = perHour();
			if (ph <= 0) return -1;
			return (long) ((last.needed() - last.current()) / ph * 3_600_000);
		}
	}

	private final Map<String, Skill> skills = new LinkedHashMap<>();
	/** Any skill's XP: one clock for the whole tracker (resets, idle pause). */
	public final ActivitySession all = new ActivitySession(90_000);
	// the action bar repeats the same gain for a few frames: count each one once
	private String lastText;
	private long lastAt;

	/** Feeds one action bar; returns the gain counted, or null. */
	public synchronized Gain actionBar(String text, long now) {
		Gain g = parse(text);
		if (g == null) return null;
		Matcher m = GAIN.matcher(text);
		String key = m.find() ? m.group() : text;
		if (key.equals(lastText) && now - lastAt < 1500) return null;
		lastText = key;
		lastAt = now;
		Skill s = skills.computeIfAbsent(g.skill(), Skill::new);
		s.gained += g.amount();
		s.session.add("xp", g.amount(), now);
		s.last = g;
		s.lastAt = now;
		all.activity(now);
		return g;
	}

	public synchronized Map<String, Skill> skills() {
		return new LinkedHashMap<>(skills);
	}

	public synchronized void reset() {
		skills.clear();
		lastText = null;
		all.reset();
	}
}
