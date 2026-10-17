package io.github.donttemptfate.fatesofa.ui;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.text.Texts;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.data.Ingredient;
import io.github.donttemptfate.fatesofa.data.ItemRepo;
import io.github.donttemptfate.fatesofa.data.Price;
import io.github.donttemptfate.fatesofa.data.Recipe;
import io.github.donttemptfate.fatesofa.data.RepoItem;
import io.github.donttemptfate.fatesofa.hud.Rect;
import io.github.donttemptfate.fatesofa.items.Stacks;
import io.github.donttemptfate.fatesofa.items.Valuer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * An item's page: what it is worth, every way to get it (crafting, forge, NPC shop, Kat, drops) with
 * what the inputs cost now, and what it is used in. Click an ingredient to go to its page; Backspace or
 * the back arrow goes back.
 */
public final class RecipeScreen extends Screen {
	private final Screen parent;
	private final Deque<String[]> history = new ArrayDeque<>();
	private String key;
	private boolean usedIn;
	private int tab;
	private int scroll;
	private final Widgets.Hits hits = new Widgets.Hits();
	private int px, py, pw, ph;

	public RecipeScreen(Screen parent, String key, boolean usedIn) {
		super(Component.literal("Item"));
		this.parent = parent;
		this.key = key;
		this.usedIn = usedIn;
	}

	@Override
	protected void init() {
		pw = Math.min(this.width - 16, 360);
		ph = Math.min(this.height - 16, 260);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
	}

	void go(String next, boolean nextUsedIn) {
		if (Ofa.get().repo.items().get(next) == null) return;
		history.push(new String[] {key, usedIn ? "1" : "0", Integer.toString(tab)});
		key = next;
		usedIn = nextUsedIn;
		tab = 0;
		scroll = 0;
	}

	void back() {
		if (history.isEmpty()) {
			onClose();
			return;
		}
		String[] h = history.pop();
		key = h[0];
		usedIn = h[1].equals("1");
		tab = Integer.parseInt(h[2]);
		scroll = 0;
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
		Ofa o = Ofa.get();
		ItemRepo repo = o.repo.items();
		RepoItem it = repo.get(key);
		if (it == null) {
			g.centeredText(font, "Unknown item: " + key, this.width / 2, this.height / 2, Ui.GRAY);
			return;
		}
		long now = System.currentTimeMillis();
		int x = px + 10, y = py + 10, w = pw - 20;
		// header: back, icon, name, value
		Rect backR = new Rect(x, y, 12, 16);
		g.text(font, "←", x + 2, y + 4, backR.contains(mx, my) ? Ui.WHITE : Ui.GRAY, true);
		hits.add(backR, this::back, null);
		g.item(Stacks.of(it), x + 14, y);
		Ui.draw(g, Ui.refont(Texts.fromLegacy(it.displayName()), Ui.Role.NAME), Ui.Role.NAME, x + 34, y + 11);
		Rect iconR = new Rect(x + 14, y, 16, 16);
		if (iconR.contains(mx, my)) g.setTooltipForNextFrame(font, Stacks.of(it), mx, my);
		Price v = o.prices.value(key, now);
		String valueText = v == null ? "no price" : Fmt.shortNum(v.value()) + " · " + v.kind().label;
		g.text(font, valueText, x + w - font.width(valueText), y + 4, v == null ? Ui.DARK : Ui.GOLD, true);
		y += 22;

		// tabs: one per recipe, then "Used in"
		List<Recipe> recipes = it.recipes();
		List<String> tabs = new ArrayList<>();
		for (Recipe r : recipes) tabs.add(typeName(r.type()));
		int tx = x;
		for (int i = 0; i <= tabs.size(); i++) {
			boolean isUsed = i == tabs.size();
			String label = isUsed ? "Used in (" + repo.usedIn(key).size() + ")" : tabs.get(i);
			int tw = font.width(label) + 10;
			boolean active = isUsed ? usedIn : !usedIn && tab == i;
			Rect r = new Rect(tx, y, tw, 13);
			Widgets.button(g, r.x(), r.y(), r.w(), r.h(), label, r.contains(mx, my), active);
			final int idx = i;
			hits.add(r, () -> {
				usedIn = isUsed;
				if (!isUsed) tab = idx;
				scroll = 0;
			}, null);
			tx += tw + 3;
			if (tx > x + w - 40) break;
		}
		y += 18;
		if (!it.crafttext().isBlank()) {
			g.text(font, it.crafttext(), x, y, Ui.ORANGE, true);
			y += 11;
		}
		g.enableScissor(px + 4, y, px + pw - 4, py + ph - 4);
		if (usedIn) drawUsedIn(g, repo, x, y - scroll, w, mx, my);
		else if (recipes.isEmpty()) g.text(font, "No recipe in the item data. It may drop, be sold by an NPC not listed, or come from an event.", x, y, Ui.GRAY, true);
		else drawRecipe(g, recipes.get(Math.min(tab, recipes.size() - 1)), x, y - scroll, w, mx, my, now);
		g.disableScissor();
	}

	static String typeName(Recipe.Type t) {
		return switch (t) {
			case CRAFTING -> "Crafting";
			case FORGE -> "Forge";
			case NPC_SHOP -> "NPC shop";
			case TRADE -> "Trade";
			case KAT -> "Kat upgrade";
			case DROPS -> "Drop";
			case OTHER -> "Other";
		};
	}

	void drawRecipe(GuiGraphicsExtractor g, Recipe r, int x, int y, int w, int mx, int my, long now) {
		Ofa o = Ofa.get();
		ItemRepo repo = o.repo.items();
		int listX = x;
		if (r.type() == Recipe.Type.CRAFTING && r.slots() != null) {
			for (int i = 0; i < 9; i++) {
				int sx = x + (i % 3) * 18, sy = y + (i / 3) * 18;
				g.fill(sx, sy, sx + 17, sy + 17, 0x40000000);
				Ui.ring(g, sx, sy, 17, 17, 1, Ui.rule());
				Ingredient in = Ingredient.parse(r.slots()[i]);
				if (in != null) slotItem(g, repo, in, sx, sy, mx, my);
			}
			g.text(font, "→", x + 58, y + 22, Ui.GRAY, true);
			RepoItem out = repo.get(r.output());
			if (out != null) {
				ItemStack st = Stacks.of(out);
				st.setCount((int) Math.max(1, Math.min(99, r.outputCount())));
				g.item(st, x + 70, y + 18);
				g.itemDecorations(font, st, x + 70, y + 18);
			}
			listX = x + 96;
		}
		int ly = y;
		int lw = x + w - listX;
		Valuer.Cost cost = Valuer.cost(o.prices, r, now);
		for (Ingredient in : r.inputs()) {
			RepoItem ri = repo.get(in.key());
			String name = in.coins() ? "Coins" : ri == null ? in.key() : ri.plainName();
			String count = in.coins() ? Fmt.shortNum(in.count()) : Fmt.trimmed(in.count()) + "×";
			Rect row = new Rect(listX, ly, lw, 12);
			boolean over = row.contains(mx, my);
			if (over && ri != null) g.fill(row.x() - 1, row.y() - 1, row.right(), row.bottom(), 0x20FFFFFF);
			g.text(font, count + " " + name, listX, ly + 2, ri == null && !in.coins() ? Ui.GRAY : Ui.WHITE, true);
			String each = in.coins() ? "" : priceOf(in, now);
			if (!each.isEmpty()) g.text(font, each, listX + lw - font.width(each), ly + 2, Ui.GOLD, true);
			if (ri != null) hits.add(row, () -> go(in.key(), false), () -> go(in.key(), true));
			ly += 12;
		}
		ly = Math.max(ly, y + (r.type() == Recipe.Type.CRAFTING ? 56 : 0)) + 6;
		new Card.Rule("COST NOW", Ui.accent(), cost.missing() > 0 ? cost.missing() + " WITHOUT A PRICE" : null).draw(g, x, ly, w);
		ly += 12;
		g.text(font, "Inputs: " + Fmt.shortNum(cost.total()) + (cost.missing() > 0 ? " + unpriced items" : ""), x, ly, Ui.WHITE, true);
		Price outP = o.prices.value(r.output(), now);
		if (outP != null) {
			double outV = outP.value() * Math.max(1, r.outputCount());
			String line = "Output value: " + Fmt.shortNum(outV) + " (" + outP.kind().label + ")";
			g.text(font, line, x, ly + 11, Ui.GOLD, true);
			if (cost.missing() == 0 && cost.total() > 0) {
				double diff = outV - cost.total();
				g.text(font, "Value − inputs: " + Fmt.signedShort(diff), x, ly + 22, diff >= 0 ? Ui.GREEN : Ui.RED, true);
			}
		}
		ly += 34;
		if (r.durationS() > 0) {
			g.text(font, (r.type() == Recipe.Type.FORGE ? "Forge time: " : "Time: ") + Fmt.duration(r.durationS() * 1000), x, ly, Ui.AQUA, true);
			ly += 11;
		}
		if (r.note() != null) g.text(font, (r.type() == Recipe.Type.DROPS ? "Dropped by: " : "From: ") + Texts.plain(Texts.fromLegacy(r.note())), x, ly, Ui.GRAY, true);
	}

	String priceOf(Ingredient in, long now) {
		var q = Ofa.get().prices.bazaarQuote(in.key(), now);
		if (q != null && q.instantBuy() > 0) return Fmt.shortNum(q.instantBuy() * in.count());
		Price p = Ofa.get().prices.value(in.key(), now);
		return p == null ? "" : Fmt.shortNum(p.value() * in.count());
	}

	void slotItem(GuiGraphicsExtractor g, ItemRepo repo, Ingredient in, int sx, int sy, int mx, int my) {
		RepoItem ri = repo.get(in.key());
		if (ri == null) return;
		ItemStack st = Stacks.of(ri);
		st.setCount((int) Math.max(1, Math.min(99, in.count())));
		g.item(st, sx + 1, sy + 1);
		g.itemDecorations(font, st, sx + 1, sy + 1);
		Rect r = new Rect(sx, sy, 17, 17);
		if (r.contains(mx, my)) g.setTooltipForNextFrame(font, st, mx, my);
		hits.add(r, () -> go(in.key(), false), () -> go(in.key(), true));
	}

	void drawUsedIn(GuiGraphicsExtractor g, ItemRepo repo, int x, int y, int w, int mx, int my) {
		List<Recipe> uses = repo.usedIn(key);
		if (uses.isEmpty()) {
			g.text(font, "Not used in any recipe in the item data.", x, y, Ui.GRAY, true);
			return;
		}
		int cols = Math.max(1, w / 18);
		int i = 0;
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (Recipe r : uses) {
			if (!seen.add(r.output())) continue;
			RepoItem out = repo.get(r.output());
			if (out == null) continue;
			int cx = x + (i % cols) * 18, cy = y + (i / cols) * 18;
			Rect rr = new Rect(cx, cy, 17, 17);
			if (rr.contains(mx, my)) {
				g.fill(cx, cy, cx + 17, cy + 17, 0x40FFFFFF);
				g.setTooltipForNextFrame(font, Stacks.of(out), mx, my);
			}
			g.item(Stacks.of(out), cx + 1, cy + 1);
			final String k = out.key();
			hits.add(rr, () -> go(k, false), () -> go(k, true));
			i++;
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		Widgets.Hit h = hits.at(e.x(), e.y());
		if (h != null) {
			Runnable r = e.button() == 1 ? h.right() : h.left();
			if (r != null) r.run();
			return true;
		}
		if (e.button() == 3) {
			back();
			return true;
		}
		return super.mouseClicked(e, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double h, double v) {
		scroll = Math.max(0, scroll - (int) (v * 18));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == 259) { // Backspace
			back();
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

	static String lower(String s) {
		return s.toLowerCase(Locale.ROOT);
	}
}
