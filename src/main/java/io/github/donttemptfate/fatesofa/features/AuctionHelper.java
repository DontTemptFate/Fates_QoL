package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatescommon.skyblock.Items;
import io.github.donttemptfate.fatescommon.text.Fmt;
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
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Auction house: under each listing's tooltip, the item's lowest BIN and its worth with upgrades (plain numbers,
 * not buy advice); beside "Manage Auctions", what sold, what expired and the coins waiting to be collected.
 */
public final class AuctionHelper extends Feature {
	final Setting<String> lines = setting(Setting.lines("auctionhelper.tooltip", "Lines under listings", Display.lines(
			"lbin", "Lowest BIN of this item", "compare", "This listing vs the lowest BIN (%)", "worth", "Worth with upgrades", "sales", "Sales a day"),
			List.of("lbin", "compare", "worth")));
	final Display display = new Display(this, "auctionhelper", 0xFFFFAA00, Display.lines("sold", "Sold (coins to collect)", "expired", "Expired",
			"active", "Still running", "listed", "Value still listed"), List.of("sold", "expired", "active", "listed"));

	public AuctionHelper() {
		super("auction_helper", "Auction house helper", "Under auction listings: the lowest BIN and the item's worth with upgrades. Beside Manage Auctions: "
				+ "what sold, what expired and the coins waiting for you. Numbers only, not buy advice.", Category.ITEMS, true, Risk.SAFE);
	}

	static boolean auctionMenu(String title) {
		return title.contains("Auction") || title.startsWith("Auctions") || title.contains("BIN");
	}

	@Override
	public void init() {
		ItemTooltipCallback.EVENT.register((st, ctx, flag, out) -> guard(() -> {
			Minecraft mc = Minecraft.getInstance();
			if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> s) || !auctionMenu(LegacyText.clean(s.getTitle().getString()))) return;
			MarketMenus.Listing l = MarketMenus.listing(Items.lorePlain(st));
			if (l == null) return;
			String key = Items.key(st);
			if (key == null) return;
			Ofa o = Ofa.get();
			var ah = o.prices.ahQuote(key);
			List<String> want = Values.lines(get(lines), lines.options);
			for (String id : want) {
				switch (id) {
					case "lbin" -> {
						if (ah != null && ah.lbin() > 0) out.add(Component.literal("Lowest BIN: ").withStyle(ChatFormatting.GRAY)
								.append(Component.literal(Fmt.shortNum(ah.lbin() * st.getCount())).withStyle(ChatFormatting.GOLD)));
					}
					case "compare" -> {
						if (ah != null && ah.lbin() > 0) {
							long pct = MarketMenus.percentVs(l.price(), ah.lbin() * st.getCount());
							out.add(Component.literal("This listing: " + (pct >= 0 ? "+" : "") + pct + "% vs lowest BIN").withStyle(ChatFormatting.DARK_GRAY));
						}
					}
					case "worth" -> {
						var b = o.valuation.of(Items.data(st), key, st.getCount(), System.currentTimeMillis());
						if (b != null && !b.parts().isEmpty()) out.add(Component.literal("Worth with upgrades: ").withStyle(ChatFormatting.GRAY)
								.append(Component.literal(Fmt.shortNum(b.total())).withStyle(ChatFormatting.GOLD)));
					}
					case "sales" -> {
						if (ah != null && ah.salesPerDay() > 0) out.add(Component.literal("Sells ~" + Fmt.trimmed(ah.salesPerDay()) + " a day").withStyle(ChatFormatting.DARK_GRAY));
					}
					default -> {
					}
				}
			}
		}));
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
			String t = LegacyText.clean(acs.getTitle().getString());
			if (!(t.equals("Manage Auctions") || t.equals("Your Auctions") || t.startsWith("Manage Auctions"))) return;
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> guard(() -> drawManage(acs, g)));
		});
	}

	void drawManage(AbstractContainerScreen<?> s, GuiGraphicsExtractor g) {
		int sold = 0, expired = 0, active = 0;
		double soldCoins = 0, listed = 0;
		for (Slot sl : s.getMenu().slots) {
			if (sl.container instanceof Inventory) continue;
			ItemStack st = sl.getItem();
			if (st.isEmpty()) continue;
			MarketMenus.Listing l = MarketMenus.listing(Items.lorePlain(st));
			if (l == null) continue;
			switch (l.status()) {
				case SOLD -> {
					sold++;
					soldCoins += l.price();
				}
				case EXPIRED, ENDED -> expired++;
				default -> {
					active++;
					listed += l.price();
				}
			}
		}
		if (sold + expired + active == 0) return;
		Card c = Trackers.card(display, "YOUR AUCTIONS", null);
		Card.KeyValues kv = Trackers.rows();
		int fs = sold, fe = expired, fa = active;
		double fc = soldCoins, fl = listed;
		display.each(Map.of(
				"sold", () -> Trackers.row(kv, "Sold", fs + (fs > 0 ? " · " + Trackers.num(display, fc) + " to collect" : ""), fs > 0 ? Ui.GREEN : Ui.GRAY),
				"expired", () -> Trackers.row(kv, "Expired", Integer.toString(fe), fe > 0 ? Ui.YELLOW : Ui.GRAY),
				"active", () -> Trackers.row(kv, "Running", Integer.toString(fa), Ui.WHITE),
				"listed", () -> {
					if (fl > 0) Trackers.row(kv, "Still listed", Trackers.num(display, fl), Ui.GOLD);
				}));
		c.add(kv);
		Trackers.beside(s, g, c);
	}
}
