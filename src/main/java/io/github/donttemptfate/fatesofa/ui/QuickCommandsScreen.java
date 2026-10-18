package io.github.donttemptfate.fatesofa.ui;

import io.github.donttemptfate.fatescommon.ui.Ui;
import io.github.donttemptfate.fatesofa.features.QuickCommandsFeature;
import io.github.donttemptfate.fatesofa.game.Game;
import io.github.donttemptfate.fatesofa.game.Keys;
import io.github.donttemptfate.fatesofa.hud.Rect;
import io.github.donttemptfate.fatesofa.quick.QuickCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor for quick commands: name, command, icon, alias, whether it's a tab; the first nine also get the
 * Quick command 1–9 keys (bound in Controls).
 */
public final class QuickCommandsScreen extends Screen {
	static final int ROW_H = 38, HEADER_H = 34, FOOTER_H = 24;

	private final Screen parent;
	private final List<QuickCommands.Entry> work;
	private final Widgets.Hits hits = new Widgets.Hits();
	private int px, py, pw, ph, scroll, focusRow;
	private String note = "";
	private final java.util.Map<String, net.minecraft.world.item.ItemStack> icons = new java.util.HashMap<>();

	/** Opened from settings or /ofa quick: the list as it is. */
	public QuickCommandsScreen(Screen parent) {
		this(parent, OPEN);
	}

	static final int OPEN = -1000;

	/** @param focusRow the row to start editing; −1 adds a new row first (the "+" tab) */
	public QuickCommandsScreen(Screen parent, int focusRow) {
		super(Component.literal("Quick commands"));
		this.parent = parent;
		var f = QuickCommandsFeature.INSTANCE;
		this.work = new ArrayList<>(f == null ? QuickCommands.defaults() : f.entries());
		this.focusRow = focusRow == OPEN ? -1 : focusRow;
		if (focusRow == -1 && f != null) { // "+": start with a new row
			this.work.add(new QuickCommands.Entry("", "", "paper", true, ""));
			this.focusRow = work.size() - 1;
		}
	}

	int visibleRows() {
		return Math.max(1, (ph - HEADER_H - FOOTER_H - 6) / ROW_H);
	}

	@Override
	protected void init() {
		pw = Math.min(this.width - 16, 400);
		ph = Math.min(this.height - 16, 240);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		if (focusRow >= 0) {
			scroll = Math.max(0, Math.min(focusRow, work.size() - visibleRows()));
			focusRow = -2 - focusRow; // remember to focus its name box once
		}
		scroll = Math.max(0, Math.min(scroll, Math.max(0, work.size() - visibleRows())));
		int x = px + 10, w = pw - 20;
		for (int r = 0; r < visibleRows() && scroll + r < work.size(); r++) {
			int i = scroll + r, y = py + HEADER_H + r * ROW_H;
			QuickCommands.Entry e = work.get(i);
			int cmdX = x + 22 + 92;
			EditBox name = box(x + 22, y, 88, e.name(), "Name", v -> set(i, v, null, null, null));
			box(cmdX, y, x + w - 18 - cmdX, e.command(), "command, without /", v -> set(i, null, v, null, null));
			box(x + 22, y + 17, 60, e.alias(), "alias", v -> set(i, null, null, null, v));
			box(x + 22 + 64, y + 17, 110, e.icon(), "icon (e.g. bone, HYPERION)", v -> set(i, null, null, v, null));
			if (focusRow == -2 - i) {
				setFocused(name);
				focusRow = -1;
			}
		}
	}

	EditBox box(int x, int y, int w, String value, String hint, java.util.function.Consumer<String> onChange) {
		EditBox b = new EditBox(this.font, x, y, w, 14, Component.literal(hint));
		b.setMaxLength(QuickCommands.MAX_COMMAND);
		b.setValue(value);
		b.setHint(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
		b.setResponder(onChange);
		addRenderableWidget(b);
		return b;
	}

	void set(int i, String name, String command, String icon, String alias) {
		if (i >= work.size()) return;
		QuickCommands.Entry e = work.get(i);
		work.set(i, new QuickCommands.Entry(name != null ? name : e.name(), command != null ? command : e.command(),
				icon != null ? icon : e.icon(), e.tab(), alias != null ? alias : e.alias()));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
		super.extractBackground(g, mx, my, delta);
		Ui.frame(g, px, py, pw, ph, 0.95f);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		super.extractRenderState(g, mx, my, delta);
		hits.clear();
		int x = px + 10, w = pw - 20;
		Ui.draw(g, Ui.t("QUICK COMMANDS", Ui.Role.TITLE, Ui.accent()), Ui.Role.TITLE, x, py + 18);
		Ui.draw(g, Ui.t("One click, key or alias = one command · keys: Controls → Quick command 1–9",
				Ui.Role.SMALL, Ui.GRAY), Ui.Role.SMALL, x, py + 28);
		for (int r = 0; r < visibleRows() && scroll + r < work.size(); r++) {
			int i = scroll + r, y = py + HEADER_H + r * ROW_H;
			QuickCommands.Entry e = work.get(i);
			g.fill(x - 2, y - 2, x + w + 2, y + ROW_H - 4, 0x18FFFFFF);
			g.fill(x - 2, y - 2, x, y + ROW_H - 4, Ui.accent() & 0x60FFFFFF);
			g.item(icons.computeIfAbsent(e.icon(), QuickCommandsFeature::iconStack), x + 2, y);
			g.text(font, Integer.toString(i + 1), x + 6, y + 21, Ui.DARK, false);
			// remove
			Rect del = new Rect(x + w - 14, y + 1, 12, 12);
			g.text(font, "✕", del.x() + 2, del.y() + 2, del.contains(mx, my) ? Ui.RED : 0xFF8A5A8A, false);
			hits.add(del, () -> {
				work.remove(i);
				rebuildWidgets();
			}, null);
			// second line: tab toggle, key, move
			int lx = x + 22 + 64 + 116;
			Rect tog = new Rect(lx, y + 19, 20, 10);
			Widgets.toggle(g, tog.x(), tog.y(), e.tab(), tog.contains(mx, my));
			g.text(font, "Tab", lx + 24, y + 20, Ui.GRAY, false);
			hits.add(new Rect(lx, y + 17, 46, 14), () -> work.set(i, new QuickCommands.Entry(e.name(), e.command(), e.icon(), !e.tab(), e.alias())), null);
			String key = i < Keys.quick.length && Keys.quick[i] != null
					? (Keys.quick[i].isUnbound() ? "no key" : "Key " + Keys.quick[i].getTranslatedKeyMessage().getString()) : "";
			g.text(font, key, lx + 52, y + 20, Keys.quick.length > i && Keys.quick[i] != null && !Keys.quick[i].isUnbound() ? Ui.AQUA : Ui.DARK, false);
			Rect up = new Rect(x + w - 28, y + 17, 12, 12), down = new Rect(x + w - 14, y + 17, 12, 12);
			if (i > 0) {
				g.text(font, "▲", up.x() + 2, up.y() + 2, up.contains(mx, my) ? Ui.WHITE : Ui.GRAY, false);
				hits.add(up, () -> swap(i, i - 1), null);
			}
			if (i < work.size() - 1) {
				g.text(font, "▼", down.x() + 2, down.y() + 2, down.contains(mx, my) ? Ui.WHITE : Ui.GRAY, false);
				hits.add(down, () -> swap(i, i + 1), null);
			}
			String problem = QuickCommands.aliasProblem(e.alias(), work, i);
			if (problem != null) g.text(font, "alias: " + problem, x + 22, y + 32, Ui.RED, false);
			else if (QuickCommands.shadowsHypixel(e.alias()))
				g.text(font, "(shadows Hypixel's /" + e.alias() + " while OFA runs)", x + 22, y + 32, Ui.YELLOW, false);
		}
		if (work.size() > visibleRows()) {
			String more = (scroll + 1) + "–" + Math.min(work.size(), scroll + visibleRows()) + " of " + work.size() + " · scroll for more";
			int doneW = Ui.width(Ui.t("Done", Ui.Role.BODY_BOLD, 0), Ui.Role.BODY_BOLD) + 16;
			g.text(font, more, px + pw - 10 - doneW - 8 - font.width(more), py + ph - FOOTER_H + 5, Ui.DARK, false);
		}
		// footer
		int fy = py + ph - FOOTER_H + 2;
		g.fill(px + 8, fy - 4, px + pw - 8, fy - 3, Ui.rule());
		int bx = x;
		for (String label : List.of("+ Add", "Reset to defaults", "Done")) {
			int bw = Ui.width(Ui.t(label, Ui.Role.BODY_BOLD, 0), Ui.Role.BODY_BOLD) + 16;
			if (label.equals("Done")) bx = px + pw - 10 - bw;
			Rect b = new Rect(bx, fy, bw, 14);
			Widgets.button(g, b.x(), b.y(), b.w(), b.h(), label, b.contains(mx, my), label.equals("Done"));
			hits.add(b, switch (label) {
				case "+ Add" -> () -> {
					if (work.size() >= QuickCommands.MAX) {
						note = "That's the most quick commands (" + QuickCommands.MAX + ").";
						return;
					}
					work.add(new QuickCommands.Entry("", "", "paper", true, ""));
					focusRow = work.size() - 1;
					rebuildWidgets();
				};
				case "Reset to defaults" -> () -> {
					work.clear();
					work.addAll(QuickCommands.defaults());
					scroll = 0;
					rebuildWidgets();
				};
				default -> this::onClose;
			}, null);
			bx += bw + 4;
		}
		if (!note.isEmpty()) g.text(font, note, x + 200, fy + 3, Ui.YELLOW, false);
	}

	void swap(int a, int b) {
		var t = work.get(a);
		work.set(a, work.get(b));
		work.set(b, t);
		rebuildWidgets();
	}

	@Override
	public boolean mouseScrolled(double x, double y, double h, double v) {
		int before = scroll;
		scroll = Math.max(0, Math.min(Math.max(0, work.size() - visibleRows()), scroll - (int) Math.signum(v)));
		if (scroll != before) rebuildWidgets();
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

	/** Saves on the way out: empty rows dropped, bad aliases cleared (with a note). */
	@Override
	public void onClose() {
		List<QuickCommands.Entry> out = new ArrayList<>();
		int cleared = 0;
		for (QuickCommands.Entry e : work) {
			if (e.command().isEmpty()) continue;
			if (QuickCommands.aliasProblem(e.alias(), out, -1) != null) {
				e = new QuickCommands.Entry(e.name(), e.command(), e.icon(), e.tab(), "");
				cleared++;
			}
			out.add(e);
		}
		if (QuickCommandsFeature.INSTANCE != null) QuickCommandsFeature.INSTANCE.save(out);
		if (cleared > 0) Game.warn(cleared + " alias(es) weren't usable and were removed.");
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
