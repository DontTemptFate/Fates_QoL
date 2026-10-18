package io.github.donttemptfate.fatesofa.core;

import io.github.donttemptfate.fatesofa.config.Setting;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * The "Tracker" option bundle: when counts reset, rates per hour, time shown, pause when idle and how
 * money is valued. {@link #shouldReset} gives the feature one place to ask.
 */
public final class Tracker {
	public static final String GROUP = "Tracker";
	public static final List<String> RESETS = List.of("never", "session", "daily", "island_change");
	public static final List<String> PRICES = List.of("default", "bazaar_instant", "bazaar_offer", "npc");

	private final Feature f;
	public final Setting<String> reset;
	public final Setting<Boolean> perHour;
	public final Setting<Boolean> uptime;
	public final Setting<Integer> idlePause;
	public final Setting<String> price;
	public final Setting<Boolean> profit;

	public Tracker(Feature f, String prefix) {
		this.f = f;
		reset = f.setting(Setting.choice(prefix + ".reset", "Reset counts", "session", RESETS).group(GROUP)
				.labels(java.util.Map.of("never", "Never", "session", "Each game session", "daily", "Each day", "island_change", "On island change")));
		perHour = f.setting(Setting.bool(prefix + ".perHour", "Show per hour", true).group(GROUP));
		uptime = f.setting(Setting.bool(prefix + ".uptime", "Show time tracked", true).group(GROUP));
		idlePause = f.setting(Setting.integer(prefix + ".idlePause", "Pause after idle (s)", 60, 0, 900).group(GROUP).advanced()
				.help("Stop the clock when nothing happened for this long (0 = never pause)."));
		price = f.setting(Setting.choice(prefix + ".price", "Value items by", "default", PRICES).group(GROUP).advanced()
				.labels(java.util.Map.of("default", "OFA default", "bazaar_instant", "Bazaar instant sell", "bazaar_offer", "Bazaar sell offer", "npc", "NPC price")));
		profit = f.setting(Setting.bool(prefix + ".profit", "Show coin value", true).group(GROUP));
	}

	String mode() {
		return f.store() == null ? reset.def : f.get(reset);
	}

	/**
	 * Whether counts started at {@code startedMs} should reset now. {@code islandChanged}: the player
	 * just changed island; {@code newSession}: the game just started.
	 */
	public boolean shouldReset(long startedMs, long nowMs, boolean islandChanged, boolean newSession, ZoneId zone) {
		return switch (mode()) {
			case "session" -> newSession;
			case "daily" -> !LocalDate.ofInstant(java.time.Instant.ofEpochMilli(startedMs), zone)
					.equals(LocalDate.ofInstant(java.time.Instant.ofEpochMilli(nowMs), zone));
			case "island_change" -> islandChanged;
			default -> false;
		};
	}

	/** True when the clock should stop: idle for longer than the pause setting. */
	public boolean paused(long lastEventMs, long nowMs) {
		int s = f.store() == null ? idlePause.def : f.get(idlePause);
		return s > 0 && nowMs - lastEventMs > s * 1000L;
	}
}
