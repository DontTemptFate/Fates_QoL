package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatescommon.skyblock.Items;
import io.github.donttemptfate.fatescommon.text.Fmt;
import io.github.donttemptfate.fatescommon.text.LegacyText;
import io.github.donttemptfate.fatescommon.ui.Card;
import io.github.donttemptfate.fatescommon.ui.Ui;
import io.github.donttemptfate.fatesofa.activity.MenuReaders;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Display;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.core.Tracker;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Beside a minion's menu: what its storage is worth, how full it is, fuel time left and its upgrades. Display
 * only; collecting is still your click.
 */
public final class MinionHelper extends Feature {
	/** Buttons and frame items of the minion menu that aren't stored items. */
	static final Set<String> BUTTONS = Set.of("Collect All", "Close", "Pickup Minion", "Ideal Layout", "Next Tier", "Minion Skin Slot",
			"Upgrade Slot", "Fuel", "Automated Shipping", "Quick-Upgrade Minion", "Bonemeal Slot");

	final Display display = new Display(this, "minion", 0xFF55FF55, Display.lines("value", "Storage value", "fill", "How full", "fuel", "Fuel left",
			"upgrades", "Upgrades", "top", "Most valuable stored items"), List.of("value", "fill", "fuel", "upgrades", "top"));
	final Tracker pricing = new Tracker(this, "minion.price");
	final Setting<Integer> topItems = setting(Setting.integer("minion.topItems", "Stored items listed", 3, 0, 10).group("Display"));

	public MinionHelper() {
		super("minion_helper", "Minion helper", "Beside a minion's menu: what its storage is worth, how full it is, fuel time left and its upgrades.",
				Category.TRACKERS, true, Risk.SAFE);
	}

	@Override
	public void init() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
			if (MenuReaders.minion(LegacyText.clean(acs.getTitle().getString())) == null) return;
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
		String[] m = MenuReaders.minion(LegacyText.clean(s.getTitle().getString()));
		if (m == null) return null;
		double value = 0;
		int stored = 0, storageSlots = 0;
		long fuel = -1;
		List<String> upgrades = new ArrayList<>();
		List<Object[]> items = new ArrayList<>();
		for (Slot sl : s.getMenu().slots) {
			if (sl.container instanceof Inventory) continue;
			ItemStack st = sl.getItem();
			String name = st.isEmpty() ? "" : Items.plainName(st);
			List<String> lore = st.isEmpty() ? List.of() : Items.lorePlain(st);
			boolean storageArea = sl.index % 9 >= 3 && sl.index % 9 <= 7 && sl.index / 9 >= 1 && sl.index / 9 <= 4;
			if (storageArea) storageSlots++;
			if (st.isEmpty() || BUTTONS.contains(name) || st.getItem().toString().contains("glass_pane")) continue;
			long t = MenuReaders.timeLeft(lore);
			if (t >= 0) {
				fuel = t;
				continue;
			}
			boolean isUpgrade = lore.stream().anyMatch(l -> l.contains("upgrade") || l.contains("Upgrade") || l.contains("This item can be used as a minion upgrade"));
			if (!storageArea && isUpgrade) {
				upgrades.add(name);
				continue;
			}
			if (!storageArea) continue;
			String key = Items.key(st);
			if (key == null) continue;
			stored++;
			double v = Trackers.valueOfKey(key, st.getCount(), pricing);
			if (!Double.isNaN(v)) {
				value += v;
				items.add(new Object[] {st.getCount(), name, v});
			}
		}
		Card c = Trackers.card(display, "MINION", m[0].toUpperCase() + " " + m[1]);
		Card.KeyValues kv = Trackers.rows();
		double fValue = value;
		int fStored = stored, fSlots = storageSlots;
		long fFuel = fuel;
		display.each(java.util.Map.of(
				"value", () -> Trackers.row(kv, "Storage worth", Trackers.num(display, fValue), Ui.GOLD),
				"fill", () -> {
					if (fSlots > 0) Trackers.row(kv, "Storage", fStored + " / " + fSlots + " slots", fStored >= fSlots ? Ui.RED : Ui.WHITE);
				},
				"fuel", () -> Trackers.row(kv, "Fuel", fFuel < 0 ? "none" : Fmt.duration(fFuel) + " left", fFuel < 0 ? Ui.GRAY : Ui.AQUA),
				"upgrades", () -> {
					if (!upgrades.isEmpty()) Trackers.row(kv, "Upgrades", String.join(", ", upgrades), Ui.WHITE);
				},
				"top", () -> {
					items.sort((a, b) -> Double.compare((double) b[2], (double) a[2]));
					for (int i = 0; i < Math.min(get(topItems), items.size()); i++)
						Trackers.row(kv, items.get(i)[0] + "× " + items.get(i)[1], Trackers.num(display, (double) items.get(i)[2]), Ui.GOLD);
				}));
		c.add(kv);
		c.add(Card.Line.of(Card.seg("Collecting is still your click.", Ui.Role.SMALL, Ui.DARK)));
		return c;
	}
}
