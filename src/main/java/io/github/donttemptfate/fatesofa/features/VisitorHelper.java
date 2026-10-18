package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.item.Items;
import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.VisitorOffer;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.mixin.AbstractContainerScreenAccessor;
import io.github.donttemptfate.fatesofa.state.Island;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/** Garden visitors: what their request costs at the bazaar, what the item rewards are worth, and coins per copper. */
public final class VisitorHelper extends Feature {
	public VisitorHelper() {
		super("visitor_helper", "Visitor offer value", "When a garden visitor's offer is open: cost of the items wanted (bazaar), "
				+ "value of item rewards, and coins per copper.", Category.FARMING, true, Risk.SAFE);
	}

	@Override
	public void init() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> guard(() -> draw(acs, g)));
		});
	}

	void draw(AbstractContainerScreen<?> s, GuiGraphicsExtractor g) {
		if (!Ofa.get().state.in(Island.GARDEN) && !io.github.donttemptfate.fatesofa.game.Dev.test()) return;
		VisitorOffer offer = null;
		for (Slot sl : s.getMenu().slots) {
			if (sl.container instanceof Inventory) continue;
			if (Items.plainName(sl.getItem()).equals("Accept Offer")) offer = VisitorOffer.parse(Items.lorePlain(sl.getItem()));
		}
		if (offer == null) return;
		long now = System.currentTimeMillis();
		double cost = 0;
		boolean unpriced = false;
		Card c = Trackers.card("VISITOR OFFER", null);
		Card.KeyValues kv = Trackers.rows();
		for (VisitorOffer.Line l : offer.wanted()) {
			String key = Trackers.keyForName(l.name());
			var q = key == null ? null : Ofa.get().prices.bazaarQuote(key, now);
			double each = q != null && q.instantBuy() > 0 ? q.instantBuy() : Double.NaN;
			if (Double.isNaN(each)) unpriced = true;
			else cost += each * l.amount();
			Trackers.row(kv, Fmt.shortNum(l.amount()) + "× " + l.name(), Double.isNaN(each) ? "—" : Fmt.shortNum(each * l.amount()), Ui.WHITE);
		}
		double rewards = 0;
		for (VisitorOffer.Line l : offer.rewards()) {
			double v = Trackers.valueOf(l.name(), (int) l.amount());
			if (!Double.isNaN(v)) rewards += v;
			Trackers.row(kv, "Reward: " + l.name(), Double.isNaN(v) ? "—" : Fmt.shortNum(v), Ui.GREEN);
		}
		Trackers.row(kv, "Cost to buy", Fmt.shortNum(cost) + (unpriced ? " + unpriced" : ""), Ui.GOLD);
		if (offer.copper() > 0) {
			Trackers.row(kv, "Copper", Fmt.shortNum(offer.copper()), Ui.ORANGE);
			double net = Math.max(0, cost - rewards);
			Trackers.row(kv, "Coins per copper", Fmt.shortNum(net / offer.copper()), Ui.AQUA);
		}
		c.add(kv);
		AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) s;
		int room = a.fatesofa$leftPos() - 8;
		float scale = Math.min(1f, Math.max(0.5f, room / (float) c.width()));
		c.draw(g, Math.max(2, a.fatesofa$leftPos() - Math.round(c.width() * scale) - 6), a.fatesofa$topPos(), scale);
	}

	static List<String> none() {
		return List.of();
	}
}
