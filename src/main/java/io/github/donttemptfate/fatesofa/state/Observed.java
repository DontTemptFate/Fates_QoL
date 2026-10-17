package io.github.donttemptfate.fatesofa.state;

/**
 * A value together with where it came from and when. Every piece of game state carries this, so a
 * display can say "from the scoreboard, 2 s ago" or show a value as stale instead of pretending.
 */
public record Observed<T>(T value, Source source, long atMs) {
	public enum Source { MOD_API, SCOREBOARD, TAB_LIST, CHAT, ITEM, MENU, REPO, PUBLIC_API, DERIVED }

	public long ageMs(long nowMs) {
		return Math.max(0, nowMs - atMs);
	}

	public boolean fresh(long nowMs, long maxAgeMs) {
		return ageMs(nowMs) <= maxAgeMs;
	}
}
