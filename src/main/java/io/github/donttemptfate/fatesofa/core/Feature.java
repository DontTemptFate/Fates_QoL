package io.github.donttemptfate.fatesofa.core;

import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.config.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * One switchable feature. Every hook a feature registers runs through {@link #guard}, so an error is
 * caught, counted and — after three in a minute — switches only this feature off for the session.
 * A disabled feature does no work: its hooks return at once.
 */
public abstract class Feature {
	/**
	 * How a feature relates to Hypixel's rules. {@code SAFE}: display of what the client already
	 * shows, or protecting the player from their own mistakes. {@code HELPER}: community-tolerated
	 * helpers that aren't addressed in Hypixel's written rules (dungeon waypoints, puzzle hints);
	 * off by default, with a note next to the toggle.
	 */
	public enum Risk { SAFE, HELPER }

	public final String id;
	public final String name;
	public final String description;
	public final Category category;
	public final Risk risk;
	public final Setting<Boolean> enabled;
	private final List<Setting<?>> settings = new ArrayList<>();
	private final ErrorBudget errors = new ErrorBudget();
	private Settings store;
	private long nanos;
	private long calls;

	protected Feature(String id, String name, String description, Category category, boolean defaultOn, Risk risk) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.risk = risk;
		String help = description + (risk == Risk.HELPER ? " (Helper: tolerated by the community, not covered by Hypixel's written rules.)" : "");
		this.enabled = Setting.bool("feature." + id, name, defaultOn && risk == Risk.SAFE).help(help).feature(id).build();
	}

	/** Declares a sub-setting of this feature (call from {@link #declare}). */
	protected <T> Setting<T> setting(Setting.Builder<T> b) {
		Setting<T> s = b.feature(id).build();
		settings.add(s);
		return s;
	}

	/** Declares this feature's settings. Called once at start-up. */
	public void declare(Settings store) {
		this.store = store;
		store.declare(enabled);
		for (Setting<?> s : settings) store.declare(s);
	}

	public List<Setting<?>> settings() {
		return List.copyOf(settings);
	}

	protected Settings store() {
		return store;
	}

	protected <T> T get(Setting<T> s) {
		return store.get(s);
	}

	protected boolean on(Setting<Boolean> s) {
		return store != null && store.on(s);
	}

	/** On in settings and not switched off after errors. */
	public boolean active() {
		return store != null && store.on(enabled) && !errors.tripped();
	}

	public boolean failed() {
		return errors.tripped();
	}

	public ErrorBudget errors() {
		return errors;
	}

	/** The player switched it back on: give it a fresh start. */
	public void resetErrors() {
		errors.reset();
	}

	/** Runs {@code r} if the feature is active; any error is caught and counted. */
	public final void guard(Runnable r) {
		if (!active()) return;
		long t = System.nanoTime();
		try {
			r.run();
		} catch (Throwable e) {
			fail(e);
		} finally {
			nanos += System.nanoTime() - t;
			calls++;
		}
	}

	/** Like {@link #guard} with a result; {@code fallback} when inactive or failing. */
	public final <T> T guard(Supplier<T> s, T fallback) {
		if (!active()) return fallback;
		long t = System.nanoTime();
		try {
			return s.get();
		} catch (Throwable e) {
			fail(e);
			return fallback;
		} finally {
			nanos += System.nanoTime() - t;
			calls++;
		}
	}

	void fail(Throwable e) {
		String msg = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
		boolean off = errors.record(System.currentTimeMillis(), msg);
		Features.reportError(this, e, off);
	}

	/** Total time spent in this feature's hooks since the last {@link #takeTiming}, in nanoseconds. */
	public long[] takeTiming() {
		long[] r = {nanos, calls};
		nanos = 0;
		calls = 0;
		return r;
	}

	/** Words the settings search should also match. */
	public Set<String> keywords() {
		return Set.of();
	}

	/** Called once after settings are loaded (register game hooks here). */
	public void init() {
	}
}
