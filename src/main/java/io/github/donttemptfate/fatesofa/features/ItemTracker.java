package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.item.Items;
import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.ItemFlow;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.config.Values;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Display;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.core.Tracker;
import io.github.donttemptfate.fatesofa.game.ChatBus;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What you pick up and what goes into your sacks: a short log ("+64 Enchanted Diamond") and a tracker for the
 * items you choose (gained, per hour, value). Players pick what to track; nothing is tracked they didn't ask for
 * beyond the short log.
 */
public final class ItemTracker extends Feature {
	public static ItemTracker INSTANCE;
	static final String LOG = "Pickup log", TRACKED = "Tracked items";

	final Setting<String> sources = setting(Setting.lines("itemtracker.sources", "Count items from", Display.lines("inventory", "Picked up into your inventory",
			"sacks", "Added to your sacks"), List.of("inventory", "sacks")));
	final Setting<Boolean> showLog = setting(Setting.bool("itemtracker.log", "Show the pickup log", true).group(LOG));
	final Display logDisplay = new Display(this, "itemtracker.logcard", LOG, 0xFF55FF55, null, null, true);
	final Setting<Integer> logSeconds = setting(Setting.integer("itemtracker.log.seconds", "Keep each line for (s)", 8, 2, 60).group(LOG));
	final Setting<Integer> logLines = setting(Setting.integer("itemtracker.log.lines", "Lines shown", 6, 1, 20).group(LOG));
	final Setting<Boolean> logValue = setting(Setting.bool("itemtracker.log.value", "Show each line's value", true).group(LOG));
	final Setting<Integer> logMin = setting(Setting.integer("itemtracker.log.min", "Hide pickups worth less than (coins)", 0, 0, 1_000_000).group(LOG).advanced());

	final Setting<String> items = setting(Setting.rules("itemtracker.items", "Items to track", "").group(TRACKED)
			.help("One item per line, by name (Enchanted Carrot) or id (ENCHANTED_CARROT). Leave empty to use \"Also list the top…\" below.")
			.keywords("track items farming drops loot"));
	final Setting<Integer> topN = setting(Setting.integer("itemtracker.top", "Also list the top items gained (by value)", 0, 0, 15).group(TRACKED)
			.help("0 = only the items you listed above."));
	final Display trackedDisplay = new Display(this, "itemtracker.card", TRACKED, 0xFFFFAA00,
			Display.lines("amount", "Amount gained", "per_hour", "Per hour", "value", "Value", "total", "Total value"), List.of("amount", "per_hour", "value", "total"));
	final Tracker tracking = new Tracker(this, "itemtracker.tracker");

	public final ItemFlow flow = new ItemFlow();
	private Map<String, Integer> lastInv;
	private final Map<String, String> names = new HashMap<>();
	private int tick;

	public ItemTracker() {
		super("item_tracker", "Item pickups & tracker", "A short log of what you pick up or put in your sacks, and a tracker for the items you choose: "
				+ "gained, per hour and value.", Category.TRACKERS, true, Risk.SAFE);
		INSTANCE = this;
	}

	@Override
	public List<Action> actions() {
		return List.of(new Action("Reset counts", flow::reset));
	}

	boolean from(String source) {
		return Values.lines(get(sources), sources.options).contains(source);
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		ChatBus.onChat(m -> guard(() -> {
			if (!from("sacks") || !ItemFlow.SACKS_MSG.matcher(m.plain()).matches()) return;
			for (String hover : hovers(m.component()))
				for (ItemFlow.Change c : ItemFlow.sackLines(hover, m.atMs())) flow.add(new ItemFlow.Change(c.name(), Trackers.keyForName(c.name()), c.amount(), "sacks", c.atMs()));
		}));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			if (++tick % 10 != 0) return;
			if (tick % 20 == 0) tracking.maintain(flow.session, flow::reset, o.state.island(), System.currentTimeMillis());
			scanInventory(mc);
		}));
		o.state.onIslandChange(i -> lastInv = null);
		Hud.register(new HudElement("pickup_log", "Pickup log", this, HudPos.at(Anchor.BOTTOM_RIGHT, -4, -60), 12, this::logCard, this::sampleLog,
				() -> on(showLog) && o.state.onSkyBlock()).display(logDisplay));
		Hud.register(new HudElement("item_tracker", "Tracked items", this, HudPos.at(Anchor.RIGHT, -4, 110), 16, this::trackedCard, this::sampleTracked,
				() -> o.state.onSkyBlock()).display(trackedDisplay));
	}

	/** Hover texts anywhere in a chat line (the sacks message keeps its item list there). */
	static List<String> hovers(Component c) {
		List<String> out = new ArrayList<>();
		for (Component part : c.toFlatList()) {
			if (!(part.getStyle().getHoverEvent() instanceof HoverEvent.ShowText st)) continue;
			// clean line by line: the item list is one line per item
			StringBuilder b = new StringBuilder();
			for (String line : st.value().getString().split("\n")) b.append(io.github.donttemptfate.fates.common.text.Strip.clean(line)).append('\n');
			out.add(b.toString());
		}
		return out;
	}

	/** Compares the inventory with half a second ago; only while no menu is open (moving items from a chest isn't a pickup). */
	void scanInventory(Minecraft mc) {
		if (!from("inventory") || mc.player == null || mc.gui.screen() != null || !(Ofa.get().state.onSkyBlock() || io.github.donttemptfate.fatesofa.game.Dev.test())) {
			lastInv = null;
			return;
		}
		Map<String, Integer> now = new HashMap<>();
		var inv = mc.player.getInventory();
		for (int i = 0; i < 36; i++) {
			ItemStack st = inv.getItem(i);
			if (st.isEmpty()) continue;
			String key = Items.key(st);
			if (key == null) continue;
			now.merge(key, st.getCount(), Integer::sum);
			names.putIfAbsent(key, Items.plainName(st));
		}
		long t = System.currentTimeMillis();
		for (var e : ItemFlow.grew(lastInv, now).entrySet()) flow.add(new ItemFlow.Change(names.getOrDefault(e.getKey(), e.getKey()), e.getKey(), e.getValue(), "inventory", t));
		lastInv = now;
	}

	double valueOf(ItemFlow.Change c) {
		String key = c.key() != null ? c.key() : Trackers.keyForName(c.name());
		return key == null ? Double.NaN : Trackers.valueOfKey(key, c.amount(), tracking);
	}

	Card logCard() {
		long now = System.currentTimeMillis();
		List<ItemFlow.Change> recent = flow.recent(now, get(logSeconds) * 1000L, 50);
		Card c = new Card(logDisplay.alpha(), 90, 200);
		Card.KeyValues kv = Trackers.rows();
		int shown = 0;
		for (ItemFlow.Change ch : recent) {
			if (shown >= get(logLines)) break;
			double v = valueOf(ch);
			if (get(logMin) > 0 && (Double.isNaN(v) || v < get(logMin))) continue;
			Trackers.row(kv, "+" + Fmt.full(ch.amount()) + " " + ch.name(), on(logValue) && !Double.isNaN(v) ? Trackers.num(logDisplay, v) : "",
					ch.source().equals("sacks") ? Ui.AQUA : Ui.GREEN);
			shown++;
		}
		if (kv.isEmpty()) return null;
		c.add(kv);
		return c;
	}

	/** The rows the tracker card shows: the player's list first, then the top gained items if asked. */
	List<ItemFlow.Total> trackedRows() {
		List<ItemFlow.Total> out = new ArrayList<>();
		for (String want : Values.rules(get(items))) {
			ItemFlow.Total t = flow.total(want);
			if (t == null) {
				// listed by id: find it by key
				for (ItemFlow.Total x : flow.totals()) if (want.equalsIgnoreCase(x.key)) t = x;
			}
			out.add(t != null ? t : placeholder(want));
		}
		if (get(topN) > 0) {
			List<ItemFlow.Total> all = flow.totals();
			all.removeIf(out::contains);
			all.sort((a, b) -> Double.compare(totalValue(b), totalValue(a)));
			out.addAll(all.subList(0, Math.min(get(topN), all.size())));
		}
		return out;
	}

	private final Map<String, ItemFlow.Total> placeholders = new HashMap<>();

	ItemFlow.Total placeholder(String name) {
		return placeholders.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> {
			ItemFlow f = new ItemFlow();
			f.add(new ItemFlow.Change(name, null, 0, "none", 0));
			return f.total(name);
		});
	}

	double totalValue(ItemFlow.Total t) {
		String key = t.key != null ? t.key : Trackers.keyForName(t.name);
		return key == null ? 0 : nz(Trackers.valueOfKey(key, t.amount, tracking));
	}

	static double nz(double v) {
		return Double.isNaN(v) ? 0 : v;
	}

	Card trackedCard() {
		List<ItemFlow.Total> rows = trackedRows();
		if (rows.isEmpty()) return null;
		Card c = Trackers.card(trackedDisplay, "TRACKED ITEMS", null);
		Card.KeyValues kv = Trackers.rows();
		double total = 0;
		for (ItemFlow.Total t : rows) {
			List<String> parts = new ArrayList<>();
			double v = totalValue(t);
			total += v;
			for (String id : trackedDisplay.lines()) {
				switch (id) {
					case "amount" -> parts.add(Trackers.num(trackedDisplay, t.amount));
					case "per_hour" -> {
						double ph = t.session.perHour(t.amount);
						if (ph > 0 && tracking.showPerHour()) parts.add(Trackers.num(trackedDisplay, ph) + "/h");
					}
					case "value" -> {
						if (v > 0 && tracking.showProfit()) parts.add(Trackers.num(trackedDisplay, v) + " coins");
					}
					default -> {
					}
				}
			}
			Trackers.row(kv, t.name, String.join(" · ", parts), t.amount > 0 ? Ui.WHITE : Ui.GRAY);
		}
		if (trackedDisplay.shows("total") && total > 0 && tracking.showProfit()) {
			double ph = flow.session.perHour(total);
			Trackers.row(kv, "Total", Trackers.num(trackedDisplay, total) + (ph > 0 && tracking.showPerHour() ? " · " + Trackers.num(trackedDisplay, ph) + "/h" : ""), Ui.GOLD);
		}
		if (tracking.showUptime() && flow.session.started()) Trackers.row(kv, "Tracked for", Fmt.duration(flow.session.activeMs()), Ui.GRAY);
		c.add(kv);
		return c;
	}

	Card sampleLog() {
		Card c = new Card(logDisplay.alpha(), 90, 200);
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "+64 Enchanted Diamond", "11k", Ui.GREEN);
		Trackers.row(kv, "+1,280 Enchanted Carrot", "450k", Ui.AQUA);
		c.add(kv);
		return c;
	}

	Card sampleTracked() {
		Card c = Trackers.card(trackedDisplay, "TRACKED ITEMS", null);
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Enchanted Carrot", "12.8k · 41k/h · 4.5M coins", Ui.WHITE);
		Trackers.row(kv, "Total", "4.5M · 14M/h", Ui.GOLD);
		c.add(kv);
		return c;
	}
}
