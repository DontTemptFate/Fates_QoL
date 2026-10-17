package io.github.donttemptfate.fatesofa.ui;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.features.StorageFeature;
import io.github.donttemptfate.fatesofa.hud.Rect;
import io.github.donttemptfate.fatesofa.items.Stacks;
import io.github.donttemptfate.fatesofa.items.Valuer;
import io.github.donttemptfate.fatesofa.storage.StorageCache;
import io.github.donttemptfate.fatesofa.storage.StoredItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every remembered ender chest page and backpack: a page list with what each is worth, the chosen page as
 * a real slot grid with its most valuable items beside it, or one search across all of them, grouped by page.
 */
public final class StorageScreen extends Screen {
	static final int CELL = 18, SIDE_W = 112, HEADER_H = 30, FOOTER_H = 14;

	private final Screen parent;
	private final String initial;
	private EditBox search;
	private String pageId;
	private final Widgets.Hits hits = new Widgets.Hits();
	private int px, py, pw, ph;
	private int sideScroll, mainScroll;
	private final Map<StoredItem, ItemStack> stacks = new HashMap<>();
	private final Map<StoredItem, Double> values = new IdentityHashMap<>();
	private long valuesAt;
	private String lastQuery;
	private List<StorageCache.Hit> lastHits = List.of();

	public StorageScreen(Screen parent, String query) {
		super(Component.literal("Storage"));
		this.parent = parent;
		this.initial = query == null ? "" : query;
	}

	ItemStack stack(StoredItem it) {
		return stacks.computeIfAbsent(it, Stacks::stored);
	}

	/** What a stored stack is worth now (NaN when there's no price). Refreshed every few seconds. */
	double value(StoredItem it) {
		long now = System.currentTimeMillis();
		if (now - valuesAt > 5000) {
			values.clear();
			valuesAt = now;
		}
		return values.computeIfAbsent(it, i -> {
			Valuer.Valued v = Valuer.value(Ofa.get().prices, i.key(), i.name(), i.count(), now);
			return v == null ? Double.NaN : v.total();
		});
	}

	double pageValue(StorageCache.Page p) {
		double t = 0;
		for (StoredItem it : p.items()) {
			double v = value(it);
			if (!Double.isNaN(v)) t += v;
		}
		return t;
	}

	@Override
	protected void init() {
		pw = Math.min(this.width - 16, 392);
		ph = Math.min(this.height - 16, 236);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		String keep = search == null ? initial : search.getValue();
		int sx = px + 10 + SIDE_W + 4;
		search = new EditBox(this.font, sx, py + 9, px + pw - 10 - sx, 14, Component.literal("Search storage"));
		search.setValue(keep);
		search.setResponder(v -> mainScroll = 0);
		addRenderableWidget(search);
		setFocused(search); // type straight away
	}

	StorageCache cache() {
		return StorageFeature.INSTANCE == null ? null : StorageFeature.INSTANCE.cache();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractBackground(g, mouseX, mouseY, delta);
		Ui.frame(g, px, py, pw, ph, 0.95f);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		super.extractRenderState(g, mx, my, delta);
		hits.clear();
		StorageCache c = cache();
		List<StorageCache.Page> pages = c == null ? List.of() : c.pages();
		if (search.getValue().isEmpty()) g.text(font, "Search everything stored…", search.getX() + 4, search.getY() + 3, 0xFF5A5A6A, false);
		// header: title and the total of everything remembered
		Ui.draw(g, Ui.t("STORAGE", Ui.Role.TITLE, Ui.accent()), Ui.Role.TITLE, px + 10, py + 19);
		if (!pages.isEmpty()) {
			double all = 0;
			for (StorageCache.Page p : pages) all += pageValue(p);
			Ui.draw(g, Ui.t(Fmt.shortNum(all) + " total", Ui.Role.SMALL, Ui.GOLD), Ui.Role.SMALL, px + 10, py + 28);
		}
		g.fill(px + 8, py + HEADER_H + 2, px + pw - 8, py + HEADER_H + 3, Ui.rule());
		int top = py + HEADER_H + 7, bottom = py + ph - FOOTER_H - 4;

		if (pages.isEmpty()) {
			empty(g, top);
			footer(g, "Ender chest pages and backpacks are remembered when you open them.");
			return;
		}

		ItemStack[] hovered = {null};
		String[] note = {null};
		sidebar(g, pages, top, bottom, mx, my);
		g.fill(px + 10 + SIDE_W, top - 2, px + 11 + SIDE_W, bottom, Ui.rule());
		int mainX = px + 10 + SIDE_W + 8, mainW = px + pw - 10 - mainX;
		g.enableScissor(mainX - 2, top - 2, px + pw - 6, bottom);
		if (search.getValue().isBlank()) page(g, pages, mainX, top - mainScroll, mainW, mx, my, hovered);
		else results(g, c, mainX, top - mainScroll, mainW, bottom, mx, my, hovered, note);
		g.disableScissor();
		footer(g, search.getValue().isBlank() ? "Click a page · type to search all pages · /ofa find <item>"
				: "Click a page name to open it · Esc clears the search");

		if (hovered[0] != null && my >= top && my < bottom) {
			List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(this.minecraft, hovered[0]));
			if (note[0] != null) lines.add(Component.literal(note[0]).withStyle(ChatFormatting.LIGHT_PURPLE));
			g.setComponentTooltipForNextFrame(font, lines, mx, my);
		}
	}

	// ---- parts ----

	void sidebar(GuiGraphicsExtractor g, List<StorageCache.Page> pages, int top, int bottom, int mx, int my) {
		int x = px + 8, w = SIDE_W;
		boolean searching = !search.getValue().isBlank();
		int rows = 0;
		String lastGroup = null;
		for (StorageCache.Page p : pages) {
			String group = p.id().startsWith("ender:") ? "ENDER CHEST" : "BACKPACKS";
			if (!group.equals(lastGroup)) rows += 2;
			lastGroup = group;
			rows++;
		}
		int contentH = rows * 12;
		sideScroll = Math.max(0, Math.min(sideScroll, contentH - (bottom - top)));
		g.enableScissor(x, top - 2, x + w + 2, bottom);
		int y = top - sideScroll;
		lastGroup = null;
		for (StorageCache.Page p : pages) {
			String group = p.id().startsWith("ender:") ? "ENDER CHEST" : "BACKPACKS";
			if (!group.equals(lastGroup)) {
				if (lastGroup != null) y += 6;
				Ui.draw(g, Ui.t(group, Ui.Role.LABEL, Ui.GRAY), Ui.Role.LABEL, x + 2, y + 7);
				y += 12;
				lastGroup = group;
			}
			Rect r = new Rect(x, y, w, 12);
			boolean active = !searching && p.id().equals(pageId);
			boolean over = r.contains(mx, my) && my >= top && my < bottom;
			if (active) {
				g.fill(r.x(), r.y(), r.right(), r.bottom(), (Ui.accent() & 0xFFFFFF) | 0x30000000);
				g.fill(r.x(), r.y(), r.x() + 2, r.bottom(), Ui.accent());
			} else if (over) g.fill(r.x(), r.y(), r.right(), r.bottom(), 0x18FFFFFF);
			g.text(font, shortLabel(p.id()), r.x() + 6, r.y() + 2, active ? Ui.WHITE : over ? 0xFFDDDDDD : Ui.GRAY, false);
			double v = pageValue(p);
			String vs = v > 0 ? Fmt.shortNum(v) : p.items().isEmpty() ? "empty" : "—";
			g.text(font, vs, r.right() - 3 - font.width(vs), r.y() + 2, v > 0 ? Ui.GOLD : Ui.DARK, false);
			if (r.y() + r.h() > top && r.y() < bottom) hits.add(r, () -> open(p.id()), null);
			y += 12;
		}
		g.disableScissor();
		if (contentH > bottom - top) { // scroll thumb
			int trackH = bottom - top, thumb = Math.max(12, trackH * trackH / contentH);
			int ty = top + (trackH - thumb) * sideScroll / Math.max(1, contentH - trackH);
			g.fill(x + w + 1, ty, x + w + 3, ty + thumb, (Ui.accent() & 0xFFFFFF) | 0x80000000);
		}
	}

	void open(String id) {
		pageId = id;
		mainScroll = 0;
		search.setValue("");
	}

	void page(GuiGraphicsExtractor g, List<StorageCache.Page> pages, int x, int y, int w, int mx, int my, ItemStack[] hovered) {
		StorageCache.Page page = null;
		for (StorageCache.Page p : pages) if (p.id().equals(pageId)) page = p;
		if (page == null) {
			page = pages.get(0);
			pageId = page.id();
		}
		// page header: name, when it was seen, its value
		Ui.draw(g, Ui.t(StorageCache.label(page.id()), Ui.Role.NAME, Ui.WHITE), Ui.Role.NAME, x, y + 8);
		String sub = page.items().size() + " stacks · seen " + Fmt.duration(System.currentTimeMillis() - page.updatedAt()) + " ago";
		Ui.draw(g, Ui.t(sub, Ui.Role.SMALL, Ui.GRAY), Ui.Role.SMALL, x, y + 18);
		double total = pageValue(page);
		if (total > 0) {
			Component big = Ui.t(Fmt.shortNum(total), Ui.Role.HERO_S, Ui.GOLD);
			Ui.draw(g, big, Ui.Role.HERO_S, x + w - Ui.width(big, Ui.Role.HERO_S), y + 11);
			Component lbl = Ui.t("PAGE VALUE", Ui.Role.LABEL, Ui.GRAY);
			Ui.draw(g, lbl, Ui.Role.LABEL, x + w - Ui.width(lbl, Ui.Role.LABEL), y + 19);
		}
		int gy = y + 26;

		// the grid, shaped like the real page
		int rows = rows(page);
		int offset = rowOffset(page, rows);
		List<StoredItem> ranked = new ArrayList<>(page.items());
		ranked.removeIf(it -> Double.isNaN(value(it)) || value(it) <= 0);
		ranked.sort(Comparator.comparingDouble(this::value).reversed());
		List<StoredItem> best = ranked.subList(0, Math.min(3, ranked.size()));
		Map<Integer, StoredItem> bySlot = new HashMap<>();
		for (StoredItem it : page.items()) bySlot.put(it.slot() - offset, it);
		for (int i = 0; i < rows * 9; i++) {
			int sx = x + (i % 9) * CELL, sy = gy + (i / 9) * CELL;
			slot(g, sx, sy);
			StoredItem it = bySlot.get(i);
			if (it == null) continue;
			ItemStack st = stack(it);
			if (best.contains(it)) Ui.ring(g, sx, sy, CELL, CELL, 1, 0xC0FFAA00);
			g.item(st, sx + 1, sy + 1);
			g.itemDecorations(font, st, sx + 1, sy + 1);
			if (mx >= sx && mx < sx + CELL && my >= sy && my < sy + CELL) {
				g.fill(sx + 1, sy + 1, sx + CELL - 1, sy + CELL - 1, 0x40FFFFFF);
				hovered[0] = st;
			}
		}

		// beside the grid: the page's most valuable items
		int cx = x + 9 * CELL + 10, cw = x + w - cx;
		if (cw >= 64 && !ranked.isEmpty()) {
			Ui.draw(g, Ui.t("MOST VALUABLE", Ui.Role.LABEL, Ui.GRAY), Ui.Role.LABEL, cx, gy + 6);
			List<Valuer.Valued> merged = new ArrayList<>();
			for (StoredItem it : ranked) {
				Valuer.Valued v = Valuer.value(Ofa.get().prices, it.key(), it.name(), it.count(), System.currentTimeMillis());
				if (v != null) merged.add(v);
			}
			int ry = gy + 11;
			for (Valuer.Valued v : Valuer.top(merged, Math.min(5, rows + 1))) {
				StoredItem src = null;
				for (StoredItem it : ranked) if (v.key().equals(it.key())) {
					src = it;
					break;
				}
				if (src == null) continue;
				ItemStack st = stack(src);
				g.fill(cx, ry, cx + cw, ry + 17, 0x14FFFFFF);
				g.item(st, cx + 1, ry);
				String val = Fmt.shortNum(v.total());
				g.text(font, val, cx + 21, ry + 5, Ui.GOLD, false);
				if (v.count() > 1) {
					String cnt = v.count() + "×";
					int cxr = cx + cw - 3 - font.width(cnt);
					if (cxr > cx + 21 + font.width(val) + 3) g.text(font, cnt, cxr, ry + 5, Ui.DARK, false);
				}
				if (mx >= cx && mx < cx + cw && my >= ry && my < ry + 17) hovered[0] = st;
				ry += 20;
			}
		}
		if (page.items().isEmpty()) g.text(font, "This page was empty when you last opened it.", x, gy + rows * CELL + 6, Ui.GRAY, false);
	}

	void results(GuiGraphicsExtractor g, StorageCache c, int x, int y, int w, int bottom, int mx, int my, ItemStack[] hovered, String[] note) {
		if (!search.getValue().equals(lastQuery)) {
			lastQuery = search.getValue();
			lastHits = c.search(lastQuery);
		}
		Map<String, List<StorageCache.Hit>> byPage = new LinkedHashMap<>();
		double worth = 0;
		for (StorageCache.Hit h : lastHits) {
			byPage.computeIfAbsent(h.page().id(), k -> new ArrayList<>()).add(h);
			double v = value(h.item());
			if (!Double.isNaN(v)) worth += v;
		}
		int stacksFound = lastHits.size();
		String head = stacksFound == 0 ? "Nothing matches \"" + search.getValue().trim() + "\""
				: stacksFound + (stacksFound == 1 ? " stack" : " stacks") + " in " + byPage.size() + (byPage.size() == 1 ? " page" : " pages");
		Ui.draw(g, Ui.t(head, Ui.Role.NAME, Ui.WHITE), Ui.Role.NAME, x, y + 8);
		if (worth > 0) {
			Component big = Ui.t(Fmt.shortNum(worth), Ui.Role.HERO_S, Ui.GOLD);
			Ui.draw(g, big, Ui.Role.HERO_S, x + w - Ui.width(big, Ui.Role.HERO_S), y + 11);
			Component lbl = Ui.t("FOUND VALUE", Ui.Role.LABEL, Ui.GRAY);
			Ui.draw(g, lbl, Ui.Role.LABEL, x + w - Ui.width(lbl, Ui.Role.LABEL), y + 19);
		}
		int gy = y + 26, perRow = Math.max(1, w / CELL);
		for (Map.Entry<String, List<StorageCache.Hit>> e : byPage.entrySet()) {
			String label = StorageCache.label(e.getKey());
			Rect r = new Rect(x, gy, font.width(label) + 6, 11);
			boolean over = r.contains(mx, my) && my < bottom;
			g.text(font, label, x, gy + 1, over ? Ui.WHITE : Ui.accent(), false);
			g.text(font, "  " + e.getValue().size() + " found", x + font.width(label), gy + 1, Ui.DARK, false);
			if (over) g.fill(x, gy + 10, x + font.width(label), gy + 11, Ui.WHITE);
			if (gy < bottom) hits.add(r, () -> open(e.getKey()), null);
			gy += 12;
			List<StorageCache.Hit> list = e.getValue();
			for (int i = 0; i < list.size(); i++) {
				int sx = x + (i % perRow) * CELL, sy = gy + (i / perRow) * CELL;
				slot(g, sx, sy);
				StoredItem it = list.get(i).item();
				ItemStack st = stack(it);
				g.item(st, sx + 1, sy + 1);
				g.itemDecorations(font, st, sx + 1, sy + 1);
				if (mx >= sx && mx < sx + CELL && my >= sy && my < sy + CELL && my < bottom) {
					g.fill(sx + 1, sy + 1, sx + CELL - 1, sy + CELL - 1, 0x40FFFFFF);
					hovered[0] = st;
					note[0] = label + ", slot " + (it.slot() + 1);
				}
			}
			gy += ((list.size() + perRow - 1) / perRow) * CELL + 6;
		}
		mainMax = Math.max(0, gy + mainScroll - bottom);
	}

	private int mainMax;

	/** An inset slot in the theme's colours. */
	void slot(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x, y, x + CELL, y + CELL, 0xFF000000 | (Ui.track() & 0xFFFFFF));
		g.fill(x, y, x + CELL - 1, y + 1, 0x60000000);
		g.fill(x, y + 1, x + 1, y + CELL - 1, 0x60000000);
		g.fill(x + 1, y + CELL - 1, x + CELL, y + CELL, (Ui.rule() & 0xFFFFFF) | 0xA0000000);
		g.fill(x + CELL - 1, y + 1, x + CELL, y + CELL - 1, (Ui.rule() & 0xFFFFFF) | 0xA0000000);
	}

	void empty(GuiGraphicsExtractor g, int top) {
		int w = pw - 48;
		ItemStack chest = new ItemStack(Items.ENDER_CHEST);
		g.item(chest, px + pw / 2 - 8, top + 10);
		int y = top + 34;
		Component t = Ui.t("Nothing remembered yet", Ui.Role.NAME, Ui.WHITE);
		Ui.draw(g, t, Ui.Role.NAME, px + (pw - Ui.width(t, Ui.Role.NAME)) / 2, y + 8);
		y += 18;
		String text = "Open each ender chest page and backpack once, on any island. OFA remembers what's inside, so you can "
				+ "browse, value and search all of it here from anywhere.";
		for (var seq : font.split(Component.literal(text), w)) {
			g.text(font, seq, px + (pw - font.width(seq)) / 2, y, Ui.GRAY, false);
			y += 10;
		}
		y += 8;
		for (String cmd : List.of("/ofa storage  —  this screen", "/ofa find <item>  —  search straight away")) {
			g.text(font, cmd, px + (pw - font.width(cmd)) / 2, y, Ui.accent(), false);
			y += 11;
		}
	}

	void footer(GuiGraphicsExtractor g, String text) {
		int fy = py + ph - FOOTER_H - 1;
		g.fill(px + 8, fy - 2, px + pw - 8, fy - 1, Ui.rule());
		Ui.draw(g, Ui.t(text, Ui.Role.SMALL, Ui.DARK), Ui.Role.SMALL, px + 10, fy + 9);
	}

	// ---- layout helpers ----

	static String shortLabel(String id) {
		if (id.startsWith("ender:")) return "Page " + id.substring(6);
		if (id.startsWith("backpack:")) return "Backpack " + id.substring(9);
		return id;
	}

	/** Rows of the real window: ender chest pages hold 45, backpacks by size, else whatever the slots need. */
	static int rows(StorageCache.Page p) {
		if (p.id().startsWith("ender:")) return 5;
		String t = p.title() == null ? "" : p.title().toLowerCase(Locale.ROOT);
		int named = t.contains("small") ? 1 : t.contains("medium") ? 2 : t.contains("large") ? 3 : t.contains("greater") ? 4 : t.contains("jumbo") ? 5 : 0;
		if (named > 0) return named;
		int max = 0;
		for (StoredItem it : p.items()) max = Math.max(max, it.slot());
		return Math.min(6, max / 9 + 1);
	}

	/** If items sit below the page's size (Hypixel put a frame row on top), shift the grid up by that many rows. */
	static int rowOffset(StorageCache.Page p, int rows) {
		int max = -1;
		for (StoredItem it : p.items()) max = Math.max(max, it.slot());
		return max >= rows * 9 ? (max / 9 - rows + 1) * 9 : 0;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		if (mx < px + 10 + SIDE_W) sideScroll = Math.max(0, sideScroll - (int) (v * 24));
		else if (!search.getValue().isBlank()) mainScroll = Math.max(0, Math.min(mainMax, mainScroll - (int) (v * 24)));
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (super.mouseClicked(e, doubleClick)) return true;
		Widgets.Hit h = hits.at(e.x(), e.y());
		if (h != null && h.left() != null) {
			h.left().run();
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
		if (e.key() == 256 && !search.getValue().isEmpty()) { // Esc clears the search first
			search.setValue("");
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
