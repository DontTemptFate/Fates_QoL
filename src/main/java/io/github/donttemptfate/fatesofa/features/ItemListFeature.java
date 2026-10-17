package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.item.Items;
import io.github.donttemptfate.fates.common.text.Strip;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.data.RepoItem;
import io.github.donttemptfate.fatesofa.game.Keys;
import io.github.donttemptfate.fatesofa.items.ItemIndex;
import io.github.donttemptfate.fatesofa.items.Stacks;
import io.github.donttemptfate.fatesofa.mixin.AbstractContainerScreenAccessor;
import io.github.donttemptfate.fatesofa.ui.RecipeScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * The item list: every SkyBlock item, searchable, beside any inventory window. Click an item for its
 * recipes, right-click for what it's used in. Typing in the search also highlights matching items in the
 * open window.
 */
public final class ItemListFeature extends Feature {
	public static ItemListFeature INSTANCE;

	final Setting<Boolean> highlight = setting(Setting.bool("itemlist.highlight", "Highlight search matches in the window", true)
			.help("While you type in the item list's search, items in the open window that don't match are dimmed.")
			.keywords("search find storage"));
	final Setting<Boolean> startShown = setting(Setting.bool("itemlist.shown", "Show the item list", true)
			.help("The list beside inventory windows. The item list key shows or hides it."));

	private volatile ItemIndex index;
	private String query = "";
	private int page;
	private boolean hidden;

	public ItemListFeature() {
		super("item_list", "Item list & recipes", "Every SkyBlock item beside your inventory: search, recipes, what an item is used in, and values.",
				Category.ITEMS, true, Risk.SAFE);
		INSTANCE = this;
	}

	@Override
	public java.util.Set<String> keywords() {
		return java.util.Set.of("neu", "recipe", "encyclopedia", "search", "craft");
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		o.repo.onLoaded(() -> index = new ItemIndex(o.repo.items()));
		if (o.repo.items().size() > 0) index = new ItemIndex(o.repo.items());
		hidden = !on(startShown);
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
			guard(() -> attach(mc, acs));
		});
	}

	public ItemIndex index() {
		return index;
	}

	/** Panel geometry for a window; null when there's no room. */
	record Panel(int x, int y, int w, int h, int cols, int rows) {
		int perPage() {
			return cols * rows;
		}

		int cellX(int i) {
			return x + 2 + (i % cols) * 18;
		}

		int cellY(int i) {
			return y + 20 + (i / cols) * 18;
		}
	}

	static Panel panel(AbstractContainerScreen<?> s) {
		AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) s;
		int x = a.fatesofa$leftPos() + a.fatesofa$imageWidth() + 8;
		int w = s.width - x - 4;
		if (w < 60) return null;
		int cols = Math.min(12, (w - 4) / 18);
		int rows = Math.max(1, (s.height - 20 - 18 - 6) / 18);
		return new Panel(x, 4, cols * 18 + 4, s.height - 8, cols, rows);
	}

	void attach(Minecraft mc, AbstractContainerScreen<?> s) {
		Panel p0 = panel(s);
		if (p0 == null) return;
		EditBox box = new EditBox(mc.font, p0.x(), p0.y(), p0.w(), 14, Component.literal("Search items"));
		box.setHint(Component.literal("Search items…"));
		box.setValue(query);
		box.setMaxLength(60);
		box.setResponder(v -> {
			query = v;
			page = 0;
		});
		box.setVisible(!hidden);
		Screens.getWidgets(s).add(box);

		ScreenEvents.afterExtract(s).register((sc, g, mx, my, d) -> guard(() -> {
			box.setVisible(!hidden);
			if (!hidden) draw(s, g, mx, my);
			if (on(highlight) && !query.isBlank()) dimNonMatching(s, g);
		}));
		ScreenMouseEvents.allowMouseClick(s).register((sc, e) -> guard(() -> !click(s, e), true));
		ScreenMouseEvents.allowMouseScroll(s).register((sc, mx, my, h, v) -> guard(() -> !scroll(s, mx, my, v), true));
		ScreenKeyboardEvents.allowKeyPress(s).register((sc, e) -> guard(() -> key(s, box, e), true));
	}

	List<RepoItem> results() {
		ItemIndex i = index;
		return i == null ? List.of() : i.search(query);
	}

	void draw(AbstractContainerScreen<?> s, GuiGraphicsExtractor g, int mx, int my) {
		Panel p = panel(s);
		if (p == null) return;
		ItemIndex idx = index;
		if (idx == null) {
			g.text(s.getFont(), "Item data loading…", p.x(), p.y() + 20, Ui.GRAY, true);
			return;
		}
		List<RepoItem> res = results();
		int pages = Math.max(1, (res.size() + p.perPage() - 1) / p.perPage());
		page = Math.min(page, pages - 1);
		int start = page * p.perPage();
		RepoItem hovered = null;
		for (int i = 0; i < p.perPage() && start + i < res.size(); i++) {
			int cx = p.cellX(i), cy = p.cellY(i);
			RepoItem it = res.get(start + i);
			boolean over = mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18;
			if (over) {
				g.fill(cx, cy, cx + 18, cy + 18, 0x40FFFFFF);
				hovered = it;
			}
			g.item(Stacks.of(it), cx + 1, cy + 1);
		}
		int by = p.cellY(p.perPage()) + 2;
		String label = res.isEmpty() ? "no items" : (page + 1) + " / " + pages + " · " + res.size();
		g.text(s.getFont(), "<", p.x() + 2, by, mx < p.x() + 14 && my >= by - 2 && my < by + 10 ? Ui.WHITE : Ui.GRAY, true);
		g.text(s.getFont(), ">", p.x() + p.w() - 8, by, mx > p.x() + p.w() - 14 && my >= by - 2 && my < by + 10 ? Ui.WHITE : Ui.GRAY, true);
		g.centeredText(s.getFont(), label, p.x() + p.w() / 2, by, Ui.GRAY);
		if (hovered != null) g.setTooltipForNextFrame(s.getFont(), Stacks.of(hovered), mx, my);
	}

	/** Returns true when the click was ours (the window doesn't get it). */
	boolean click(AbstractContainerScreen<?> s, MouseButtonEvent e) {
		if (hidden) return false;
		Panel p = panel(s);
		if (p == null || e.x() < p.x() || e.x() >= p.x() + p.w() || e.y() < p.y() + 18) return false;
		int by = p.cellY(p.perPage()) + 2;
		if (e.y() >= by - 2 && e.y() < by + 10) {
			if (e.x() < p.x() + 14) page = Math.max(0, page - 1);
			else if (e.x() > p.x() + p.w() - 14) page++;
			return true;
		}
		List<RepoItem> res = results();
		int col = (int) ((e.x() - p.x() - 2) / 18), row = (int) ((e.y() - p.y() - 20) / 18);
		if (col < 0 || col >= p.cols() || row < 0 || row >= p.rows()) return true;
		int i = page * p.perPage() + row * p.cols() + col;
		if (i >= res.size()) return true;
		RepoItem it = res.get(i);
		Minecraft.getInstance().gui.setScreen(new RecipeScreen(s, it.key(), io.github.donttemptfate.fatesofa.ui.Mouse.right(e.button())));
		return true;
	}

	boolean scroll(AbstractContainerScreen<?> s, double mx, double my, double v) {
		if (hidden) return false;
		Panel p = panel(s);
		if (p == null || mx < p.x()) return false;
		page = Math.max(0, page + (v < 0 ? 1 : -1));
		return true;
	}

	/** Keys: typing goes to the search box (so "E" doesn't close the window); the item list key toggles the panel. */
	boolean key(AbstractContainerScreen<?> s, EditBox box, KeyEvent e) {
		if (box.isFocused()) {
			if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) { // Esc: leave the box, keep the window
				box.setFocused(false);
				s.setFocused(null);
				return false;
			}
			box.keyPressed(e);
			return false;
		}
		if (Keys.itemList != null && Keys.itemList.matches(e)) {
			hidden = !hidden;
			return false;
		}
		// "R" / "U" over a slot: recipe / used in (like other item list mods); never while typing elsewhere
		if (s.getFocused() instanceof EditBox) return true;
		Slot hov = ((AbstractContainerScreenAccessor) s).fatesofa$hoveredSlot();
		if (hov != null && hov.hasItem() && (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_R || e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_U)) {
			String key = Items.key(hov.getItem());
			if (key != null && Ofa.get().repo.items().get(key) != null) {
				Minecraft.getInstance().gui.setScreen(new RecipeScreen(s, key, e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_U));
				return false;
			}
		}
		return true;
	}

	void dimNonMatching(AbstractContainerScreen<?> s, GuiGraphicsExtractor g) {
		AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) s;
		String[] words = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
		for (Slot slot : s.getMenu().slots) {
			ItemStack st = slot.getItem();
			if (st.isEmpty()) continue;
			if (matches(st, words)) {
				int x = a.fatesofa$leftPos() + slot.x, y = a.fatesofa$topPos() + slot.y;
				Ui.ring(g, x - 1, y - 1, 18, 18, 1, Ui.accent());
				continue;
			}
			int x = a.fatesofa$leftPos() + slot.x, y = a.fatesofa$topPos() + slot.y;
			g.fill(x, y, x + 16, y + 16, 0xB0101010);
		}
	}

	static boolean matches(ItemStack st, String[] words) {
		String t = io.github.donttemptfate.fates.common.item.ItemFacts.of(st).searchText();
		for (String w : words) if (!t.contains(w)) return false;
		return true;
	}
}
