package io.github.donttemptfate.fatesofa.activity;

import io.github.donttemptfate.fatesofa.core.ActivitySession;
import io.github.donttemptfate.fatesofa.state.ScoreboardInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Slayer session from the sidebar and chat: bosses killed per type, spawn and kill times, failures, drops.
 * Display only.
 */
public final class SlayerTracker {
	public record Kill(String boss, long spawnMs, long killMs) {
	}

	public static final Pattern START = Pattern.compile("^\\s*SLAYER QUEST STARTED!\\s*$");
	public static final Pattern COMPLETE = Pattern.compile("^\\s*SLAYER QUEST COMPLETE!\\s*$");
	public static final Pattern FAILED = Pattern.compile("^\\s*SLAYER QUEST FAILED!\\s*$");

	private Pattern start = START, complete = COMPLETE, failed = FAILED;
	private long questStartAt = -1, bossSpawnAt = -1, lastCompleteAt = -100_000;
	private String boss;
	private final List<Kill> kills = new ArrayList<>();
	private int fails;
	private final Map<String, Integer> drops = new LinkedHashMap<>();
	public final ActivitySession session = new ActivitySession(5 * 60_000);

	public void patterns(Pattern start, Pattern complete, Pattern failed) {
		if (start != null) this.start = start;
		if (complete != null) this.complete = complete;
		if (failed != null) this.failed = failed;
	}

	public synchronized void scoreboard(ScoreboardInfo sb, long now) {
		if (sb == null) return;
		if (sb.slayerBoss() != null) boss = sb.slayerBoss();
		boolean slay = false;
		for (String l : sb.lines()) if (l.contains("Slay the boss!")) slay = true;
		if (slay && bossSpawnAt < 0 && questStartAt >= 0) bossSpawnAt = now;
	}

	public synchronized void chat(String plain, long now) {
		if (start.matcher(plain).matches()) {
			questStartAt = now;
			bossSpawnAt = -1;
			session.activity(now);
			return;
		}
		if (complete.matcher(plain).matches()) {
			if (questStartAt >= 0) {
				long spawn = bossSpawnAt > 0 ? bossSpawnAt - questStartAt : -1;
				long kill = bossSpawnAt > 0 ? now - bossSpawnAt : -1;
				kills.add(new Kill(boss == null ? "Slayer boss" : boss, spawn, kill));
				lastCompleteAt = now;
				session.add("kills", 1, now);
			}
			questStartAt = -1;
			bossSpawnAt = -1;
			return;
		}
		if (failed.matcher(plain).matches()) {
			fails++;
			questStartAt = -1;
			bossSpawnAt = -1;
			return;
		}
		Drops.Drop d = Drops.parse(plain);
		// drops count while a quest runs and for a few seconds after the boss dies (its loot lands then)
		if (d != null && (questStartAt >= 0 || now - lastCompleteAt < 15_000)) {
			drops.merge(d.item(), d.amount(), Integer::sum);
			session.add("drop:" + d.item(), d.amount(), now);
		}
	}


	public synchronized List<Kill> kills() {
		return List.copyOf(kills);
	}

	public synchronized int fails() {
		return fails;
	}

	public synchronized Map<String, Integer> drops() {
		return new LinkedHashMap<>(drops);
	}

	public synchronized String boss() {
		return boss;
	}

	/** Seconds since the boss spawned (while fighting), else -1. */
	public synchronized long fightingFor(long now) {
		return bossSpawnAt > 0 && questStartAt >= 0 ? now - bossSpawnAt : -1;
	}

	public synchronized boolean questActive() {
		return questStartAt >= 0;
	}

	public synchronized double averageKillMs() {
		long n = 0, t = 0;
		for (Kill k : kills) if (k.killMs() > 0) {
			n++;
			t += k.killMs();
		}
		return n == 0 ? -1 : t / (double) n;
	}

	public synchronized void reset() {
		kills.clear();
		drops.clear();
		fails = 0;
		session.reset();
	}
}
