package io.github.donttemptfate.fatesofa.config;

import java.util.List;
import java.util.Locale;

/**
 * One player setting: a key, a label, a short help line, its type and default. Features declare
 * their settings; the settings screen, search, import/export and presets all work from these.
 */
public final class Setting<T> {
	public enum Type { BOOL, INT, DOUBLE, ENUM, STRING, COLOR, KEY }

	public final String key;
	public final String label;
	public final String help;
	public final Type type;
	public final T def;
	public final double min;
	public final double max;
	public final List<String> options; // ENUM values
	public final String featureId;
	public final String keywords;

	private Setting(Builder<T> b) {
		this.key = b.key;
		this.label = b.label;
		this.help = b.help == null ? "" : b.help;
		this.type = b.type;
		this.def = b.def;
		this.min = b.min;
		this.max = b.max;
		this.options = b.options == null ? List.of() : List.copyOf(b.options);
		this.featureId = b.featureId;
		this.keywords = b.keywords == null ? "" : b.keywords;
	}

	/** True when the search text matches this setting's label, help or keywords. */
	public boolean matches(String query) {
		if (query == null || query.isBlank()) return true;
		String hay = (label + " " + help + " " + keywords + " " + key).toLowerCase(Locale.ROOT);
		for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
			if (!hay.contains(word)) return false;
		}
		return true;
	}

	public static Builder<Boolean> bool(String key, String label, boolean def) {
		return new Builder<>(key, label, Type.BOOL, def);
	}

	public static Builder<Integer> integer(String key, String label, int def, int min, int max) {
		Builder<Integer> b = new Builder<>(key, label, Type.INT, def);
		b.min = min;
		b.max = max;
		return b;
	}

	public static Builder<Double> decimal(String key, String label, double def, double min, double max) {
		Builder<Double> b = new Builder<>(key, label, Type.DOUBLE, def);
		b.min = min;
		b.max = max;
		return b;
	}

	public static Builder<String> choice(String key, String label, String def, List<String> options) {
		Builder<String> b = new Builder<>(key, label, Type.ENUM, def);
		b.options = options;
		return b;
	}

	public static Builder<String> text(String key, String label, String def) {
		return new Builder<>(key, label, Type.STRING, def);
	}

	public static Builder<Integer> color(String key, String label, int argb) {
		return new Builder<>(key, label, Type.COLOR, argb);
	}

	public static final class Builder<T> {
		final String key;
		final String label;
		final Type type;
		final T def;
		String help;
		double min;
		double max;
		List<String> options;
		String featureId;
		String keywords;

		Builder(String key, String label, Type type, T def) {
			this.key = key;
			this.label = label;
			this.type = type;
			this.def = def;
		}

		public Builder<T> help(String help) {
			this.help = help;
			return this;
		}

		public Builder<T> feature(String featureId) {
			this.featureId = featureId;
			return this;
		}

		public Builder<T> keywords(String keywords) {
			this.keywords = keywords;
			return this;
		}

		public Setting<T> build() {
			return new Setting<>(this);
		}
	}
}
