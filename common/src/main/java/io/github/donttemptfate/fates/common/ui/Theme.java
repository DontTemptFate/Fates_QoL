package io.github.donttemptfate.fates.common.ui;

import java.util.List;

/**
 * A colour set for every card, panel and screen. "Fates" is the default (the fatesflipper.com purple);
 * the others are there so nobody is stuck with one look.
 */
public record Theme(String id, String name, int outer, int inner, int bgRgb, int rule, int track, int accent, int bar, boolean chakra) {
	public static final Theme FATES = new Theme("fates", "Fates", 0xFF5000FF, 0xFF28007F, 0x100010, 0xFF2A0B5E, 0xFF1A0A30, 0xFFFF55FF, 0xFFB048FF, true);
	public static final Theme MIDNIGHT = new Theme("midnight", "Midnight", 0xFF2F7BFF, 0xFF123C8A, 0x060B16, 0xFF15305C, 0xFF0B1630, 0xFF55FFFF, 0xFF3FA9FF, true);
	public static final Theme EMBER = new Theme("ember", "Ember", 0xFFFF6A00, 0xFF7F3300, 0x140A04, 0xFF4A2410, 0xFF2A1408, 0xFFFFAA00, 0xFFFF7A30, true);
	public static final Theme VANILLA = new Theme("vanilla", "Vanilla", 0xFF5000FF, 0xFF28007F, 0x100010, 0xFF3A2A6E, 0xFF1A0A30, 0xFFFFFF55, 0xFFAAAAAA, false);

	public static final List<Theme> ALL = List.of(FATES, MIDNIGHT, EMBER, VANILLA);

	public static Theme byId(String id) {
		for (Theme t : ALL) if (t.id.equals(id)) return t;
		return FATES;
	}

	public static List<String> ids() {
		return ALL.stream().map(Theme::id).toList();
	}
}
