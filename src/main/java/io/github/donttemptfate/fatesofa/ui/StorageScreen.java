package io.github.donttemptfate.fatesofa.ui;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.features.StorageFeature;
import io.github.donttemptfate.fatesofa.hud.Rect;
import io.github.donttemptfate.fatesofa.items.Stacks;
import io.github.donttemptfate.fatesofa.storage.StorageCache;
import io.github.donttemptfate.fatesofa.storage.StoredItem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Every remembered ender chest page and backpack, one at a time or searched all together. */
public final class StorageScreen extends Screen {
	private final Screen parent;
	private EditBox search;
	private String pageId;
	private final Widgets.Hits hits = new Widgets.Hits();
	private int px, py, pw, ph;
	private final java.util.Map<StoredItem, ItemStack> stacks = new java.util.HashMap<>();
	private String lastQuery;
	private List<StorageCache.Hit> lastHits = List.of();

	ItemStack stack(StoredItem it) {
		return stacks.computeIfAbsent(it, Stacks::stored);
	}

	public StorageScreen(Screen parent, String query) {
		super(Component.literal("Storage"));
		this.parent = parent;
		this.initial = query == null ? "" : query;
	}

	private final String initial;

	@Override
	protected void init() {
		pw = Math.min(this.width - 16, 300);
		ph = Math.min(this.height - 16, 250);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		String keep = search == null ? initial : search.getValue();
		search = new EditBox(this.font, px + 90, py + 8, pw - 98, 14, Component.literal("Search storage"));
		search.setHint(Component.literal("Search everything stored…"));
		search.setValue(keep);
		addRenderableWidget(search);
	}

	StorageCache cache() {
		return StorageFeature.INSTANCE == null ? null : StorageFeature.INSTANCE.cache();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractBackground(g, mouseX, mouseY, delta);
		Ui.frame(g, px, py, pw, ph, 0.94f);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		super.extractRenderState(g, mx, my, delta);
		hits.clear();
		StorageCache c = cache();
		g.text(font, "STORAGE", px + 10, py + 11, Ui.accent(), true);
		if (c == null || c.pages().isEmpty()) {
			int ty = py + 34;
			for (String line : List.of("Nothing remembered yet.", "",
					"Open each ender chest page and backpack once (on any island). OFA remembers what's inside, "
							+ "so you can browse and search it here from anywhere: /ofa storage, or /ofa find <item>.")) {
				for (var seq : font.split(Component.literal(line), pw - 20)) {
					g.text(font, seq, px + 10, ty, line.startsWith("Nothing") ? Ui.WHITE : Ui.GRAY, true);
					ty += 10;
				}
				if (line.isEmpty()) ty += 2;
			}
			return;
		}
		List<StorageCache.Page> pages = c.pages();
		int ly = py + 30;
		for (StorageCache.Page p : pages) {
			Rect r = new Rect(px + 8, ly, 76, 11);
			boolean active = p.id().equals(pageId) && search.getValue().isBlank();
			if (active) g.fill(r.x(), r.y(), r.right(), r.bottom(), (Ui.accent() & 0xFFFFFF) | 0x40000000);
			else if (r.contains(mx, my)) g.fill(r.x(), r.y(), r.right(), r.bottom(), 0x20FFFFFF);
			g.text(font, StorageCache.label(p.id()), r.x() + 3, r.y() + 2, active ? Ui.WHITE : Ui.GRAY, false);
			hits.add(r, () -> {
				pageId = p.id();
				search.setValue("");
			}, null);
			ly += 12;
			if (ly > py + ph - 14) break;
		}
		int gx = px + 92, gy = py + 30;
		ItemStack hovered = null;
		String hoverNote = null;
		if (!search.getValue().isBlank()) {
			if (!search.getValue().equals(lastQuery)) {
				lastQuery = search.getValue();
				lastHits = c.search(lastQuery);
			}
			List<StorageCache.Hit> hitsFound = lastHits;
			g.text(font, hitsFound.size() + " found", gx, gy, Ui.GRAY, true);
			gy += 12;
			int cols = Math.max(1, (pw - 100) / 18);
			for (int i = 0; i < hitsFound.size(); i++) {
				int x = gx + (i % cols) * 18, y = gy + (i / cols) * 18;
				if (y > py + ph - 20) break;
				StoredItem it = hitsFound.get(i).item();
				ItemStack st = stack(it);
				g.item(st, x, y);
				g.itemDecorations(font, st, x, y);
				if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
					hovered = st;
					hoverNote = StorageCache.label(hitsFound.get(i).page().id()) + ", slot " + (it.slot() + 1);
				}
			}
		} else {
			StorageCache.Page page = null;
			for (StorageCache.Page p : pages) if (p.id().equals(pageId)) page = p;
			if (page == null) {
				page = pages.get(0);
				pageId = page.id();
			}
			g.text(font, StorageCache.label(page.id()) + " · seen " + Fmt.duration(System.currentTimeMillis() - page.updatedAt()) + " ago", gx, gy, Ui.GRAY, true);
			gy += 12;
			for (StoredItem it : page.items()) {
				int x = gx + (it.slot() % 9) * 18, y = gy + (it.slot() / 9) * 18;
				g.fill(x - 1, y - 1, x + 17, y + 17, 0x30000000);
				ItemStack st = stack(it);
				g.item(st, x, y);
				g.itemDecorations(font, st, x, y);
				if (mx >= x && mx < x + 16 && my >= y && my < y + 16) hovered = st;
			}
		}
		if (hovered != null) {
			List<Component> lines = new java.util.ArrayList<>(Screen.getTooltipFromItem(this.minecraft, hovered));
			if (hoverNote != null) lines.add(Component.literal(hoverNote).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
			g.setComponentTooltipForNextFrame(font, lines, mx, my);
		}
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
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
