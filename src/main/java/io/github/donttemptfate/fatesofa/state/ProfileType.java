package io.github.donttemptfate.fatesofa.state;

/** Profile game modes. Ironman and Bingo can't use the auction house or bazaar. */
public enum ProfileType {
	NORMAL, IRONMAN, BINGO, STRANDED, UNKNOWN;

	/** From the symbol after the profile name in the tab list ("Profile: Kiwi ♲"). */
	public static ProfileType fromProfileLine(String value) {
		if (value == null) return UNKNOWN;
		if (value.contains("♲")) return IRONMAN;
		if (value.contains("Ⓑ")) return BINGO;
		if (value.contains("☀")) return STRANDED;
		return NORMAL;
	}

	public boolean canTrade() {
		return this == NORMAL || this == UNKNOWN;
	}
}
