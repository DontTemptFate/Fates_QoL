package io.github.donttemptfate.fatesofa.features;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.donttemptfate.fates.common.item.Items;
import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.ItemFlow;
import io.github.donttemptfate.fatesofa.activity.MenuReaders;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.config.Values;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Display;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Collection progress: read from your collection menus when you open them, then kept up to date with what
 * you gain (pickups and sacks, from the item tracker). Shows progress to the next tier, per hour and the time
 * left. You pick which collections to show.
 */
public final class CollectionsFeature extends Feature {
	record Known(String name, String nextTier, double current, double needed, long readAt, int gainedBefore) {
	}

	final Setting<String> which = setting(Setting.rules("collections.which", "Collections to show", "")
			.help("One collection per line (Wheat, Mithril…). Empty: the ones you gained in the last few minutes.").keywords("collection tier progress"));
	final Setting<Integer> recentMinutes = setting(Setting.integer("collections.recent", "With an empty list: gained in the last (min)", 10, 1, 120));
	final Setting<Integer> max = setting(Setting.integer("collections.max", "Collections shown", 4, 1, 12));
	final Display display = new Display(this, "collections", 0xFFFFFF55,
			Display.lines("bar", "Progress bar", "numbers", "Amount / next tier", "per_hour", "Per hour", "eta", "Time to next tier"),
			List.of("bar", "numbers", "per_hour", "eta"));

	private final Map<String, Known> known = new LinkedHashMap<>();
	private Path file;
	private int tick;

	public CollectionsFeature() {
		super("collections", "Collections tracker", "Progress to the next collection tier (read from your collection menus, then counted live from what you "
				+ "gain), per hour and time left.", Category.TRACKERS, true, Risk.SAFE);
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		file = o.dir.resolve("collections.json");
		load();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			if (++tick % 20 != 0 || !(mc.gui.screen() instanceof AbstractContainerScreen<?> s)) return;
			boolean changed = false;
			for (Slot sl : s.getMenu().slots) {
				if (sl.container instanceof Inventory || sl.getItem().isEmpty()) continue;
				MenuReaders.Collection c = MenuReaders.collection(Items.lorePlain(sl.getItem()));
				if (c == null) continue;
				int before = gained(c.name());
				Known k = new Known(c.name(), c.nextTier(), c.current(), c.needed(), System.currentTimeMillis(), before);
				Known old = known.put(c.name().toLowerCase(Locale.ROOT), k);
				changed |= old == null || old.current() != k.current();
			}
			if (changed) save();
		}));
		Hud.register(new HudElement("collections", "Collections", this, HudPos.at(Anchor.LEFT, 4, 100), 14, this::card, this::sample,
				() -> o.state.onSkyBlock() && !rows().isEmpty()).display(display));
	}

	/** What the item tracker counted for this collection's item this session. */
	static int gained(String name) {
		ItemTracker it = ItemTracker.INSTANCE;
		if (it == null) return 0;
		ItemFlow.Total t = it.flow.total(name);
		return t == null ? 0 : t.amount;
	}

	static ItemFlow.Total total(String name) {
		ItemTracker it = ItemTracker.INSTANCE;
		return it == null ? null : it.flow.total(name);
	}

	List<Known> rows() {
		List<Known> out = new ArrayList<>();
		List<String> want = Values.rules(get(which));
		long now = System.currentTimeMillis();
		if (!want.isEmpty()) {
			for (String w : want) {
				Known k = known.get(w.toLowerCase(Locale.ROOT));
				if (k != null) out.add(k);
			}
		} else {
			for (Known k : known.values()) {
				ItemFlow.Total t = total(k.name());
				if (t != null && t.amount > 0 && now - t.lastAt <= get(recentMinutes) * 60_000L) out.add(k);
			}
		}
		return out.size() > get(max) ? out.subList(0, get(max)) : out;
	}

	Card card() {
		List<Known> rows = rows();
		if (rows.isEmpty()) return null;
		Card c = Trackers.card(display, "COLLECTIONS", null);
		for (Known k : rows) {
			double now = k.current() + Math.max(0, gained(k.name()) - k.gainedBefore());
			double frac = k.needed() > 0 ? Math.min(1, now / k.needed()) : 0;
			ItemFlow.Total t = total(k.name());
			double ph = t == null ? 0 : t.session.perHour(t.amount);
			List<String> parts = new ArrayList<>();
			for (String id : display.lines()) {
				switch (id) {
					case "numbers" -> parts.add(Trackers.num(display, now) + " / " + Trackers.num(display, k.needed()));
					case "per_hour" -> {
						if (ph > 0) parts.add(Trackers.num(display, ph) + "/h");
					}
					case "eta" -> {
						if (ph > 0 && now < k.needed()) parts.add(Fmt.duration((long) ((k.needed() - now) / ph * 3_600_000)));
					}
					default -> {
					}
				}
			}
			c.add(new Card.Line(List.of(Card.seg(k.name() + " → " + k.nextTier(), Ui.Role.SMALL, Ui.WHITE)),
					List.of(Card.seg(Fmt.percent(frac), Ui.Role.SMALL, Ui.GOLD))));
			if (!parts.isEmpty()) c.add(Card.Line.of(Card.seg(String.join(" · ", parts), Ui.Role.SMALL, Ui.GRAY)));
			if (display.shows("bar")) c.add(new Card.Bar(frac, display.accent(System.currentTimeMillis())));
		}
		return c;
	}

	Card sample() {
		Card c = Trackers.card(display, "COLLECTIONS", null);
		c.add(new Card.Line(List.of(Card.seg("Wheat → IX", Ui.Role.SMALL, Ui.WHITE)), List.of(Card.seg("45%", Ui.Role.SMALL, Ui.GOLD))));
		c.add(Card.Line.of(Card.seg("12.3k / 25k · 3.1k/h · 4h 5m", Ui.Role.SMALL, Ui.GRAY)));
		c.add(new Card.Bar(0.45, 0xFFFFFF55));
		return c;
	}

	void load() {
		try {
			if (!Files.isRegularFile(file)) return;
			JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			for (var e : o.entrySet()) {
				JsonObject k = e.getValue().getAsJsonObject();
				known.put(e.getKey(), new Known(k.get("n").getAsString(), k.get("t").getAsString(), k.get("c").getAsDouble(), k.get("m").getAsDouble(),
						k.get("at").getAsLong(), 0));
			}
		} catch (Exception e) {
			known.clear();
		}
	}

	void save() {
		JsonObject o = new JsonObject();
		for (var e : known.entrySet()) {
			JsonObject k = new JsonObject();
			k.addProperty("n", e.getValue().name());
			k.addProperty("t", e.getValue().nextTier());
			k.addProperty("c", e.getValue().current());
			k.addProperty("m", e.getValue().needed());
			k.addProperty("at", e.getValue().readAt());
			o.add(e.getKey(), k);
		}
		Ofa.get().io.execute(() -> {
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, o.toString(), StandardCharsets.UTF_8);
			} catch (Exception ignored) {
				// the menu can be read again next time
			}
		});
	}
}
