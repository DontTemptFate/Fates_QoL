package io.github.donttemptfate.fates.common.item;

import io.github.donttemptfate.fates.common.text.Strip;

import java.util.Locale;

/** SkyBlock rarities, their colours, and reading them from an item's last lore line. */
public enum Rarity {
	COMMON(0xFFFFFF, 'f'),
	UNCOMMON(0x55FF55, 'a'),
	RARE(0x5555FF, '9'),
	EPIC(0xAA00AA, '5'),
	LEGENDARY(0xFFAA00, '6'),
	MYTHIC(0xFF55FF, 'd'),
	DIVINE(0x55FFFF, 'b'),
	SPECIAL(0xFF5555, 'c'),
	VERY_SPECIAL(0xFF5555, 'c'),
	ULTIMATE(0xAA0000, '4'),
	ADMIN(0xAA0000, '4');

	public final int rgb;
	public final char code;

	Rarity(int rgb, char code) {
		this.rgb = rgb;
		this.code = code;
	}

	/**
	 * Reads the rarity from a lore line like "§6§lLEGENDARY SWORD" or "§d§l§ka§r §d§lMYTHIC ACCESSORY
	 * §d§l§ka". Null when the line isn't a rarity line.
	 */
	public static Rarity fromLine(String line) {
		String t = Strip.clean(line).toUpperCase(Locale.ROOT).trim();
		// recombobulated items wrap the line in an obfuscated "a" on each side
		if (t.startsWith("A ")) t = t.substring(2);
		if (t.endsWith(" A")) t = t.substring(0, t.length() - 2);
		t = t.trim();
		if (t.startsWith("VERY SPECIAL")) return VERY_SPECIAL;
		for (Rarity r : values()) {
			String name = r.name().replace('_', ' ');
			if (t.equals(name) || t.startsWith(name + " ")) return r;
		}
		return null;
	}

	public String label() {
		return name().replace('_', ' ');
	}
}
