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
	final io.github.donttemptfate.fatesofa.core.Display display = new io.github.donttemptfate.fatesofa.core.Display(this, "visitor", 0xFF55FF55, io.github.donttemptfate.fatesofa.core.Display.lines("wanted", "Items wanted", "rewards", "Rewards", "cost", "Cost to buy",
			"copper", "Copper", "per_copper", "Coins per copper"), java.util.List.of("wanted", "rewards", "cost", "copper", "per_copper"));
	final io.github.donttemptfate.fatesofa.config.Setting<String> buyBy = setting(io.github.donttemptfate.fatesofa.config.Setting
			.choice("visitor.buyBy", "Price items at", "instant_buy", java.util.List.of("instant_buy", "buy_order"))
			.labels(java.util.Map.of("instant_buy", "Bazaar instant buy", "buy_order", "Bazaar buy order")));
	final io.github.donttemptfate.fatesofa.config.Setting<Integer> goodBelow = setting(io.github.donttemptfate.fatesofa.config.Setting
			.integer("visitor.goodBelow", "Coins per copper counted as good (green) below", 1500, 0, 20000)
			.help("Offers cheaper than this per copper show green; dearer ones show red. 0 = no colouring."));

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
		double cost = 0, rewards = 0;
		boolean unpriced = false;
		Card c = Trackers.card(display, "VISITOR OFFER", null);
		Card.KeyValues kv = Trackers.rows();
		java.util.List<Object[]> wanted = new java.util.ArrayList<>();
		for (VisitorOffer.Line l : offer.wanted()) {
			String key = Trackers.keyForName(l.name());
			var q = key == null ? null : Ofa.get().prices.bazaarQuote(key, now);
			double each = q == null ? Double.NaN : get(buyBy).equals("buy_order") ? q.instantSell() : q.instantBuy();
			if (!(each > 0)) each = Double.NaN;
			if (Double.isNaN(each)) unpriced = true;
			else cost += each * l.amount();
			wanted.add(new Object[] {l, each});
		}
		java.util.List<Object[]> rewardList = new java.util.ArrayList<>();
		for (VisitorOffer.Line l : offer.rewards()) {
			double v = Trackers.valueOf(l.name(), (int) l.amount());
			if (!Double.isNaN(v)) rewards += v;
			rewardList.add(new Object[] {l, v});
		}
		double fCost = cost, fRewards = rewards;
		boolean fUnpriced = unpriced;
		VisitorOffer fo = offer;
		display.each(java.util.Map.of(
				"wanted", () -> {
					for (Object[] w : wanted) {
						VisitorOffer.Line l = (VisitorOffer.Line) w[0];
						double each = (double) w[1];
						Trackers.row(kv, Trackers.num(display, l.amount()) + "× " + l.name(), Double.isNaN(each) ? "—" : Trackers.num(display, each * l.amount()), Ui.WHITE);
					}
				},
				"rewards", () -> {
					for (Object[] r : rewardList) {
						double v = (double) r[1];
						Trackers.row(kv, "Reward: " + ((VisitorOffer.Line) r[0]).name(), Double.isNaN(v) ? "—" : Trackers.num(display, v), Ui.GREEN);
					}
				},
				"cost", () -> Trackers.row(kv, "Cost to buy", Trackers.num(display, fCost) + (fUnpriced ? " + unpriced" : ""), Ui.GOLD),
				"copper", () -> {
					if (fo.copper() > 0) Trackers.row(kv, "Copper", Trackers.num(display, fo.copper()), Ui.ORANGE);
				},
				"per_copper", () -> {
					if (fo.copper() <= 0) return;
					double per = Math.max(0, fCost - fRewards) / fo.copper();
					int col = get(goodBelow) <= 0 ? Ui.AQUA : per <= get(goodBelow) ? Ui.GREEN : Ui.RED;
					Trackers.row(kv, "Coins per copper", Trackers.num(display, per), col);
				}));
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
