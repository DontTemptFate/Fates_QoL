package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatescommon.text.Fmt;
import io.github.donttemptfate.fatescommon.ui.Card;
import io.github.donttemptfate.fatescommon.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.Election;
import io.github.donttemptfate.fatesofa.activity.SkyBlockTime;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;

import java.util.List;

/** Upcoming SkyBlock events (Jacob's contest, Dark Auction, Spooky Festival, Cult of the Fallen Star…) and the mayor. */
public final class EventTimers extends Feature {
	static final java.util.Map<String, String> EVENTS = io.github.donttemptfate.fatesofa.core.Display.lines(
			"jacob", "Jacob's Farming Contest", "dark_auction", "Dark Auction", "spooky", "Spooky Festival", "jerry", "Season of Jerry",
			"new_year", "New Year Celebration", "zoo", "Traveling Zoo", "hoppity", "Hoppity's Hunt", "cult", "Cult of the Fallen Star");

	final io.github.donttemptfate.fatesofa.core.Display display = new io.github.donttemptfate.fatesofa.core.Display(this, "events",
			io.github.donttemptfate.fatesofa.core.Display.GROUP, 0xFFFF55FF,
			io.github.donttemptfate.fatesofa.core.Display.lines("date", "SkyBlock date", "events", "Upcoming events", "mayor", "Mayor and minister",
					"perks", "Mayor's perks"),
			List.of("date", "events", "mayor"), false);
	final Setting<Integer> count = setting(Setting.integer("events.count", "Events shown", 5, 1, 8).group("Events"));
	final Setting<String> which = setting(Setting.lines("events.which", "Events listed", EVENTS, List.copyOf(EVENTS.keySet())).group("Events")
			.help("Untick events you don't care about."));
	final Setting<Integer> within = setting(Setting.integer("events.within", "Only events within (hours, 0 = any)", 0, 0, 240).group("Events").advanced());
	final Setting<Boolean> running = setting(Setting.bool("events.runningFirst", "Running events first", true).group("Events").advanced());
	final Setting<Boolean> endTime = setting(Setting.bool("events.endTime", "Show time left for running events", true).group("Events").advanced());
	final io.github.donttemptfate.fatesofa.core.Alert soon = new io.github.donttemptfate.fatesofa.core.Alert(this, "events.soon", "Starting soon", false,
			"{event}", "in {minutes} min", 0xFFFFFF55, "minecraft:block.note_block.bell", "event", "minutes");
	final Setting<Integer> soonMinutes = setting(Setting.integer("events.soon.minutes", "Minutes before", 5, 1, 60).group("Starting soon"));
	final Setting<String> soonWhich = setting(Setting.lines("events.soon.which", "Alert for", EVENTS, List.of("jacob", "dark_auction", "cult"))
			.group("Starting soon"));
	private final java.util.Set<String> alerted = new java.util.HashSet<>();
	private Election election;
	private int tick;

	public EventTimers() {
		super("event_timers", "Event timers", "A HUD card with the next SkyBlock events and the current mayor, and an optional alert before an event starts.",
				Category.EVENTS, true, Risk.SAFE);
	}

	static String idOf(String name) {
		for (var e : EVENTS.entrySet()) if (e.getValue().equals(name)) return e.getKey();
		return name;
	}

	@Override
	public void init() {
		election = Ofa.get().election;
		Hud.register(new HudElement("events", "Event timers", this, HudPos.at(Anchor.LEFT, 4, 0), 5, this::card, this::card,
				() -> Ofa.get().state.onSkyBlock()).display(display));
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			if (++tick % 20 != 0 || !soon.wanted() || !Ofa.get().state.onSkyBlock()) return;
			long now = System.currentTimeMillis();
			var wanted = io.github.donttemptfate.fatesofa.config.Values.lines(get(soonWhich), soonWhich.options);
			for (SkyBlockTime.Event e : SkyBlockTime.upcoming(now)) {
				if (e.running(now) || !wanted.contains(idOf(e.name()))) continue;
				long left = e.startMs() - now;
				String key = e.name() + "@" + e.startMs();
				if (left <= get(soonMinutes) * 60_000L && left > 0 && alerted.add(key))
					soon.fire(java.util.Map.of("event", e.name(), "minutes", Long.toString(Math.max(1, Math.round(left / 60_000.0)))), Ofa.get().state.island(), now);
			}
			if (alerted.size() > 50) alerted.clear();
		}));
	}

	List<SkyBlockTime.Event> shown(long now) {
		var wanted = io.github.donttemptfate.fatesofa.config.Values.lines(get(which), which.options);
		List<SkyBlockTime.Event> out = new java.util.ArrayList<>();
		for (SkyBlockTime.Event e : SkyBlockTime.upcoming(now)) {
			if (!wanted.contains(idOf(e.name()))) continue;
			if (get(within) > 0 && !e.running(now) && e.startMs() - now > get(within) * 3_600_000L) continue;
			out.add(e);
		}
		if (!on(running)) out.sort(java.util.Comparator.comparingLong(SkyBlockTime.Event::startMs));
		return out;
	}

	Card card() {
		long now = System.currentTimeMillis();
		Card c = Trackers.card(display, "EVENTS", display.shows("date") ? SkyBlockTime.at(now).label().replaceAll(", Year \\d+", "") : null);
		display.each(java.util.Map.of(
				"events", () -> {
					Card.KeyValues kv = Trackers.rows();
					List<SkyBlockTime.Event> ev = shown(now);
					for (int i = 0; i < Math.min(get(count), ev.size()); i++) {
						SkyBlockTime.Event e = ev.get(i);
						boolean run = e.running(now);
						Trackers.row(kv, e.name(), run ? "now" + (on(endTime) ? " · " + Fmt.duration(e.endMs() - now) + " left" : "") : "in " + Fmt.duration(e.startMs() - now),
								run ? Ui.GREEN : Ui.WHITE);
					}
					if (!kv.isEmpty()) c.add(kv);
				},
				"mayor", () -> {
					Election.Mayor m = election == null ? null : election.mayor(now);
					if (m != null) c.add(Card.Line.of(Card.seg("Mayor " + m.name() + (m.minister() != null ? " · Minister " + m.minister() : ""), Ui.Role.SMALL, Ui.GOLD)));
				},
				"perks", () -> {
					Election.Mayor m = election == null ? null : election.mayor(now);
					if (m != null && m.perks() != null && !m.perks().isEmpty()) c.add(Card.Line.of(Card.seg(String.join(", ", m.perks()), Ui.Role.SMALL, Ui.GRAY)));
				}));
		return c;
	}
}
