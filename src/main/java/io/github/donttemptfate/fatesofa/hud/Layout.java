package io.github.donttemptfate.fatesofa.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The HUD compositor's placement step: turns each element's anchor + offset into a screen rectangle,
 * keeps it on screen and out of the reserved areas (hotbar, chat, scoreboard, boss bar), and slides
 * elements that would overlap an earlier one away from their anchor edge. Elements are placed in
 * priority order, so the more important one keeps its spot.
 */
public final class Layout {
	private Layout() {}

	/** An element to place: id, size in GUI pixels (before scale), position. */
	public record Item(String id, int w, int h, HudPos pos) {
	}

	public static final int MARGIN = 2;
	/** How far an element may be pushed while looking for free space before it gives up and overlaps. */
	static final int MAX_PUSH = 400;

	public static Map<String, Rect> place(List<Item> items, int screenW, int screenH, List<Rect> reserved) {
		Map<String, Rect> out = new LinkedHashMap<>();
		List<Rect> taken = new ArrayList<>(reserved);
		for (Item it : items) {
			int w = Math.max(1, Math.round(it.w() * it.pos().scale()));
			int h = Math.max(1, Math.round(it.h() * it.pos().scale()));
			Anchor a = it.pos().anchor();
			int x = a.baseX(screenW, w) + it.pos().dx();
			int y = a.baseY(screenH, h) + it.pos().dy();
			Rect r = clamp(new Rect(x, y, w, h), screenW, screenH);
			Rect free = findFree(r, a.stackDir(), taken, screenW, screenH);
			out.put(it.id(), free);
			taken.add(free);
		}
		return out;
	}

	static Rect clamp(Rect r, int screenW, int screenH) {
		int x = Math.max(MARGIN, Math.min(r.x(), screenW - r.w() - MARGIN));
		int y = Math.max(MARGIN, Math.min(r.y(), screenH - r.h() - MARGIN));
		if (r.w() + 2 * MARGIN > screenW) x = 0;
		if (r.h() + 2 * MARGIN > screenH) y = 0;
		return new Rect(x, y, r.w(), r.h());
	}

	static Rect findFree(Rect r, int dir, List<Rect> taken, int screenW, int screenH) {
		Rect c = r;
		for (int pushed = 0; pushed <= MAX_PUSH; ) {
			Rect hit = firstOverlap(c, taken);
			if (hit == null) return c;
			int step = dir > 0 ? hit.bottom() + MARGIN - c.y() : hit.y() - MARGIN - c.bottom();
			if (step == 0) step = dir;
			pushed += Math.abs(step);
			c = c.moved(0, step);
			if (c.y() < 0 || c.bottom() > screenH) break; // ran off the screen: try the other way
		}
		c = r;
		for (int pushed = 0; pushed <= MAX_PUSH; ) {
			Rect hit = firstOverlap(c, taken);
			if (hit == null) return c;
			int step = -dir > 0 ? hit.bottom() + MARGIN - c.y() : hit.y() - MARGIN - c.bottom();
			if (step == 0) step = -dir;
			pushed += Math.abs(step);
			c = c.moved(0, step);
			if (c.y() < 0 || c.bottom() > screenH) break;
		}
		return r; // no free space: keep the player's spot
	}

	static Rect firstOverlap(Rect c, List<Rect> taken) {
		for (Rect t : taken) if (c.overlaps(t)) return t;
		return null;
	}
}
