package io.github.donttemptfate.fatesofa.core;

/**
 * Decides when a feature that keeps failing is switched off for the session: three errors within a
 * minute. A single odd error is logged and forgiven; a feature never crashes the game.
 */
public final class ErrorBudget {
	public static final int MAX_ERRORS = 3;
	public static final long WINDOW_MS = 60_000;

	private final long[] times = new long[MAX_ERRORS];
	private int count;
	private int total;
	private boolean tripped;
	private String lastError;

	/** Records an error; returns true when this error switches the feature off. */
	public synchronized boolean record(long nowMs, String error) {
		total++;
		lastError = error;
		if (tripped) return false;
		times[count % MAX_ERRORS] = nowMs;
		count++;
		if (count >= MAX_ERRORS) {
			long oldest = times[count % MAX_ERRORS];
			if (nowMs - oldest <= WINDOW_MS) {
				tripped = true;
				return true;
			}
		}
		return false;
	}

	public synchronized boolean tripped() {
		return tripped;
	}

	/** The player turned the feature back on: start counting again. */
	public synchronized void reset() {
		tripped = false;
		count = 0;
	}

	public synchronized int total() {
		return total;
	}

	public synchronized String lastError() {
		return lastError;
	}
}
