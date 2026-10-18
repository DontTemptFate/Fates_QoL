package io.github.donttemptfate.fatesofa.features;

import com.google.gson.JsonObject;
import io.github.donttemptfate.fates.common.item.ItemKey;
import io.github.donttemptfate.fates.common.item.Items;
import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.SkyBlockTime;
import io.github.donttemptfate.fatesofa.activity.TabWidgets;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.ActivitySession;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.game.Alerts;
import io.github.donttemptfate.fatesofa.game.ChatBus;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;
import io.github.donttemptfate.fatesofa.state.Island;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Garden: crops per hour from your farming tool's counter (and what they'd sell to an NPC for), visitors
 * waiting, pests, composter, the next Jacob's contest, and an alert when a pest appears.
 */
public final class GardenFeature extends Feature {
	final io.github.donttemptfate.fatesofa.core.Display display = new io.github.donttemptfate.fatesofa.core.Display(this, "garden", 0xFF55FF55,
			io.github.donttemptfate.fatesofa.core.Display.lines("crops", "Crops per hour", "value", "Crop value per hour", "total", "Crops this session",
					"time", "Time farmed", "visitors", "Visitors", "pests", "Pests", "composter", "Composter", "jacob", "Next Jacob's contest"),
			List.of("crops", "value", "visitors", "pests", "composter", "jacob"));
	final io.github.donttemptfate.fatesofa.core.Tracker tracking = new io.github.donttemptfate.fatesofa.core.Tracker(this, "garden.tracker");
	final Setting<Boolean> onlyGarden = setting(Setting.bool("garden.onlyGarden", "Only on the Garden", true).group("Display")
			.help("Off: the card also shows elsewhere once you have farmed (handy to watch visitors pile up)."));
	final Setting<Integer> visitorNames = setting(Setting.integer("garden.visitorNames", "Visitor names shown", 3, 0, 6).group("Display").advanced());
	final io.github.donttemptfate.fatesofa.core.Alert pest = new io.github.donttemptfate.fatesofa.core.Alert(this, "garden.pestAlert", "Pest alert",
			"PEST!", "Plot {plot}", 0xFF55FF55, "minecraft:block.note_block.pling|1|2", "plot");
	final io.github.donttemptfate.fatesofa.core.Alert visitor = new io.github.donttemptfate.fatesofa.core.Alert(this, "garden.visitorAlert", "New visitor alert",
			false, "{name}", "is visiting ({count} waiting)", 0xFF55FFFF, "minecraft:entity.villager.yes", "name", "count");
	final Setting<Integer> visitorFull = setting(Setting.integer("garden.visitorAlert.atLeast", "Only when at least this many wait", 1, 1, 6)
			.group("New visitor alert"));
	final io.github.donttemptfate.fatesofa.core.Alert jacob = new io.github.donttemptfate.fatesofa.core.Alert(this, "garden.jacobAlert", "Jacob's contest alert",
			false, "Jacob's contest", "in {minutes} min", 0xFFFFFF55, "minecraft:block.note_block.bell", "minutes");
	final Setting<Integer> jacobMinutes = setting(Setting.integer("garden.jacobAlert.minutes", "Minutes before", 2, 1, 20).group("Jacob's contest alert"));
	private java.util.List<String> lastVisitors;
	private long jacobAlertedFor;

	static final Pattern PEST = Pattern.compile(".*Pests? (?:has|have) (?:appeared|spawned) in (?:the )?Plot - (?<plot>[\\w ]+)!?.*");
	static final String[][] CROPS = {{"WHEAT", "WHEAT"}, {"CARROT", "CARROT_ITEM"}, {"POTATO", "POTATO_ITEM"}, {"PUMPKIN", "PUMPKIN"},
			{"MELON", "MELON"}, {"MUSHROOM", "RED_MUSHROOM"}, {"CACTUS", "CACTUS"}, {"CANE", "SUGAR_CANE"}, {"WART", "NETHER_STALK"},
			{"COCO", "INK_SACK-3"}};

	private final ActivitySession session = new ActivitySession(60_000);
	private String toolUuid;
	private long lastCount = -1;
	private String crop;
	private double crops;
	private int tick;

	public GardenFeature() {
		super("garden", "Garden HUD", "Crops per hour (from your tool's counter) and their NPC value, visitors, pests, composter, "
				+ "next Jacob's contest and a pest alert.", Category.FARMING, true, Risk.SAFE);
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			if (++tick % 5 == 0 && o.state.in(Island.GARDEN)) counter(mc);
			if (tick % 20 == 0) second(o);
		}));
		ChatBus.onChat(m -> guard(() -> {
			if (!pest.wanted()) return;
			Pattern p = o.patterns.get("garden.pests-api.chat.pest-spawn");
			Matcher mm = (p != null ? p : PEST).matcher(m.plain());
			if (mm.matches()) {
				String plot = null;
				try {
					plot = mm.group("plot");
				} catch (IllegalArgumentException ignored) {
					// repo pattern without a plot group
				}
				pest.fire(java.util.Map.of("plot", plot == null ? "?" : plot.trim()), o.state.island(), System.currentTimeMillis());
			}
		}));
		Hud.register(new HudElement("garden", "Garden", this, HudPos.at(Anchor.LEFT, 4, 60), 20, this::card, this::sample,
				() -> o.state.in(Island.GARDEN) || (!on(onlyGarden) && session.started())).display(display));
	}

	@Override
	public java.util.List<Action> actions() {
		return java.util.List.of(new Action("Reset counts", this::resetCounts));
	}

	void resetCounts() {
		session.reset();
		crops = 0;
	}

	/** Once a second: tracker upkeep, visitor and Jacob alerts. */
	void second(Ofa o) {
		long now = System.currentTimeMillis();
		tracking.maintain(session, this::resetCounts, o.state.island(), now);
		if (o.state.in(Island.GARDEN) && visitor.wanted()) {
			List<String> v = TabWidgets.visitors(o.state.tab());
			// the first reading after arriving only learns who is already there
			if (lastVisitors != null) {
				for (String name : v) {
					if (!lastVisitors.contains(name) && v.size() >= get(visitorFull))
						visitor.fire(java.util.Map.of("name", name, "count", Integer.toString(v.size())), o.state.island(), now);
				}
			}
			lastVisitors = v;
		} else if (!o.state.in(Island.GARDEN)) {
			lastVisitors = null;
		}
		if (jacob.wanted() && o.state.onSkyBlock()) {
			for (SkyBlockTime.Event e : SkyBlockTime.upcoming(now)) {
				if (!e.name().startsWith("Jacob") || e.running(now)) continue;
				long left = e.startMs() - now;
				if (left > 0 && left <= get(jacobMinutes) * 60_000L && jacobAlertedFor != e.startMs()) {
					jacobAlertedFor = e.startMs();
					jacob.fire(java.util.Map.of("minutes", Long.toString(Math.max(1, Math.round(left / 60_000.0)))), o.state.island(), now);
				}
			}
		}
	}

	void counter(Minecraft mc) {
		if (mc.player == null) return;
		ItemStack held = mc.player.getMainHandItem();
		JsonObject d = Items.data(held);
		if (d == null) return;
		long count = Math.max(ItemKey.num(d, "mined_crops", -1), ItemKey.num(d, "farmed_cultivating", -1));
		if (count < 0) return;
		String uuid = ItemKey.str(d, "uuid");
		long now = System.currentTimeMillis();
		if (uuid == null || !uuid.equals(toolUuid)) {
			toolUuid = uuid;
			lastCount = count;
			crop = cropOf(ItemKey.str(d, "id"));
			return;
		}
		if (count > lastCount) {
			crops += count - lastCount;
			session.add("crops", count - lastCount, now);
		}
		lastCount = count;
	}

	static String cropOf(String toolId) {
		if (toolId == null) return null;
		String t = toolId.toUpperCase(Locale.ROOT);
		for (String[] c : CROPS) if (t.contains(c[0])) return c[1];
		return null;
	}

	Card card() {
		Ofa o = Ofa.get();
		long now = System.currentTimeMillis();
		Card c = Trackers.card(display, "GARDEN", null);
		Card.KeyValues kv = Trackers.rows();
		double ph = session.perHour(crops);
		Double npc = crop == null ? null : o.prices.npcPrice(crop);
		display.each(java.util.Map.of(
				"crops", () -> {
					if (ph > 0 && tracking.showPerHour()) Trackers.row(kv, "Crops / hour", Trackers.num(display, ph), Ui.AQUA);
				},
				"value", () -> {
					if (ph <= 0 || crop == null || !tracking.showProfit()) return;
					double each = tracking.priceMode().equals("default") ? (npc == null ? Double.NaN : npc) : Trackers.valueOfKey(crop, 1, tracking);
					if (!Double.isNaN(each)) Trackers.row(kv, "Value / hour", Trackers.num(display, ph * each), Ui.GOLD);
				},
				"total", () -> {
					if (crops > 0) Trackers.row(kv, "Crops", Trackers.num(display, crops), Ui.WHITE);
				},
				"time", () -> {
					if (session.started() && tracking.showUptime()) Trackers.row(kv, "Farmed for", Fmt.duration(session.activeMs()), Ui.GRAY);
				},
				"visitors", () -> {
					List<String> visitors = TabWidgets.visitors(o.state.tab());
					if (visitors.isEmpty()) return;
					int n = Math.min(get(visitorNames), visitors.size());
					Trackers.row(kv, "Visitors", visitors.size() + (n > 0 ? ": " + String.join(", ", visitors.subList(0, n)) + (visitors.size() > n ? "…" : "") : ""), Ui.WHITE);
				},
				"pests", () -> {
					TabWidgets.Pests pests = TabWidgets.pests(o.state.tab());
					if (pests != null && pests.alive() > 0) Trackers.row(kv, "Pests", pests.alive() + (pests.plots() != null ? " · plots " + pests.plots() : ""), Ui.RED);
				},
				"composter", () -> {
					for (String l : TabWidgets.composter(o.state.tab())) {
						int colon = l.indexOf(':');
						if (colon > 0) Trackers.row(kv, "Composter " + l.substring(0, colon).toLowerCase(Locale.ROOT), l.substring(colon + 1).trim(), Ui.WHITE);
					}
				},
				"jacob", () -> {
					for (SkyBlockTime.Event e : SkyBlockTime.upcoming(now)) {
						if (!e.name().startsWith("Jacob")) continue;
						Trackers.row(kv, "Jacob's contest", e.running(now) ? "now · " + Fmt.duration(e.endMs() - now) + " left" : "in " + Fmt.duration(e.startMs() - now),
								e.running(now) ? Ui.GREEN : Ui.WHITE);
					}
				}));
		if (kv.isEmpty()) return null;
		c.add(kv);
		return c;
	}

	Card sample() {
		Card c = Trackers.card(display, "GARDEN", null);
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Crops / hour", "112k", Ui.AQUA);
		Trackers.row(kv, "NPC value / hour", "336k", Ui.GOLD);
		Trackers.row(kv, "Visitors", "2: Jacob, Rhys", Ui.WHITE);
		Trackers.row(kv, "Pests", "3 · plots 2, 5", Ui.RED);
		Trackers.row(kv, "Jacob's contest", "in 12m 40s", Ui.WHITE);
		c.add(kv);
		return c;
	}
}
