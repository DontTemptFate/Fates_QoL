package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatescommon.skyblock.Items;
import io.github.donttemptfate.fatescommon.text.Fmt;
import io.github.donttemptfate.fatescommon.text.LegacyText;
import io.github.donttemptfate.fatescommon.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.data.Price;
import io.github.donttemptfate.fatesofa.game.Game;
import io.github.donttemptfate.fatesofa.game.Keys;
import io.github.donttemptfate.fatesofa.mixin.AbstractContainerScreenAccessor;
import io.github.donttemptfate.fatesofa.protect.Protection;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;


/**
 * Item and slot locks, plus a second click for selling, salvaging or dropping valuable items.
 * Lock: hover an item and press the lock key (Controls → Fates OFA). Shift + lock key locks the slot.
 */
public final class ProtectionFeature extends Feature {
	public static ProtectionFeature INSTANCE;

	final Setting<Integer> confirmMillions = setting(Setting.integer("protect.confirmValue", "Ask before selling items worth (M coins)", 5, 0, 1000)
			.help("Selling, salvaging or dropping an item worth at least this many million coins needs a second click. 0 = never ask.")
			.keywords("confirm valuable sell salvage drop").group("Valuable items"));
	final Setting<Boolean> showLocks = setting(Setting.bool("protect.showLocks", "Show lock marks on items", true).group("Lock marks")
			.help("A red corner on locked items and slots in every inventory."));
	final Setting<Integer> itemMark = setting(Setting.color("protect.itemMark", "Locked item mark", 0xFFFF5555).group("Lock marks"));
	final Setting<Integer> slotMark = setting(Setting.color("protect.slotMark", "Locked slot mark", 0xFFFFAA00).group("Lock marks"));
	final Setting<String> markStyle = setting(Setting.choice("protect.markStyle", "Mark style", "corner", java.util.List.of("corner", "outline", "tint"))
			.group("Lock marks"));
	final Setting<String> lockStops = setting(Setting.lines("protect.lockStops", "Locks stop", io.github.donttemptfate.fatesofa.core.Display.lines("DROP", "Dropping", "NPC_SELL", "Selling to an NPC", "SALVAGE", "Salvaging", "TRADE", "Trading", "AUCTION", "Listing on the auction house", "BAZAAR", "Selling on the bazaar"),
			java.util.List.of("DROP", "NPC_SELL", "SALVAGE", "TRADE", "AUCTION", "BAZAAR")).group("What locks stop")
			.help("Untick what you want to allow for locked items (e.g. trading). Moving items around is always allowed."));
	final Setting<String> confirmFor = setting(Setting.lines("protect.confirmFor", "Ask twice before", io.github.donttemptfate.fatesofa.core.Display.lines("DROP", "Dropping", "NPC_SELL", "Selling to an NPC", "SALVAGE", "Salvaging", "TRADE", "Trading", "AUCTION", "Listing on the auction house", "BAZAAR", "Selling on the bazaar"),
			java.util.List.of("NPC_SELL", "SALVAGE", "DROP")).group("Valuable items"));
	final Setting<Integer> confirmSeconds = setting(Setting.integer("protect.confirmSeconds", "Time for the second click (s)", 3, 1, 10).group("Valuable items").advanced());
	final Setting<Integer> dropK = setting(Setting.integer("protect.dropValue", "Ask before dropping items worth (k coins)", 100, 0, 1_000_000)
			.group("Valuable items").help("Separate from the sell threshold above, because a dropped item is gone for good. 0 = off."));
	final Setting<Integer> npcShare = setting(Setting.integer("protect.npcShare", "Ask when an NPC pays under (% of value)", 5, 0, 50).group("NPC selling")
			.help("Selling to an NPC for a tiny part of what the item is worth needs a second click. 0 = off."));
	final Setting<Integer> npcMinK = setting(Setting.integer("protect.npcMin", "Only for items worth over (k coins)", 100, 0, 100_000).group("NPC selling"));
	final Setting<Boolean> auctionGuard = setting(Setting.bool("protect.auction", "Check auction prices", true).group("Auction price check")
			.help("Creating an auction for far below the lowest BIN, or with what looks like an extra zero, needs the same click twice."));
	final Setting<Integer> auctionLow = setting(Setting.integer("protect.auctionLow", "Ask below (% of lowest BIN)", 50, 1, 100).group("Auction price check"));
	final Setting<Integer> auctionHigh = setting(Setting.integer("protect.auctionHigh", "Ask above (× lowest BIN)", 10, 2, 100).group("Auction price check"));
	final Setting<Boolean> tradeTotals = setting(Setting.bool("protect.trade", "Show trade totals", true).group("Trades")
			.help("Beside the trade window: what you give, what you get (with upgrades), and a warning when it's lopsided."));
	final Setting<Integer> tradeWarn = setting(Setting.integer("protect.tradeWarn", "Warn when you get less than (% of what you give)", 50, 0, 100)
			.group("Trades"));
	final Setting<String> blockSound = setting(Setting.sound("protect.sound", "Sound when something is stopped", "minecraft:entity.villager.no|0.6|1").group("When stopped"));
	final Setting<Boolean> chatNote = setting(Setting.bool("protect.chat", "Say why in chat", true).group("When stopped"));
	final Setting<Boolean> titleNote = setting(Setting.bool("protect.title", "Also a title on screen", false).group("When stopped").advanced());

	private long lastNoteAt;
	private String lastNote;

	public ProtectionFeature() {
		super("protection", "Item protection", "Lock items and slots so they can't be dropped, sold, salvaged, traded or listed by mistake (moving them around is fine); valuable items need a second click.",
				Category.PROTECTION, true, Risk.SAFE);
		INSTANCE = this;
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		o.protection.fromJson(o.settings.raw("protect.locks"));
		o.settings.onChange(k -> {
			if (k.startsWith("protect.")) apply(o);
			if (k.equals("protect.locks")) o.protection.fromJson(o.settings.raw("protect.locks")); // imported
		});
		apply(o);
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> guard(() -> {
				drawLocks(acs, g);
				if (on(tradeTotals)) drawTrade(acs, g);
			}));
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, key) -> guard(() -> {
				if (Keys.lock == null || !Keys.lock.matches(key)) return true;
				toggleHovered(acs, Minecraft.getInstance().hasShiftDown());
				return false;
			}, true));
		});
	}

	/** Pushes the settings into the protection rules. */
	void apply(Ofa o) {
		o.protection.confirmValue = get(confirmMillions) * 1_000_000.0;
		o.protection.dropConfirmValue = get(dropK) * 1000.0;
		o.protection.npcMinShare = get(npcShare) / 100.0;
		o.protection.npcMinValue = get(npcMinK) * 1000.0;
		o.protection.auctionLowShare = on(auctionGuard) ? get(auctionLow) / 100.0 : 0;
		o.protection.auctionHighTimes = on(auctionGuard) ? get(auctionHigh) : 0;
		o.protection.confirmWindowMs = get(confirmSeconds) * 1000L;
		o.protection.lockStops = actions(get(lockStops), lockStops.options);
		o.protection.confirmFor = actions(get(confirmFor), confirmFor.options);
	}

	static java.util.Set<Protection.Action> actions(String v, java.util.List<String> known) {
		java.util.Set<Protection.Action> out = java.util.EnumSet.noneOf(Protection.Action.class);
		for (String id : io.github.donttemptfate.fatesofa.config.Values.lines(v, known)) out.add(Protection.Action.valueOf(id));
		return out;
	}

	void toggleHovered(AbstractContainerScreen<?> screen, boolean slot) {
		Slot hovered = ((AbstractContainerScreenAccessor) screen).fatesofa$hoveredSlot();
		if (hovered == null) return;
		Ofa o = Ofa.get();
		if (slot) {
			if (!(hovered.container instanceof Inventory)) {
				Game.warn("Only your own inventory slots can be locked.");
				return;
			}
			boolean now = o.protection.toggleSlot(hovered.getContainerSlot());
			Game.info("Slot " + hovered.getContainerSlot() + (now ? " locked." : " unlocked."));
		} else {
			ItemStack st = hovered.getItem();
			if (st.isEmpty()) return;
			String uuid = Items.uuid(st), key = Items.key(st);
			if (uuid == null && key == null) {
				Game.warn("That item can't be locked (not a SkyBlock item). Lock its slot with Shift instead.");
				return;
			}
			boolean now = o.protection.toggleItem(uuid, key);
			Game.info(Items.plainName(st) + (now ? " locked." : " unlocked.") + (uuid == null ? " (all " + Items.plainName(st) + ")" : ""));
		}
		o.settings.putRaw("protect.locks", o.protection.toJson());
	}

	void drawLocks(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g) {
		if (!on(showLocks)) return;
		Ofa o = Ofa.get();
		AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
		int lx = a.fatesofa$leftPos(), ty = a.fatesofa$topPos();
		for (Slot s : screen.getMenu().slots) {
			boolean slotLock = s.container instanceof Inventory && o.protection.slotLocked(s.getContainerSlot());
			ItemStack st = s.getItem();
			var facts = io.github.donttemptfate.fatescommon.skyblock.ItemFacts.of(st);
			boolean itemLock = !st.isEmpty() && o.protection.itemLocked(facts.uuid(), facts.uuid() == null ? facts.key() : null);
			if (!slotLock && !itemLock) continue;
			int x = lx + s.x, y = ty + s.y;
			int c = (itemLock ? get(itemMark) : get(slotMark)) | 0xFF000000;
			switch (get(markStyle)) {
				case "outline" -> Ui.ring(g, x - 1, y - 1, 18, 18, 1, c);
				case "tint" -> g.fill(x, y, x + 16, y + 16, (c & 0x00FFFFFF) | 0x50000000);
				default -> {
					g.fill(x, y, x + 4, y + 1, c);
					g.fill(x, y, x + 1, y + 4, c);
				}
			}
			if (slotLock) Ui.ring(g, x - 1, y - 1, 18, 18, 1, (get(slotMark) & 0x00FFFFFF) | 0x80000000);
		}
	}

	// ---- called from the mixin ----------------------------------------------------------------------

	public static boolean blockClick(int containerId, int slotId, int button, ContainerInput input, Player player) {
		ProtectionFeature f = INSTANCE;
		if (f == null) return false;
		return f.guard(() -> f.checkClick(containerId, slotId, button, input, player), false);
	}

	public static boolean blockDrop(LocalPlayer player) {
		ProtectionFeature f = INSTANCE;
		if (f == null) return false;
		return f.guard(() -> {
			ItemStack st = player.getInventory().getSelectedItem();
			int slot = player.getInventory().getSelectedSlot();
			return (Ofa.get().protection.slotLocked(slot) && f.note("Slot " + slot + " is locked.")) || f.decide(Protection.Action.DROP, st, slot);
		}, false);
	}

	boolean checkClick(int containerId, int slotId, int button, ContainerInput input, Player player) {
		Ofa o = Ofa.get();
		if (!o.state.onSkyBlock() && !io.github.donttemptfate.fatesofa.game.Dev.test()) return false;
		// the window the click belongs to (normally the player's open menu)
		AbstractContainerMenu menu = player.containerMenu;
		if (Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> open && open.getMenu().containerId == containerId) menu = open.getMenu();
		if (menu == null) return false;
		if (slotId == -999) {
			ItemStack carried = menu.getCarried();
			return !carried.isEmpty() && (input == ContainerInput.PICKUP || input == ContainerInput.THROW) && decide(Protection.Action.DROP, carried, -1);
		}
		if (slotId < 0 || slotId >= menu.slots.size()) return false;
		Slot slot = menu.getSlot(slotId);
		ItemStack st = slot.getItem();
		boolean own = slot.container instanceof Inventory;
		// a locked slot never gives up its item
		if (own && o.protection.slotLocked(slot.getContainerSlot()) && input != ContainerInput.CLONE) {
			return note("Slot " + slot.getContainerSlot() + " is locked.");
		}
		if (input == ContainerInput.SWAP && button >= 0 && button < 9) {
			if (o.protection.slotLocked(button)) return note("Hotbar slot " + button + " is locked.");
			// the hotbar item moves into the clicked slot: same rules as clicking it there
			ItemStack hot = player.getInventory().getItem(button);
			if (!hot.isEmpty() && !own) {
				Protection.Action a = menuAction(menu);
				if (decide(a != null ? a : Protection.Action.TRADE, hot, button)) return true;
			}
		}
		if (input == ContainerInput.THROW) return decide(Protection.Action.DROP, st, slotId);
		Protection.Action a = menuAction(menu);
		if (!own && a == Protection.Action.AUCTION && auctionClick(menu, st)) return true;
		if (!own) {
			// putting the item on your cursor into the window (an auction slot, a trade offer…)
			ItemStack carried = menu.getCarried();
			if (a != null && !carried.isEmpty() && decide(a, carried, -1)) return true;
			// Bazaar "Sell Inventory Now" sells every matching stack you carry
			if (a == Protection.Action.BAZAAR && Items.plainName(st).startsWith("Sell Inventory")) {
				for (Slot sl : menu.slots) {
					if (!(sl.container instanceof Inventory) || sl.getItem().isEmpty()) continue;
					var facts = io.github.donttemptfate.fatescommon.skyblock.ItemFacts.of(sl.getItem());
					if (o.protection.itemLocked(facts.uuid(), facts.uuid() == null ? facts.key() : null))
						return note(Items.plainName(sl.getItem()) + " is locked — move it out of your inventory before selling the inventory");
				}
			}
			return false;
		}
		if (st.isEmpty()) return false;
		return a != null && decide(a, st, slotId);
	}

	/** What putting your own item into the open menu does (by its title and its fixed buttons). */
	static Protection.Action menuAction(AbstractContainerMenu menu) {
		Screen screen = Minecraft.getInstance().gui.screen();
		String title = screen == null ? "" : LegacyText.clean(screen.getTitle().getString());
		java.util.List<String> buttons = new java.util.ArrayList<>();
		for (Slot s : menu.slots) if (!(s.container instanceof Inventory) && !s.getItem().isEmpty()) buttons.add(Items.plainName(s.getItem()));
		return Protection.actionFor(title, buttons);
	}

	static final java.util.regex.Pattern PRICE = java.util.regex.Pattern.compile("(?i)(?:item price|starting bid|bin price|price): ([\\d,.]+[kmb]?) coins?");
	static final java.util.regex.Pattern COINS = java.util.regex.Pattern.compile("(?i)^([\\d,.]+[kmb]?) coins?$");

	/** What an item is worth with its upgrades (0 when nothing prices it). */
	static double worth(ItemStack st) {
		String key = Items.key(st);
		if (key == null) return 0;
		double v = Ofa.get().valuation.total(Items.data(st), key, st.getCount(), System.currentTimeMillis());
		return Double.isNaN(v) ? 0 : v;
	}

	/** Clicking "Create (BIN) Auction" / "Confirm": checks the price against the item's lowest BIN. True = stopped. */
	boolean auctionClick(AbstractContainerMenu menu, ItemStack button) {
		if (button.isEmpty()) return false;
		String name = Items.plainName(button);
		if (!(name.startsWith("Create BIN Auction") || name.startsWith("Create Auction") || name.equals("Confirm"))) return false;
		double price = Double.NaN;
		for (String l : Items.lorePlain(button)) {
			java.util.regex.Matcher m = PRICE.matcher(l);
			if (m.find()) {
				price = Fmt.parse(m.group(1));
				break;
			}
		}
		if (!(price > 0) || menu.slots.size() <= 13) return false;
		ItemStack item = menu.getSlot(13).getItem();
		String key = Items.key(item);
		if (key == null) return false;
		var ah = Ofa.get().prices.ahQuote(key);
		if (ah == null || !(ah.lbin() > 0)) return false;
		Protection.Decision d = Ofa.get().protection.decideAuction(new Protection.Facts(Items.uuid(item), key, Items.plainName(item), worth(item), 13),
				price, ah.lbin() * Math.max(1, item.getCount()), System.currentTimeMillis());
		return d.verdict() != Protection.Verdict.ALLOW && note(d.reason() + " (" + Fmt.shortNum(price) + " vs " + Fmt.shortNum(ah.lbin()) + ")");
	}

	// ---- trade totals ---------------------------------------------------------------------------------

	private Object tradeFor;
	private long tradeAt;
	private io.github.donttemptfate.fatescommon.ui.Card tradeCard;

	/** Hypixel's trade window: your offer in the left four columns, theirs in the right four. */
	void drawTrade(AbstractContainerScreen<?> s, GuiGraphicsExtractor g) {
		if (Protection.actionFor(LegacyText.clean(s.getTitle().getString()), java.util.List.of()) != Protection.Action.TRADE) return;
		long now = System.currentTimeMillis();
		if (s != tradeFor || now - tradeAt > 500) {
			tradeFor = s;
			tradeAt = now;
			double mine = 0, theirs = 0;
			for (Slot sl : s.getMenu().slots) {
				if (sl.container instanceof Inventory || sl.index >= 36 || sl.getItem().isEmpty()) continue;
				int col = sl.index % 9;
				if (col == 4) continue;
				ItemStack st = sl.getItem();
				java.util.regex.Matcher cm = COINS.matcher(Items.plainName(st));
				double v = cm.matches() ? Fmt.parse(cm.group(1)) : worth(st);
				if (col < 4) mine += v;
				else theirs += v;
			}
			tradeCard = null;
			if (mine > 0 || theirs > 0) {
				var c = new io.github.donttemptfate.fatescommon.ui.Card(0.9f, 100, 170);
				c.add(io.github.donttemptfate.fatescommon.ui.Card.Line.of(io.github.donttemptfate.fatescommon.ui.Card.seg("TRADE", Ui.Role.LABEL, Ui.accent())));
				var kv = new io.github.donttemptfate.fatescommon.ui.Card.KeyValues(1);
				kv.row(io.github.donttemptfate.fatescommon.ui.Card.seg("You give", Ui.Role.SMALL, Ui.GRAY), io.github.donttemptfate.fatescommon.ui.Card.seg(Fmt.shortNum(mine), Ui.Role.SMALL, Ui.GOLD));
				kv.row(io.github.donttemptfate.fatescommon.ui.Card.seg("You get", Ui.Role.SMALL, Ui.GRAY), io.github.donttemptfate.fatescommon.ui.Card.seg(Fmt.shortNum(theirs), Ui.Role.SMALL, Ui.GOLD));
				c.add(kv);
				if (mine > 0 && theirs < mine * get(tradeWarn) / 100.0)
					c.add(io.github.donttemptfate.fatescommon.ui.Card.Line.of(io.github.donttemptfate.fatescommon.ui.Card.seg("You get only " + Math.round(theirs / mine * 100) + "% of what you give",
							Ui.Role.SMALL, Ui.RED)));
				c.add(io.github.donttemptfate.fatescommon.ui.Card.Line.of(io.github.donttemptfate.fatescommon.ui.Card.seg("Values from public prices; check before accepting.", Ui.Role.SMALL, Ui.DARK)));
				tradeCard = c;
			}
		}
		if (tradeCard == null) return;
		AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) s;
		int reserve = QuickCommandsFeature.INSTANCE == null ? 0 : QuickCommandsFeature.INSTANCE.leftReserve(s);
		int edge = a.fatesofa$leftPos() - reserve;
		float scale = Math.min(1f, Math.max(0.5f, (edge - 8) / (float) tradeCard.width()));
		tradeCard.draw(g, Math.max(2, edge - Math.round(tradeCard.width() * scale) - 6), a.fatesofa$topPos(), scale);
	}

	boolean decide(Protection.Action action, ItemStack st, int slot) {
		if (st == null || st.isEmpty()) return false;
		Ofa o = Ofa.get();
		String key = Items.key(st);
		double value = worth(st);
		Double npcEach = key == null ? null : o.prices.npcPrice(key);
		Protection.Decision d = o.protection.decide(action, new Protection.Facts(Items.uuid(st), key, Items.plainName(st), value, slot,
				npcEach == null ? Double.NaN : npcEach * st.getCount()), System.currentTimeMillis());
		return switch (d.verdict()) {
			case ALLOW -> false;
			case BLOCK -> note(d.reason());
			case CONFIRM -> note(d.reason() + " (" + Fmt.shortNum(value) + ")");
		};
	}

	/** Tells the player why (at most once a second per message); returns true = blocked. */
	boolean note(String text) {
		long now = System.currentTimeMillis();
		if (!text.equals(lastNote) || now - lastNoteAt > 1000) {
			if (on(chatNote)) Game.warn(text);
			if (on(titleNote)) io.github.donttemptfate.fatesofa.game.Alerts.title("STOPPED", 0xFF5555, text.length() > 48 ? text.substring(0, 47) + "…" : text);
			io.github.donttemptfate.fatesofa.game.Alerts.play(io.github.donttemptfate.fatesofa.config.Values.sound(get(blockSound)));
		}
		lastNote = text;
		lastNoteAt = now;
		return true;
	}
}
