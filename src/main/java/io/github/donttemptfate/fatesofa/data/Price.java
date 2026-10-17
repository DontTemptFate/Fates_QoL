package io.github.donttemptfate.fatesofa.data;

/** A value for one item and where it came from. Shown with its source; never presented as a flip. */
public record Price(double value, Kind kind, long atMs) {
	public enum Kind {
		BAZAAR_INSTANT_SELL("bazaar sell"),
		BAZAAR_SELL_OFFER("bazaar offer"),
		BAZAAR_INSTANT_BUY("bazaar buy"),
		AH_LOWEST_BIN("lowest BIN"),
		AH_MEDIAN("AH median"),
		NPC("NPC");

		public final String label;

		Kind(String label) {
			this.label = label;
		}
	}
}
