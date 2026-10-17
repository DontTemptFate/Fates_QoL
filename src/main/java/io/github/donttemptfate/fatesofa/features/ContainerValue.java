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
		if (!o.state.onSkyBlock() && !Boolean.getBoolean("fatesofa.test")) return null;
		String title = Strip.clean(s.getTitle().getString());
		if (s.getMenu().slots.stream().allMatch(sl -> sl.container instanceof Inventory)) return null; // player inventory
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
		Card c = new Card(0.88f, 90, 170);
		c.add(new Card.Line(List.of(Card.seg("VALUE", Ui.Role.LABEL, Ui.accent())), List.of(Card.seg("TOP " + top.size(), Ui.Role.LABEL, Ui.GRAY))));
		c.add(new Card.Hero(List.of(Card.seg(Fmt.shortNum(total), Ui.Role.HERO_S, Ui.GOLD)), null, 0, null));
		Card.KeyValues kv = new Card.KeyValues(1);
		for (Valuer.Valued v : top) {
			String name = v.name().length() > 22 ? v.name().substring(0, 21) + "…" : v.name();
			kv.row(Card.seg((v.count() > 1 ? v.count() + "× " : "") + name, Ui.Role.SMALL, Ui.WHITE), Card.seg(Fmt.shortNum(v.total()), Ui.Role.SMALL, Ui.GOLD));
		}
		c.add(kv);
		return c;
	}

	void place(AbstractContainerScreen<?> s, GuiGraphicsExtractor g, Card c) {
		AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) s;
		int room = a.fatesofa$leftPos() - 8;
		float scale = Math.min(1f, Math.max(0.5f, room / (float) c.width()));
		int x = Math.max(2, a.fatesofa$leftPos() - Math.round(c.width() * scale) - 6);
		c.draw(g, x, a.fatesofa$topPos(), scale);
	}
}
