package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.item.Items;
import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.text.Strip;
import io.github.donttemptfate.fates.common.ui.Ui;
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
			.keywords("confirm valuable sell salvage drop"));
	final Setting<Boolean> showLocks = setting(Setting.bool("protect.showLocks", "Show lock marks on items", true)
			.help("A red corner on locked items and slots in every inventory."));

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
			if (k.equals(confirmMillions.key)) o.protection.confirmValue = get(confirmMillions) * 1_000_000.0;
			if (k.equals("protect.locks")) o.protection.fromJson(o.settings.raw("protect.locks")); // imported
		});
		o.protection.confirmValue = get(confirmMillions) * 1_000_000.0;
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> guard(() -> drawLocks(acs, g)));
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, key) -> guard(() -> {
				if (Keys.lock == null || !Keys.lock.matches(key)) return true;
				toggleHovered(acs, Minecraft.getInstance().hasShiftDown());
				return false;
			}, true));
		});
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
			var facts = io.github.donttemptfate.fates.common.item.ItemFacts.of(st);
			boolean itemLock = !st.isEmpty() && o.protection.itemLocked(facts.uuid(), facts.uuid() == null ? facts.key() : null);
			if (!slotLock && !itemLock) continue;
			int x = lx + s.x, y = ty + s.y;
			int c = itemLock ? 0xFFFF5555 : 0xFFFFAA00;
			g.fill(x, y, x + 4, y + 1, c);
			g.fill(x, y, x + 1, y + 4, c);
			if (slotLock) Ui.ring(g, x - 1, y - 1, 18, 18, 1, 0x80FFAA00);
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
		if (!own) {
			// putting the item on your cursor into the window (an auction slot, a trade offer…)
			ItemStack carried = menu.getCarried();
			if (a != null && !carried.isEmpty() && decide(a, carried, -1)) return true;
			// Bazaar "Sell Inventory Now" sells every matching stack you carry
			if (a == Protection.Action.BAZAAR && Items.plainName(st).startsWith("Sell Inventory")) {
				for (Slot sl : menu.slots) {
					if (!(sl.container instanceof Inventory) || sl.getItem().isEmpty()) continue;
					var facts = io.github.donttemptfate.fates.common.item.ItemFacts.of(sl.getItem());
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
		String title = screen == null ? "" : Strip.clean(screen.getTitle().getString());
		java.util.List<String> buttons = new java.util.ArrayList<>();
		for (Slot s : menu.slots) if (!(s.container instanceof Inventory) && !s.getItem().isEmpty()) buttons.add(Items.plainName(s.getItem()));
		return Protection.actionFor(title, buttons);
	}

	boolean decide(Protection.Action action, ItemStack st, int slot) {
		if (st == null || st.isEmpty()) return false;
		Ofa o = Ofa.get();
		String key = Items.key(st);
		Price p = key == null ? null : o.prices.value(key, System.currentTimeMillis());
		double value = p == null ? 0 : p.value() * st.getCount();
		Protection.Decision d = o.protection.decide(action, new Protection.Facts(Items.uuid(st), key, Items.plainName(st), value, slot),
				System.currentTimeMillis());
		return switch (d.verdict()) {
			case ALLOW -> false;
			case BLOCK -> note(d.reason());
			case CONFIRM -> note(d.reason() + " (" + Fmt.shortNum(value) + ")");
		};
	}

	/** Tells the player why (at most once a second per message); returns true = blocked. */
	boolean note(String text) {
		long now = System.currentTimeMillis();
		if (!text.equals(lastNote) || now - lastNoteAt > 1000) Game.warn(text);
		lastNote = text;
		lastNoteAt = now;
		return true;
	}
}
