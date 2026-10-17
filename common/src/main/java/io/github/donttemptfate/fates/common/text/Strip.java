package io.github.donttemptfate.fates.common.text;

/**
 * Cleans text read from Hypixel: § formatting codes, and the invisible or decorative characters
 * Hypixel puts into scoreboard lines to keep them unique (emoji, private-use glyphs).
 */
public final class Strip {
	private Strip() {}

	/** Removes §x formatting codes (and a dangling § at the end). */
	public static String codes(String s) {
		if (s == null) return "";
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§') {
				i++; // skip the code character
				continue;
			}
			b.append(c);
		}
		return b.toString();
	}

	/**
	 * Formatting codes removed, plus characters no SkyBlock parser needs: control characters,
	 * private-use glyphs (resource-pack icons), emoji and other supplementary characters, zero-width
	 * marks. Keeps letters, digits, punctuation and the symbols SkyBlock uses as markers
	 * (⏣ ф ❤ ❈ ✎ ☠ ✦ ⸕ ☘ ♲ Ⓑ ⚚ ✯ etc. are all in the Basic Multilingual Plane and kept).
	 */
	public static String clean(String s) {
		String t = codes(s);
		StringBuilder b = new StringBuilder(t.length());
		for (int i = 0; i < t.length(); ) {
			int cp = t.codePointAt(i);
			i += Character.charCount(cp);
			if (cp < 0x20 || cp == 0x7F) continue;
			if (cp >= 0xE000 && cp <= 0xF8FF) continue; // private use (resource pack glyphs)
			if (cp > 0xFFFF) continue; // emoji and other supplementary planes
			if (cp == 0x200B || cp == 0x200C || cp == 0x200D || cp == 0xFEFF || cp == 0x2060) continue;
			b.appendCodePoint(cp);
		}
		return b.toString().replaceAll("\\s+", " ").trim();
	}

	/** Lower-cases and removes everything but letters and digits: "Crystal Hollows" → "crystalhollows". */
	public static String key(String s) {
		return clean(s).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}
}
