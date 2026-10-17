package io.github.donttemptfate.fatesofa.dungeon;

import io.github.donttemptfate.fatesofa.state.ScoreboardInfo;
import io.github.donttemptfate.fatesofa.state.TabInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The current dungeon run as the player can see it: floor, time, cleared %, secrets, crypts, deaths,
 * puzzles (tab list), and milestones from chat (blood open, watcher done, boss, end). Feeds the score
 * estimate and the run timers. Display only.
 */
public final class DungeonRun {
	static final Pattern SECRETS = Pattern.compile("^Secrets Found: (?<n>\\d+)$");
	static final Pattern SECRETS_PCT = Pattern.compile("^Secrets Found: (?<p>[\\d.]+)%$");
	static final Pattern CRYPTS = Pattern.compile("^Crypts: (?<n>\\d+)");
	static final Pattern DEATHS = Pattern.compile("^(?:Team )?Deaths: \\(?(?<n>\\d+)\\)?");
	static final Pattern PUZZLE = Pattern.compile("^(?<name>[A-Za-z' ]+): \\[(?<s>[✔✖✦])]");
	static final Pattern TIME = Pattern.compile("^Time Elapsed: (?:(?<h>\\d+)h )?(?:(?<m>\\d+)m )?(?<s>\\d+)s");
	public static final Pattern BLOOD_OPEN = Pattern.compile("^The BLOOD DOOR has been opened!$");
	public static final Pattern WATCHER_DONE = Pattern.compile("^\\[BOSS] The Watcher: You have proven yourself\\. You may pass\\.$");
	public static final Pattern BOSS_LINE = Pattern.compile("^\\[BOSS] (?<boss>(?!The Watcher)[^:]+): .*");
	public static final Pattern END = Pattern.compile("^\\s*(?:Master Mode )?The Catacombs - (?:Floor [IVX]+|Entrance)\\s*$");
	public static final Pattern KEY = Pattern.compile("^(?:\\[[^]]+] )?(?<who>\\w+) has obtained (?<key>Wither|Blood) Key!$|^A (?<key2>Wither|Blood) Key was picked up!$");

	public String floor;
	public int clearedPercent = -1;
	public int secretsFound = -1;
	public double secretsPercent = -1;
	public int crypts;
	public int deaths;
	public final Map<String, String> puzzles = new LinkedHashMap<>();
	public long elapsedMs = -1;
	public long startedAt = -1, bloodAt = -1, watcherAt = -1, bossAt = -1, endAt = -1;
	public String bossName;
	public final List<String> keys = new ArrayList<>();

	public void scoreboard(ScoreboardInfo sb, long now) {
		if (sb == null) return;
		if (sb.dungeonFloor() != null) floor = sb.dungeonFloor();
		if (sb.clearedPercent() != null) clearedPercent = sb.clearedPercent();
		for (String l : sb.lines()) {
			Matcher m = TIME.matcher(l);
			if (m.find()) {
				long h = m.group("h") == null ? 0 : Long.parseLong(m.group("h"));
				long mi = m.group("m") == null ? 0 : Long.parseLong(m.group("m"));
				elapsedMs = ((h * 60 + mi) * 60 + Long.parseLong(m.group("s"))) * 1000;
				if (startedAt < 0) startedAt = now - elapsedMs;
			}
		}
	}

	public void tab(TabInfo t) {
		if (t == null) return;
		for (String l : t.lines()) {
			Matcher m;
			if ((m = SECRETS.matcher(l)).find()) secretsFound = Integer.parseInt(m.group("n"));
			else if ((m = SECRETS_PCT.matcher(l)).find()) secretsPercent = Double.parseDouble(m.group("p"));
			else if ((m = CRYPTS.matcher(l)).find()) crypts = Integer.parseInt(m.group("n"));
			else if ((m = DEATHS.matcher(l)).find()) deaths = Integer.parseInt(m.group("n"));
			else if ((m = PUZZLE.matcher(l)).find() && !l.startsWith("Secrets") && !l.startsWith("Crypts")) puzzles.put(m.group("name").trim(), m.group("s"));
		}
	}

	public void chat(String plain, long now) {
		if (BLOOD_OPEN.matcher(plain).matches() && bloodAt < 0) bloodAt = now;
		else if (WATCHER_DONE.matcher(plain).matches() && watcherAt < 0) watcherAt = now;
		else if (END.matcher(plain).matches() && endAt < 0) endAt = now;
		else {
			Matcher b = BOSS_LINE.matcher(plain);
			if (b.matches() && bossAt < 0 && watcherAt > 0) {
				bossAt = now;
				bossName = b.group("boss").trim();
			}
			Matcher k = KEY.matcher(plain);
			if (k.matches()) keys.add(k.group("key") != null ? k.group("key") : k.group("key2"));
		}
	}

	public int failedPuzzles() {
		int n = 0;
		for (String s : puzzles.values()) if (s.equals("✖")) n++;
		return n;
	}

	/** Total secrets in the dungeon, worked out from found ÷ percentage (−1 until both are shown). */
	public int totalSecrets() {
		if (secretsFound < 0 || secretsPercent <= 0) return -1;
		return (int) Math.round(secretsFound / (secretsPercent / 100.0));
	}

	public DungeonScore.Result score(boolean paulEzpz) {
		return DungeonScore.estimate(new DungeonScore.Input(floor, Math.max(0, clearedPercent), Math.max(0, secretsFound), totalSecrets(), deaths,
				false, failedPuzzles(), crypts, false, false, paulEzpz, Math.max(0, elapsedMs), endAt > 0));
	}
}
