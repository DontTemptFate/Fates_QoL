package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatescommon.skyblock.Items;
import io.github.donttemptfate.fatescommon.text.Fmt;
import io.github.donttemptfate.fatescommon.text.LegacyText;
import io.github.donttemptfate.fatescommon.ui.Card;
import io.github.donttemptfate.fatescommon.ui.Ui;
import io.github.donttemptfate.fatesofa.activity.MenuReaders;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.config.Values;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Display;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Chocolate factory: in its menus, the upgrade that gives the most chocolate per second for its cost is
 * outlined, with a card showing the cost and how long it takes to pay for itself. You buy it yourself.
 */
public final class ChocolateFactoryHelper extends Feature {
	final Display display = new Display(this, "chocolate", 0xFFFFAA00, Display.lines("best", "Best upgrade", "payback", "Pays for itself in",
			"cps", "Chocolate per second now"), List.of("best", "payback", "cps"));
	final Setting<Integer> outline = setting(Setting.color("chocolate.outline", "Best upgrade outline", 0xFF55FF55).rainbow().group("Display"));
	final Setting<Boolean> second = setting(Setting.bool("chocolate.second", "Also mark the second best", true).group("Display"));

	public ChocolateFactoryHelper() {
		super("chocolate_factory", "Chocolate factory helper", "In the Chocolate Factory: the upgrade with the best chocolate per second for its cost "
				+ "is outlined, with its cost and payback time. You buy it yourself.", Category.EVENTS, true, Risk.SAFE);
	}

	@Override
	public void init() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
			if (!LegacyText.clean(acs.getTitle().getString()).contains("Chocolate Factory")) return;
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> guard(() -> draw(acs, g)));
		});
	}

	void draw(AbstractContainerScreen<?> s, GuiGraphicsExtractor g) {
		Slot best = null, next = null;
		MenuReaders.Upgrade bestU = null, nextU = null;
		String bestName = null;
		double cps = Double.NaN;
		for (Slot sl : s.getMenu().slots) {
			if (sl.container instanceof Inventory) continue;
			ItemStack st = sl.getItem();
			if (st.isEmpty()) continue;
			List<String> lore = Items.lorePlain(st);
			if (Double.isNaN(cps)) cps = MenuReaders.perSecond(lore);
			MenuReaders.Upgrade u = MenuReaders.upgrade(lore);
			if (u == null) continue;
			if (bestU == null || u.costPerChocolate() < bestU.costPerChocolate()) {
				next = best;
				nextU = bestU;
				best = sl;
				bestU = u;
				bestName = Items.plainName(st);
			} else if (nextU == null || u.costPerChocolate() < nextU.costPerChocolate()) {
				next = sl;
				nextU = u;
			}
		}
		if (best == null) return;
		AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) s;
		int col = Values.color(get(outline), on(outline.rainbow), System.currentTimeMillis());
		Ui.ring(g, a.fatesofa$leftPos() + best.x - 1, a.fatesofa$topPos() + best.y - 1, 18, 18, 2, col);
		if (next != null && on(second)) Ui.ring(g, a.fatesofa$leftPos() + next.x - 1, a.fatesofa$topPos() + next.y - 1, 18, 18, 1, (col & 0x00FFFFFF) | 0x90000000);
		Card c = Trackers.card(display, "CHOCOLATE", null);
		Card.KeyValues kv = Trackers.rows();
		MenuReaders.Upgrade fb = bestU;
		String fn = bestName;
		double fcps = cps;
		display.each(java.util.Map.of(
				"best", () -> Trackers.row(kv, "Best: " + fn, Trackers.num(display, fb.cost()) + " for +" + Fmt.trimmed(fb.gainPerSecond()) + "/s", Ui.GREEN),
				"payback", () -> Trackers.row(kv, "Pays for itself in", Fmt.duration((long) (fb.costPerChocolate() * 1000)), Ui.WHITE),
				"cps", () -> {
					if (!Double.isNaN(fcps)) Trackers.row(kv, "Now", Trackers.num(display, fcps) + " per second", Ui.GOLD);
				}));
		c.add(kv);
		Trackers.beside(s, g, c);
	}
}
