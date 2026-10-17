package io.github.donttemptfate.fatesofa.core;

/** Settings are grouped by what the player is doing, not by feature name. */
public enum Category {
	GENERAL("General"),
	HUD("HUD & Look"),
	ITEMS("Items & Tooltips"),
	STORAGE("Storage"),
	PROTECTION("Protection"),
	FARMING("Farming & Garden"),
	MINING("Mining"),
	FISHING("Fishing"),
	FORAGING("Foraging & Hunting"),
	SLAYER("Slayer"),
	COMBAT("Combat"),
	DUNGEONS("Dungeons"),
	KUUDRA("Kuudra"),
	RIFT("Rift"),
	EVENTS("Events"),
	BINGO("Bingo"),
	CHAT("Chat"),
	PLANNER("Planner & Profile"),
	PERFORMANCE("Performance & Diagnostics");

	public final String label;

	Category(String label) {
		this.label = label;
	}
}
