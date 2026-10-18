package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatescommon.skyblock.Items;
import io.github.donttemptfate.fatescommon.text.LegacyText;
import io.github.donttemptfate.fatescommon.ui.Card;
import io.github.donttemptfate.fatescommon.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.config.Values;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Display;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.data.MarketMenus;
import io.github.donttemptfate.fatesofa.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Your bazaar orders: in the orders menu, outbid orders get a red outline and filled ones a green one, with a
 * card counting them and the coins sitting in buy orders. You manage them yourself.
 */
public final class BazaarOrders extends Feature {
	final Display display = new Display(this, "bazaarorders", 0xFFFFAA00, Display.lines("orders", "Orders", "outbid", "Outbid", "filled", "Filled (ready to claim)",
			"locked", "Coins in buy orders", "offers", "Value of sell offers"), java.util.List.of("orders", "outbid", "filled", "locked", "offers"));
	final Setting<Integer> outbidColour = setting(Setting.color("bazaarorders.outbid", "Outbid outline", 0xFFFF5555).rainbow().group("Display"));
	final Setting<Integer> filledColour = setting(Setting.color("bazaarorders.filled", "Filled outline", 0xFF55FF55).group("Display"));

	public BazaarOrders() {
		super("bazaar_orders", "Bazaar orders helper", "In your bazaar orders: outbid orders outlined red, filled ones green, and a card with the coins in buy "
				+ "orders and what your sell offers are worth.", Category.ITEMS, true, Risk.SAFE);
	}

	@Override
	public void init() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
			if (!LegacyText.clean(acs.getTitle().getString()).contains("Bazaar Orders")) return;
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> guard(() -> draw(acs, g)));
		});
	}

	void draw(AbstractContainerScreen<?> s, GuiGraphicsExtractor g) {
		Ofa o = Ofa.get();
		long now = System.currentTimeMillis();
		AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) s;
		int orders = 0, outbid = 0, filled = 0;
		double locked = 0, offers = 0;
		for (Slot sl : s.getMenu().slots) {
			if (sl.container instanceof Inventory) continue;
			ItemStack st = sl.getItem();
			if (st.isEmpty()) continue;
			MarketMenus.Order ord = MarketMenus.order(Items.plainName(st), Items.lorePlain(st));
			if (ord == null) continue;
			orders++;
			String key = Trackers.keyForName(ord.product());
			var q = key == null ? null : o.prices.bazaarQuote(key, now);
			boolean out = q != null && MarketMenus.outbid(ord, q.instantSell(), q.instantBuy());
			int x = a.fatesofa$leftPos() + sl.x, y = a.fatesofa$topPos() + sl.y;
			if (ord.full()) {
				filled++;
				Ui.ring(g, x - 1, y - 1, 18, 18, 1, get(filledColour) | 0xFF000000);
			} else if (out) {
				outbid++;
				Ui.ring(g, x - 1, y - 1, 18, 18, 2, Values.color(get(outbidColour), on(outbidColour.rainbow), now));
			}
			if (ord.buy()) locked += (ord.amount() - ord.filled()) * ord.priceEach();
			else offers += (ord.amount() - ord.filled()) * ord.priceEach();
		}
		if (orders == 0) return;
		Card c = Trackers.card(display, "BAZAAR ORDERS", null);
		Card.KeyValues kv = Trackers.rows();
		int fo = orders, fb = outbid, ff = filled;
		double fl = locked, fof = offers;
		display.each(Map.of(
				"orders", () -> Trackers.row(kv, "Orders", Integer.toString(fo), Ui.WHITE),
				"outbid", () -> Trackers.row(kv, "Outbid", Integer.toString(fb), fb > 0 ? Ui.RED : Ui.GRAY),
				"filled", () -> Trackers.row(kv, "Filled", Integer.toString(ff), ff > 0 ? Ui.GREEN : Ui.GRAY),
				"locked", () -> {
					if (fl > 0) Trackers.row(kv, "In buy orders", Trackers.num(display, fl), Ui.GOLD);
				},
				"offers", () -> {
					if (fof > 0) Trackers.row(kv, "Sell offers worth", Trackers.num(display, fof), Ui.GOLD);
				}));
		c.add(kv);
		Trackers.beside(s, g, c);
	}
}
