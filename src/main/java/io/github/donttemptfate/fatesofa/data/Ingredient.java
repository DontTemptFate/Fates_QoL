package io.github.donttemptfate.fatesofa.data;

/** "ITEM:count" as the NEU repo writes it. {@code SKYBLOCK_COIN} means coins. */
public record Ingredient(String key, double count) {
	public static final String COINS = "SKYBLOCK_COIN";

	public static Ingredient parse(String s) {
		if (s == null) return null;
		String t = s.trim();
		if (t.isEmpty()) return null;
		int c = t.lastIndexOf(':');
		if (c > 0) {
			try {
				return new Ingredient(t.substring(0, c), Double.parseDouble(t.substring(c + 1)));
			} catch (NumberFormatException e) {
				// a ':' that is part of the name
			}
		}
		return new Ingredient(t, 1);
	}

	public boolean coins() {
		return COINS.equals(key);
	}
}
