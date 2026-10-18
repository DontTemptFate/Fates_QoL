package io.github.donttemptfate.fatesofa.core;

import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.config.Values;
import io.github.donttemptfate.fatesofa.state.Island;

import java.util.List;
import java.util.Map;

/**
 * The "Display" option bundle for a HUD card: where it shows, whether it hides behind menus, its
 * background, accent colour (or rainbow), number style and — when the card has several lines — which
 * lines in what order. One line in a feature gives it all of these.
 */
public final class Display {
	public static final String GROUP = "Display";

	private final Feature f;
	public final Setting<String> showOn;
	public final Setting<Boolean> hideInMenus;
	public final Setting<Integer> background;
	public final Setting<Integer> accent;
	public final Setting<Boolean> compact;
	public final Setting<String> lines;

	/** {@code prefix}: key prefix ("location"); {@code allLines}/{@code shown}: null for single-line cards. */
	public Display(Feature f, String prefix, int accentDefault, Map<String, String> allLines, List<String> shown) {
		this(f, prefix, GROUP, accentDefault, allLines, shown);
	}

	/** With a group name, for features that have more than one card ("Map card", "Score card"). */
	public Display(Feature f, String prefix, String group, int accentDefault, Map<String, String> allLines, List<String> shown) {
		this(f, prefix, group, accentDefault, allLines, shown, true);
	}

	/** {@code numbers}: false for cards without numbers, so no number-style option appears. */
	public Display(Feature f, String prefix, String group, int accentDefault, Map<String, String> allLines, List<String> shown, boolean numbers) {
		this.f = f;
		final String GROUP = group;
		showOn = f.setting(Setting.islands(prefix + ".show", "Show on", "").group(GROUP)
				.help("Islands where this card shows. Nothing picked = everywhere."));
		hideInMenus = f.setting(Setting.bool(prefix + ".hideInMenus", "Hide while a menu is open", false).group(GROUP).advanced());
		background = f.setting(Setting.integer(prefix + ".background", "Background opacity %", 80, 0, 100).group(GROUP));
		accent = f.setting(Setting.color(prefix + ".accent", "Accent colour", accentDefault).rainbow().group(GROUP));
		compact = !numbers ? null : f.setting(Setting.bool(prefix + ".compact", "Short numbers (1.2M)", true).group(GROUP).advanced());
		lines = allLines == null ? null : f.setting(Setting.lines(prefix + ".lines", "Lines and order", allLines, shown).group(GROUP)
				.help("Tick the lines to show; use the arrows to change their order."));
	}

	/** Shown here (island) and now (menu open or not)? */
	public boolean visible(Island here, boolean menuOpen) {
		if (f.store() == null) return true;
		if (menuOpen && f.on(hideInMenus)) return false;
		return Values.allows(f.get(showOn), here);
	}

	/** The card's background opacity, 0–1. */
	public float alpha() {
		return f.store() == null ? 0.8f : f.get(background) / 100f;
	}

	public int accent(long nowMs) {
		if (f.store() == null) return accent.def;
		return Values.color(f.get(accent), f.on(accent.rainbow), nowMs);
	}

	public boolean compact() {
		return compact == null || f.store() == null || f.on(compact);
	}

	/** The lines to show, in order (every default line for single-line cards). */
	public List<String> lines() {
		if (lines == null) return List.of();
		return Values.lines(f.store() == null ? lines.def : f.get(lines), lines.options);
	}

	public boolean shows(String line) {
		return lines == null || lines().contains(line);
	}

	/** Runs each shown line's part in the player's order (parts not shown are skipped). */
	public void each(Map<String, Runnable> parts) {
		if (lines == null) {
			parts.values().forEach(Runnable::run);
			return;
		}
		for (String id : lines()) {
			Runnable r = parts.get(id);
			if (r != null) r.run();
		}
	}

	/** Every line id → name, in default order (for building {@link #lines} maps in one line). */
	public static Map<String, String> lines(String... idName) {
		Map<String, String> m = new java.util.LinkedHashMap<>();
		for (int i = 0; i + 1 < idName.length; i += 2) m.put(idName[i], idName[i + 1]);
		return m;
	}
}
