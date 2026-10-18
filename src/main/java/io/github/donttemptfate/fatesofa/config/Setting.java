package io.github.donttemptfate.fatesofa.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One player setting: a key, a label, a short help line, its type and default. Features declare
 * their settings; the settings screen, search, import/export and presets all work from these.
 *
 * <p>The richer types are stored as text and read with {@link Values}: SOUND ("id|volume|pitch"),
 * ISLANDS (comma list, empty = everywhere), LINES (comma list of shown line ids in order), TEMPLATE
 * (text with {placeholders}) and RULES (one rule per line).
 */
public final class Setting<T> {
	public enum Type {
		BOOL, INT, DOUBLE, ENUM, STRING, COLOR, KEY, SOUND, ISLANDS, LINES, TEMPLATE, RULES;

		/** Stored as a string. */
		public boolean textual() {
			return this == STRING || this == KEY || this == SOUND || this == ISLANDS || this == LINES || this == TEMPLATE || this == RULES;
		}
	}

	public final String key;
	public final String label;
	public final String help;
	public final Type type;
	public final T def;
	public final double min;
	public final double max;
	/** ENUM values, or LINES ids (every line the feature can show). */
	public final List<String> options;
	/** Friendly names for {@link #options} (missing ones are prettified ids). */
	public final Map<String, String> optionLabels;
	/** TEMPLATE placeholders, without braces. */
	public final List<String> placeholders;
	public final String featureId;
	public final String keywords;
	/** A collapsible group inside the feature's card ("Display", "Alert"…); null = shown directly. */
	public final String group;
	/** Only shown when the player asks for advanced settings. */
	public final boolean advanced;
	/** Never listed on its own (e.g. a colour's rainbow switch, shown inside the colour picker). */
	public final boolean hidden;
	/** COLOR only: the companion "rainbow" switch, or null when this colour can't be rainbow. */
	public final Setting<Boolean> rainbow;
	/** Longest text value accepted. */
	public final int maxLength;
	/** Explains what's wrong with a text value (shown under it in settings); empty list = fine. */
	public final java.util.function.Function<String, List<String>> checker;

	private Setting(Builder<T> b) {
		this.key = b.key;
		this.label = b.label;
		this.help = b.help == null ? "" : b.help;
		this.type = b.type;
		this.def = b.def;
		this.min = b.min;
		this.max = b.max;
		this.options = b.options == null ? List.of() : List.copyOf(b.options);
		this.optionLabels = b.optionLabels == null ? Map.of() : Map.copyOf(b.optionLabels);
		this.placeholders = b.placeholders == null ? List.of() : List.copyOf(b.placeholders);
		this.featureId = b.featureId;
		this.keywords = b.keywords == null ? "" : b.keywords;
		this.group = b.group;
		this.advanced = b.advanced;
		this.hidden = b.hidden;
		this.checker = b.checker;
		this.maxLength = b.maxLength > 0 ? b.maxLength : b.type == Type.RULES ? 4000 : 512;
		if (b.type == Type.COLOR && b.rainbow) {
			Builder<Boolean> r = bool(b.key + ".rainbow", b.label + " rainbow", false).feature(b.featureId).hidden();
			r.group = b.group;
			this.rainbow = r.build();
		} else {
			this.rainbow = null;
		}
	}

	/** The name shown for an ENUM or LINES option. */
	public String optionLabel(String id) {
		String l = optionLabels.get(id);
		if (l != null) return l;
		String s = id.replace('_', ' ').toLowerCase(Locale.ROOT);
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** True when the search text matches this setting's label, help, group or keywords. */
	public boolean matches(String query) {
		if (query == null || query.isBlank()) return true;
		String hay = (label + " " + help + " " + keywords + " " + key + " " + (group == null ? "" : group)).toLowerCase(Locale.ROOT);
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

	/** An ARGB colour (alpha = transparency). */
	public static Builder<Integer> color(String key, String label, int argb) {
		return new Builder<>(key, label, Type.COLOR, argb);
	}

	/** A key on the keyboard, stored by the game's key name ("key.keyboard.r"; "" = none). */
	public static Builder<String> key(String key, String label) {
		return new Builder<>(key, label, Type.KEY, "");
	}

	/** A sound with volume and pitch ("" = silent). */
	public static Builder<String> sound(String key, String label, String def) {
		return new Builder<>(key, label, Type.SOUND, def);
	}

	/** Islands this applies on (empty = everywhere). */
	public static Builder<String> islands(String key, String label, String def) {
		return new Builder<>(key, label, Type.ISLANDS, def);
	}

	/**
	 * Which lines a card shows and in what order. {@code all}: every line id → its name, in the
	 * default order; {@code shown}: the ids shown by default.
	 */
	public static Builder<String> lines(String key, String label, Map<String, String> all, List<String> shown) {
		Builder<String> b = new Builder<>(key, label, Type.LINES, String.join(",", shown));
		b.options = List.copyOf(all.keySet());
		b.optionLabels = new LinkedHashMap<>(all);
		return b;
	}

	/** Text with {placeholders} filled in when it's used. */
	public static Builder<String> template(String key, String label, String def, String... placeholders) {
		Builder<String> b = new Builder<>(key, label, Type.TEMPLATE, def);
		b.placeholders = List.of(placeholders);
		return b;
	}

	/** A list of the player's own rules, one per line. */
	public static Builder<String> rules(String key, String label, String def) {
		return new Builder<>(key, label, Type.RULES, def);
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
		Map<String, String> optionLabels;
		List<String> placeholders;
		String featureId;
		String keywords;
		String group;
		boolean advanced;
		boolean hidden;
		boolean rainbow;
		int maxLength;
		java.util.function.Function<String, List<String>> checker;

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

		/** Puts the setting in a collapsible group of its feature's card. */
		public Builder<T> group(String group) {
			this.group = group;
			return this;
		}

		public Builder<T> advanced() {
			this.advanced = true;
			return this;
		}

		public Builder<T> hidden() {
			this.hidden = true;
			return this;
		}

		/** COLOR: allow a rainbow (colour-cycling) option. */
		public Builder<T> rainbow() {
			this.rainbow = true;
			return this;
		}

		public Builder<T> labels(Map<String, String> labels) {
			this.optionLabels = labels;
			return this;
		}

		public Builder<T> maxLength(int n) {
			this.maxLength = n;
			return this;
		}

		/** Problems to show under a text value (e.g. rule lines that can't be read). */
		public Builder<T> check(java.util.function.Function<String, List<String>> checker) {
			this.checker = checker;
			return this;
		}

		public String key() {
			return key;
		}

		public Setting<T> build() {
			return new Setting<>(this);
		}
	}
}
