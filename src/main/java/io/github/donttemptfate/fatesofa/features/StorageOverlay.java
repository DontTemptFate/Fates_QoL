package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.text.Strip;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.game.Game;
import io.github.donttemptfate.fatesofa.hud.Rect;
import io.github.donttemptfate.fatesofa.mixin.AbstractContainerScreenAccessor;
import io.github.donttemptfate.fatesofa.storage.PageValues;
import io.github.donttemptfate.fatesofa.storage.StorageCache;
import io.github.donttemptfate.fatesofa.storage.StoredItem;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * In Hypixel's own Storage menu and ender chest / backpack pages: a panel beside the window with every page
 * and what it's worth, a search across all of them (matches light up in the open page, and each page shows
 * how many it holds), and one click on a page opens it — one command per click.
 */
public final class StorageOverlay extends Feature {
	public static StorageOverlay INSTANCE;

	final Setting<Boolean> highlight = setting(Setting.bool("storageoverlay.highlight", "Highlight search matches in the page", true).group("Search highlight"));
	final Setting<Integer> matchColour = setting(Setting.color("storageoverlay.matchColour", "Match outline colour", 0xFFFF55FF).rainbow().group("Search highlight"));
	final Setting<Integer> dim = setting(Setting.integer("storageoverlay.dim", "Darken other items %", 63, 0, 95).group("Search highlight"));
	final Setting<Boolean> showValues = setting(Setting.bool("storageoverlay.values", "Page values", true).group("Panel"));
	final Setting<Boolean> showTotal = setting(Setting.bool("storageoverlay.total", "Total at the top", true).group("Panel"));

	static final int W = 112;
	private final Map<Screen, EditBox> boxes = new WeakHashMap<>();
	private PageValues values;
	/** A search to carry into the next page that opens (from OFA's storage screen). */
	private String carryQuery;
	private long carryUntil;

	public StorageOverlay() {
		super("storage_overlay", "Storage panel in Hypixel's menus",
				"Beside Hypixel's Storage menu and every ender chest page or backpack: all your pages with their value, one search across "
						+ "them (matches light up), and a click on a page opens it.",
				Category.STORAGE, true, Risk.SAFE);
		INSTANCE = this;
	}

	@Override
	public Set<String> keywords() {
		return Set.of("ender chest", "backpack", "search", "storage", "find");
	}

	@Override
	public void init() {
		values = new PageValues(Ofa.get().prices);
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
			guard(() -> attach(mc, acs));
		});
	}

	static boolean isStorageWindow(String title) {
		return title.equals("Storage") || StorageCache.pageId(title) != null;
	}

	StorageCache cache() {
		return StorageFeature.INSTANCE == null ? null : StorageFeature.INSTANCE.cache();
	}

	/** True when the panel is showing for this window (the chest value card then stays out of the way). */
	public boolean covers(AbstractContainerScreen<?> s) {
		return active() && boxes.containsKey(s) && room(s);
	}

	static boolean room(AbstractContainerScreen<?> s) {
		return edge(s) >= W + 10;
	}

	/** The window's left edge, minus the quick-button column when that sits there. */
	static int edge(AbstractContainerScreen<?> s) {
		int reserve = QuickCommandsFeature.INSTANCE == null ? 0 : QuickCommandsFeature.INSTANCE.leftReserve(s);
		return ((AbstractContainerScreenAccessor) s).fatesofa$leftPos() - reserve;
	}

	/** Opens a stored page in the game (one command) and carries {@code query} into it so the match is lit up. */
	public void open(String pageId, String query) {
		String cmd = StorageCache.openCommand(pageId);
		if (cmd == null) return;
		carryQuery = query == null || query.isBlank() ? null : query;
		carryUntil = System.currentTimeMillis() + 20_000;
		if (!Game.runCommand(cmd) && io.github.donttemptfate.fatesofa.game.Dev.test()) Game.info("(test) would run /" + cmd);
	}

	void attach(Minecraft mc, AbstractContainerScreen<?> s) {
		boolean onSb = Ofa.get().state.onSkyBlock() || io.github.donttemptfate.fatesofa.game.Dev.test();
		String title = Strip.clean(s.getTitle().getString());
		if (!onSb || !isStorageWindow(title) || !room(s)) return;
		AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) s;
		int x = edge(s) - W - 6, y = a.fatesofa$topPos();
		EditBox box = new EditBox(mc.font, x + 4, y + 24, W - 8, 13, Component.literal("Search storage"));
		box.setHint(Component.literal("Search all pages…").withStyle(ChatFormatting.DARK_GRAY));
		box.setMaxLength(60);
		if (carryQuery != null && System.currentTimeMillis() < carryUntil) box.setValue(carryQuery);
		carryQuery = null;
		Screens.getWidgets(s).add(box);
		boxes.put(s, box);

		ScreenEvents.afterExtract(s).register((sc, g, mx, my, d) -> guard(() -> draw(s, box, g, mx, my, d)));
		ScreenMouseEvents.allowMouseClick(s).register((sc, e) -> guard(() -> !click(s, box, e.x(), e.y()), true));
		ScreenKeyboardEvents.allowKeyPress(s).register((sc, e) -> guard(() -> {
			if (!box.isFocused()) return true;
			if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) { // Esc leaves the box, keeps the window
				box.setFocused(false);
				s.setFocused(null);
				return false;
			}
			box.keyPressed(e);
			return false; // typed letters don't close the window or move items
		}, true));
	}

	String[] words(EditBox box) {
		String q = box.getValue().toLowerCase(Locale.ROOT).trim();
		return q.isEmpty() ? null : q.split("\\s+");
	}

	static boolean matches(StoredItem it, String[] words) {
		String t = it.searchText();
		for (String w : words) if (!t.contains(w)) return false;
		return true;
	}

	/** Panel rows: [y, page index] for the clickable page list. */
	void draw(AbstractContainerScreen<?> s, EditBox box, GuiGraphicsExtractor g, int mx, int my, float delta) {
		AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) s;
		int x = edge(s) - W - 6, y = a.fatesofa$topPos();
		StorageCache c = cache();
		List<StorageCache.Page> pages = c == null ? List.of() : c.pages();
		String[] words = words(box);
		String here = StorageCache.pageId(Strip.clean(s.getTitle().getString()));

		int listTop = y + 42, rowH = 11;
		int bottom = Math.min(s.height - 6, listTop + Math.max(1, pages.size()) * rowH + 6);
		Ui.frame(g, x, y, W, bottom - y, 0.94f);
		box.extractRenderState(g, mx, my, delta); // the frame was drawn over the box: put it back on top
		double all = 0;
		for (StorageCache.Page p : pages) all += values.of(p);
		Ui.draw(g, Ui.t("STORAGE", Ui.Role.LABEL, Ui.accent()), Ui.Role.LABEL, x + 6, y + 11);
		if (all > 0 && on(showTotal)) {
			var t = Ui.t(Fmt.shortNum(all), Ui.Role.VALUE, Ui.GOLD);
			Ui.draw(g, t, Ui.Role.VALUE, x + W - 6 - Ui.width(t, Ui.Role.VALUE), y + 12);
		}
		if (words != null) {
			int n = 0;
			for (StorageCache.Page p : pages) for (StoredItem it : p.items()) if (matches(it, words)) n++;
			Ui.draw(g, Ui.t(n + " found", Ui.Role.SMALL, n > 0 ? Ui.GREEN : Ui.GRAY), Ui.Role.SMALL, x + 6, y + 20);
		}
		if (pages.isEmpty()) {
			g.text(Minecraft.getInstance().font, "Open each page once.", x + 6, listTop, Ui.GRAY, false);
		}
		int ry = listTop;
		for (StorageCache.Page p : pages) {
			if (ry + rowH > bottom) break;
			Rect r = new Rect(x + 4, ry, W - 8, rowH);
			boolean current = p.id().equals(here), over = r.contains(mx, my);
			if (current) g.fill(r.x(), r.y(), r.right(), r.bottom(), (Ui.accent() & 0xFFFFFF) | 0x30000000);
			else if (over) g.fill(r.x(), r.y(), r.right(), r.bottom(), 0x20FFFFFF);
			int found = 0;
			if (words != null) for (StoredItem it : p.items()) if (matches(it, words)) found++;
			String label = shortLabel(p.id());
			int col = current ? Ui.WHITE : words != null && found == 0 ? Ui.DARK : over ? 0xFFDDDDDD : Ui.GRAY;
			g.text(Minecraft.getInstance().font, label, r.x() + 3, r.y() + 2, col, false);
			String right = words != null ? (found > 0 ? found + " found" : "") : on(showValues) ? valueText(p) : "";
			if (!right.isEmpty()) {
				int rc = words != null ? Ui.GREEN : Ui.GOLD;
				g.text(Minecraft.getInstance().font, right, r.right() - 3 - Minecraft.getInstance().font.width(right), r.y() + 2, rc, false);
			}
			ry += rowH;
		}

		// matches in the open window
		if (words != null && on(highlight)) {
			for (Slot sl : s.getMenu().slots) {
				if (sl.container instanceof Inventory || !sl.hasItem()) continue;
				ItemStack st = sl.getItem();
				int sx = a.fatesofa$leftPos() + sl.x, sy = a.fatesofa$topPos() + sl.y;
				if (ItemListFeature.matches(st, words))
					Ui.ring(g, sx - 1, sy - 1, 18, 18, 1, io.github.donttemptfate.fatesofa.config.Values.color(get(matchColour), on(matchColour.rainbow), System.currentTimeMillis()));
				else if (get(dim) > 0) g.fill(sx, sy, sx + 16, sy + 16, (Math.round(get(dim) * 2.55f) << 24) | 0x101010);
			}
		}
		// tooltip for page rows
		ry = listTop;
		for (StorageCache.Page p : pages) {
			if (ry + rowH > bottom) break;
			if (new Rect(x + 4, ry, W - 8, rowH).contains(mx, my) && !p.id().equals(here)) {
				g.setComponentTooltipForNextFrame(Minecraft.getInstance().font, List.of(
						Component.literal(StorageCache.label(p.id())).withStyle(ChatFormatting.WHITE),
						Component.literal(p.items().size() + " stacks · " + valueText(p)).withStyle(ChatFormatting.GOLD),
						Component.literal("Click: runs /" + StorageCache.openCommand(p.id())).withStyle(ChatFormatting.LIGHT_PURPLE)), mx, my);
			}
			ry += rowH;
		}
	}

	String valueText(StorageCache.Page p) {
		double v = values.of(p);
		return v > 0 ? Fmt.shortNum(v) : p.items().isEmpty() ? "empty" : "—";
	}

	static String shortLabel(String id) {
		if (id.startsWith("ender:")) return "Ender " + id.substring(6);
		if (id.startsWith("backpack:")) return "Backpack " + id.substring(9);
		return id;
	}

	boolean click(AbstractContainerScreen<?> s, EditBox box, double mx, double my) {
		AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) s;
		int x = edge(s) - W - 6, y = a.fatesofa$topPos();
		if (mx < x || mx >= x + W || my < y) return false;
		StorageCache c = cache();
		List<StorageCache.Page> pages = c == null ? List.of() : c.pages();
		int listTop = y + 42, rowH = 11;
		int i = (int) Math.floor((my - listTop) / rowH);
		if (my >= listTop && i >= 0 && i < pages.size() && mx >= x + 4 && mx < x + W - 4) {
			StorageCache.Page p = pages.get(i);
			String here = StorageCache.pageId(Strip.clean(s.getTitle().getString()));
			if (!p.id().equals(here)) open(p.id(), box.getValue());
			return true;
		}
		return my < listTop + pages.size() * rowH + 6 && !(mx >= box.getX() && mx < box.getX() + box.getWidth() && my >= box.getY() && my < box.getY() + box.getHeight());
	}
}
