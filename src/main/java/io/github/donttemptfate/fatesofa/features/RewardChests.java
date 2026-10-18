package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.item.Items;
import io.github.donttemptfate.fates.common.text.Strip;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Display;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.core.Tracker;
import io.github.donttemptfate.fatesofa.dungeon.RewardChest;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dungeon and Kuudra reward chests: what's inside is worth, what opening costs (coins and keys) and the profit,
 * beside the chest. You open it yourself.
 */
public final class RewardChests extends Feature {
	final Display display = new Display(this, "rewardchest", 0xFFFFAA00,
			Display.lines("contents", "Contents value", "cost", "Cost to open", "profit", "Profit", "items", "Most valuable items"),
			List.of("contents", "cost", "profit", "items"));
	final Tracker pricing = new Tracker(this, "rewardchest.price");
	final Setting<Integer> itemsShown = setting(Setting.integer("rewardchest.items", "Items listed", 4, 0, 12).group("Display"));
	final Setting<Boolean> essence = setting(Setting.bool("rewardchest.essence", "Count essence", true).group("Display"));
	final Setting<Integer> goodAbove = setting(Setting.integer("rewardchest.good", "Profit shown green above (k coins)", 0, 0, 100_000).group("Display")
			.help("Chests with less profit than this show it in red."));
	final Setting<Boolean> upgrades = setting(Setting.bool("rewardchest.upgrades", "Value items with their upgrades", true).group("Display").advanced());

	public RewardChests() {
		super("reward_chests", "Reward chest profit", "Beside dungeon and Kuudra reward chests: what's inside is worth, what opening it costs "
				+ "(coins and keys) and the profit. You open it yourself.", Category.DUNGEONS, true, Risk.SAFE);
	}

	@Override
	public void init() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
			if (!RewardChest.TITLE.matcher(Strip.clean(acs.getTitle().getString())).matches()) return;
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> guard(() -> draw(acs, g)));
		});
	}

	private Object cachedFor;
	private long cachedAt;
	private Card cached;

	void draw(AbstractContainerScreen<?> s, GuiGraphicsExtractor g) {
		long now = System.currentTimeMillis();
		if (s != cachedFor || now - cachedAt > 500) {
			cachedFor = s;
			cachedAt = now;
			cached = build(s);
		}
		if (cached != null) Trackers.beside(s, g, cached);
	}

	Card build(AbstractContainerScreen<?> s) {
		Ofa o = Ofa.get();
		long now = System.currentTimeMillis();
		double contents = 0;
		RewardChest.Cost cost = null;
		List<Object[]> items = new ArrayList<>();
		for (Slot sl : s.getMenu().slots) {
			if (sl.container instanceof Inventory) continue;
			ItemStack st = sl.getItem();
			if (st.isEmpty()) continue;
			String name = Items.plainName(st);
			List<String> lore = Items.lorePlain(st);
			RewardChest.Cost c = RewardChest.cost(lore);
			if (c != null && (name.contains("Open") || name.contains("Reward") || name.contains("Chest"))) {
				cost = c;
				continue;
			}
			String[] ess = RewardChest.essence(name);
			if (ess != null) {
				if (!on(essence)) continue;
				double v = Trackers.valueOfKey(ess[0], Integer.parseInt(ess[1]), pricing);
				if (!Double.isNaN(v)) {
					contents += v;
					items.add(new Object[] {name, v});
				}
				continue;
			}
			String key = Items.key(st);
			if (key == null) continue;
			double v = on(upgrades) ? o.valuation.total(Items.data(st), key, st.getCount(), now) : Trackers.valueOfKey(key, st.getCount(), pricing);
			if (Double.isNaN(v)) continue;
			contents += v;
			items.add(new Object[] {(st.getCount() > 1 ? st.getCount() + "× " : "") + name, v});
		}
		if (items.isEmpty() && cost == null) return null;
		double costCoins = 0;
		List<String> costParts = new ArrayList<>();
		if (cost != null) {
			costCoins = cost.coins();
			if (cost.coins() > 0) costParts.add(Trackers.num(display, cost.coins()));
			for (String[] it : cost.items()) {
				String key = Trackers.keyForName(it[0]);
				double v = key == null ? Double.NaN : Trackers.valueOfKey(key, Integer.parseInt(it[1]), pricing);
				if (!Double.isNaN(v)) costCoins += v;
				costParts.add(it[0] + (Double.isNaN(v) ? "" : " (" + Trackers.num(display, v) + ")"));
			}
		}
		double profit = contents - costCoins;
		Card c = Trackers.card(display, Strip.clean(s.getTitle().getString()).toUpperCase(), null);
		Card.KeyValues kv = Trackers.rows();
		double fContents = contents;
		RewardChest.Cost fCost = cost;
		display.each(Map.of(
				"contents", () -> Trackers.row(kv, "Inside", Trackers.num(display, fContents), Ui.GOLD),
				"cost", () -> Trackers.row(kv, "Cost", fCost == null ? "?" : costParts.isEmpty() ? "free" : String.join(" + ", costParts), Ui.WHITE),
				"profit", () -> {
					if (fCost != null) Trackers.row(kv, "Profit", (profit >= 0 ? "+" : "") + Trackers.num(display, profit),
							profit >= get(goodAbove) * 1000.0 && profit >= 0 ? Ui.GREEN : Ui.RED);
				},
				"items", () -> {
					items.sort((a, b) -> Double.compare((double) b[1], (double) a[1]));
					for (int i = 0; i < Math.min(get(itemsShown), items.size()); i++) Trackers.row(kv, (String) items.get(i)[0], Trackers.num(display, (double) items.get(i)[1]), Ui.GOLD);
				}));
		c.add(kv);
		return c;
	}
}
