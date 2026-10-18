package io.github.donttemptfate.fatesofa.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A tracking session (farming, mining, fishing, slayer…): what was gained and how long the player was
 * actually active. Time only counts while things happen: a gap longer than the idle limit (AFK, a
 * break, another island) isn't counted, so per-hour rates stay honest.
 */
public final class ActivitySession {
	private volatile long idleLimitMs;
	private long activeMs;
	private long lastEventAt = -1;
	private long startedAt = -1;
	private final Map<String, Double> counts = new LinkedHashMap<>();
	private double coins;

	public ActivitySession(long idleLimitMs) {
		this.idleLimitMs = idleLimitMs;
	}

	/** Gaps longer than this stop the clock (from the Tracker bundle's "pause after idle"; 0 = never). */
	public void idleLimit(long ms) {
		idleLimitMs = ms <= 0 ? Long.MAX_VALUE / 4 : ms;
	}

	/** Something happened at {@code now} (a crop broken, a catch, a kill). */
	public synchronized void activity(long now) {
		if (startedAt < 0) startedAt = now;
		if (lastEventAt >= 0) {
			long gap = now - lastEventAt;
			if (gap > 0 && gap <= idleLimitMs) activeMs += gap;
		}
		lastEventAt = now;
	}

	public synchronized void add(String key, double amount, long now) {
		activity(now);
		counts.merge(key, amount, Double::sum);
	}

	public synchronized void addCoins(double amount, long now) {
		activity(now);
		coins += amount;
	}

	/** Stops counting the time since the last event (island change, menu opened). */
	public synchronized void pause() {
		lastEventAt = -1;
	}

	public synchronized void reset() {
		activeMs = 0;
		lastEventAt = -1;
		startedAt = -1;
		counts.clear();
		coins = 0;
	}

	public synchronized long activeMs() {
		return activeMs;
	}

	public synchronized boolean started() {
		return startedAt >= 0;
	}

	public synchronized boolean idle(long now) {
		return lastEventAt < 0 || now - lastEventAt > idleLimitMs;
	}

	public synchronized Map<String, Double> counts() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(counts));
	}

	public synchronized double count(String key) {
		return counts.getOrDefault(key, 0.0);
	}

	public synchronized double coins() {
		return coins;
	}

	/** Amount per active hour; 0 until a minute of active time exists. */
	public synchronized double perHour(double amount) {
		return activeMs < 60_000 ? 0 : amount * 3_600_000.0 / activeMs;
	}
}
