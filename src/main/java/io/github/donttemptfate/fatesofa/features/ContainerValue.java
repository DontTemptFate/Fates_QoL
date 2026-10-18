package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.item.Items;
import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.text.Strip;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.items.Valuer;
import io.github.donttemptfate.fatesofa.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Opening a chest, backpack, ender chest or reward chest shows what's inside is worth: the top items
 * (how many is the player's choice) and the total, beside the window.
 */
public final class ContainerValue extends Feature {
	static final Pattern STORAGE = Pattern.compile("(?i)^(chest|large chest|ender chest.*|.*backpack.*|storage.*|personal vault|"
			+ "(wood|gold|diamond|emerald|obsidian|bedrock) chest|.*sack.*|minion chest|.* minion .*)$");

	final Setting<Integer> topN = setting(Setting.integer("chestvalue.top", "How many items to list", 5, 1, 20)
			.keywords("top items chest backpack ender"));
	final Setting<String> where = setting(Setting.choice("chestvalue.where", "Show in", "storage", List.of("storage", "every_window"))
			.help("\"Storage\": chests, backpacks, ender chest, sacks, minions, dungeon reward chests. \"Every window\": any menu."));
	final Setting<Integer> minK = setting(Setting.integer("chestvalue.min", "Ignore items worth less than (k coins)", 0, 0, 10_000));
	final io.github.donttemptfate.fatesofa.core.Display display = new io.github.donttemptfate.fatesofa.core.Display(this, "chestvalue", 0xFFFF55FF, io.github.donttemptfate.fatesofa.core.Display.lines("total", "Total value", "items", "Most valuable items", "count", "How many items were valued"),
			List.of("total", "items"));
	final Setting<Integer> nameLength = setting(Setting.integer("chestvalue.nameLength", "Longest item name (letters)", 22, 10, 40).group("Display").advanced());
	final Setting<Boolean> stackCounts = setting(Setting.bool("chestvalue.counts", "Show stack sizes (3×)", true).group("Display").advanced());

	public ContainerValue() {
		super("chest_value", "Chest value", "Shows what a chest, backpack or ender chest page is worth, with its most valuable items.",
				Category.STORAGE, true, Risk.SAFE);
	}

	@Override
	public void init() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> guard(() -> draw(acs, g)));
		});
	}

	private Object cachedFor;
	private long cachedAt;
	private Card cached;

	void draw(AbstractContainerScreen<?> s, GuiGraphicsExtractor g) {
		long t0 = System.currentTimeMillis();
		if (s == cachedFor && t0 - cachedAt < 250) {
			if (cached != null) place(s, g, cached);
			return;
		}
		cachedFor = s;
		cachedAt = t0;
		cached = build(s);
		if (cached != null) place(s, g, cached);
	}

	Card build(AbstractContainerScreen<?> s) {
		Ofa o = Ofa.get();
		if (!o.state.onSkyBlock() && !io.github.donttemptfate.fatesofa.game.Dev.test()) return null;
		String title = Strip.clean(s.getTitle().getString());
		if (s.getMenu().slots.stream().allMatch(sl -> sl.container instanceof Inventory)) return null; // player inventory
		if (StorageOverlay.INSTANCE != null && StorageOverlay.INSTANCE.covers(s)) return null; // the storage panel shows values there
		if (get(where).equals("storage") && !STORAGE.matcher(title).matches()) return null;
		long now = System.currentTimeMillis();
		List<Valuer.Valued> vals = new ArrayList<>();
		for (Slot sl : s.getMenu().slots) {
			if (sl.container instanceof Inventory) continue;
			ItemStack st = sl.getItem();
			if (st.isEmpty()) continue;
			var facts = io.github.donttemptfate.fates.common.item.ItemFacts.of(st);
			Valuer.Valued v = Valuer.value(o.prices, facts.key(), facts.name(), st.getCount(), now);
			if (v != null && v.total() >= get(minK) * 1000.0) vals.add(v);
		}
		if (vals.isEmpty()) return null;
		double total = Valuer.total(vals);
		List<Valuer.Valued> top = Valuer.top(vals, get(topN));
		Card c = new Card(display.alpha(), 90, 200);
		c.add(new Card.Line(List.of(Card.seg("VALUE", Ui.Role.LABEL, display.accent(now))), display.shows("items") ? List.of(Card.seg("TOP " + top.size(), Ui.Role.LABEL, Ui.GRAY)) : List.of()));
		int len = get(nameLength);
		display.each(java.util.Map.of(
				"total", () -> c.add(new Card.Hero(List.of(Card.seg(Trackers.num(display, total), Ui.Role.HERO_S, Ui.GOLD)), null, 0, null)),
				"items", () -> {
					Card.KeyValues kv = new Card.KeyValues(1);
					for (Valuer.Valued v : top) {
						String name = v.name().length() > len ? v.name().substring(0, len - 1) + "…" : v.name();
						kv.row(Card.seg((v.count() > 1 && on(stackCounts) ? v.count() + "× " : "") + name, Ui.Role.SMALL, Ui.WHITE),
								Card.seg(Trackers.num(display, v.total()), Ui.Role.SMALL, Ui.GOLD));
					}
					c.add(kv);
				},
				"count", () -> c.add(Card.Line.of(Card.seg(vals.size() + " items valued", Ui.Role.SMALL, Ui.GRAY)))));
		return c;
	}

	void place(AbstractContainerScreen<?> s, GuiGraphicsExtractor g, Card c) {
		AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) s;
		int reserve = QuickCommandsFeature.INSTANCE == null ? 0 : QuickCommandsFeature.INSTANCE.leftReserve(s);
		int edge = a.fatesofa$leftPos() - reserve;
		int room = edge - 8;
		float scale = Math.min(1f, Math.max(0.5f, room / (float) c.width()));
		int x = Math.max(2, edge - Math.round(c.width() * scale) - 6);
		c.draw(g, x, a.fatesofa$topPos(), scale);
	}
}
