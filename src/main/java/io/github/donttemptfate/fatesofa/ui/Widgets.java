package io.github.donttemptfate.fatesofa.ui;

import io.github.donttemptfate.fatescommon.ui.Ui;
import io.github.donttemptfate.fatesofa.hud.Rect;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Immediate-mode controls for OFA's own screens, drawn in the current theme: each frame the screen
 * draws its controls and records where they are; clicks are matched against that list.
 */
final class Widgets {
	private Widgets() {}

	record Hit(Rect rect, Runnable left, Runnable right, Slider slider) {
	}

	/** A slider being dragged: value from 0..1 along its track. */
	interface Slider {
		void set(double fraction);
	}

	static final class Hits {
		final List<Hit> list = new ArrayList<>();
		/** Named spots (for the in-game test to click). */
		final java.util.Map<String, Rect> named = new java.util.HashMap<>();

		void clear() {
			list.clear();
			named.clear();
		}

		void add(Rect r, Runnable left, Runnable right) {
			list.add(new Hit(r, left, right, null));
		}

		void add(Rect r, Runnable left, Runnable right, String name) {
			list.add(new Hit(r, left, right, null));
			if (name != null) named.put(name, r);
		}

		void slider(Rect r, Slider s) {
			list.add(new Hit(r, null, null, s));
		}

		Hit at(double x, double y) {
			for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).rect().contains(x, y)) return list.get(i);
			return null;
		}
	}

	/** On/off switch, 20×10. */
	static void toggle(GuiGraphicsExtractor g, int x, int y, boolean on, boolean hover) {
		int track = on ? (Ui.accent() & 0x00FFFFFF) | 0xC0000000 : 0xFF2A2A2A;
		g.fill(x, y, x + 20, y + 10, track);
		Ui.ring(g, x, y, 20, 10, 1, hover ? Ui.WHITE : on ? Ui.accent() : 0xFF555555);
		int kx = on ? x + 11 : x + 1;
		g.fill(kx, y + 1, kx + 8, y + 9, on ? Ui.WHITE : 0xFFAAAAAA);
	}

	/** A flat button in the theme. */
	static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean hover, boolean active) {
		g.fill(x, y, x + w, y + h, active ? (Ui.accent() & 0x00FFFFFF) | 0x50000000 : hover ? 0x40FFFFFF : 0x25FFFFFF);
		Ui.ring(g, x, y, w, h, 1, hover || active ? Ui.accent() : Ui.rule());
		var t = Ui.t(label, Ui.Role.BODY_BOLD, active ? Ui.WHITE : 0xFFDDDDDD);
		int tw = Ui.width(t, Ui.Role.BODY_BOLD);
		Ui.draw(g, t, Ui.Role.BODY_BOLD, x + (w - tw) / 2, y + (h + Ui.Role.BODY_BOLD.cap()) / 2);
	}

	/** A small tick box, 9×9. */
	static void checkbox(GuiGraphicsExtractor g, int x, int y, boolean on, boolean hover) {
		g.fill(x, y, x + 9, y + 9, 0xFF1E1E1E);
		Ui.ring(g, x, y, 9, 9, 1, hover ? Ui.WHITE : on ? Ui.accent() : 0xFF555555);
		if (on) g.fill(x + 2, y + 2, x + 7, y + 7, Ui.accent());
	}

	/** A rounded-look chip with a label (for island lists and filters). */
	static void chip(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int x, int y, int w, String label, boolean on, boolean hover) {
		g.fill(x, y, x + w, y + 11, on ? (Ui.accent() & 0x00FFFFFF) | 0x60000000 : hover ? 0x30FFFFFF : 0x18FFFFFF);
		Ui.ring(g, x, y, w, 11, 1, on ? Ui.accent() : hover ? 0xFFAAAAAA : Ui.rule());
		g.text(font, label, x + (w - font.width(label)) / 2, y + 2, on ? Ui.WHITE : 0xFFBBBBBB, true);
	}

	/** Slider track with a fill and a knob; value 0..1. */
	static void slider(GuiGraphicsExtractor g, int x, int y, int w, double fraction, boolean hover) {
		fraction = Math.max(0, Math.min(1, fraction));
		g.fill(x, y + 3, x + w, y + 7, Ui.track());
		Ui.ring(g, x, y + 3, w, 4, 1, Ui.rule());
		int fx = x + (int) Math.round((w - 4) * fraction);
		g.fill(x + 1, y + 4, fx + 2, y + 6, Ui.accent());
		g.fill(fx, y, fx + 4, y + 10, hover ? Ui.WHITE : 0xFFDDDDDD);
	}
}
