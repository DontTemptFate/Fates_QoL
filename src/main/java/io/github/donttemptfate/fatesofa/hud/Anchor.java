package io.github.donttemptfate.fatesofa.hud;

/** Which point of the screen a HUD element is pinned to. Offsets are measured from it. */
public enum Anchor {
	TOP_LEFT(0, 0), TOP(1, 0), TOP_RIGHT(2, 0),
	LEFT(0, 1), CENTER(1, 1), RIGHT(2, 1),
	BOTTOM_LEFT(0, 2), BOTTOM(1, 2), BOTTOM_RIGHT(2, 2);

	final int hx; // 0 left, 1 middle, 2 right
	final int vy; // 0 top, 1 middle, 2 bottom

	Anchor(int hx, int vy) {
		this.hx = hx;
		this.vy = vy;
	}

	/** The anchor nearest to a point, for dropping an element in the HUD editor. */
	public static Anchor nearest(double x, double y, int screenW, int screenH) {
		int hx = x < screenW / 3.0 ? 0 : x > screenW * 2 / 3.0 ? 2 : 1;
		int vy = y < screenH / 3.0 ? 0 : y > screenH * 2 / 3.0 ? 2 : 1;
		for (Anchor a : values()) if (a.hx == hx && a.vy == vy) return a;
		return TOP_LEFT;
	}

	/** Screen x of this anchor's reference point for an element {@code w} wide. */
	int baseX(int screenW, int w) {
		return switch (hx) {
			case 0 -> 0;
			case 1 -> (screenW - w) / 2;
			default -> screenW - w;
		};
	}

	int baseY(int screenH, int h) {
		return switch (vy) {
			case 0 -> 0;
			case 1 -> (screenH - h) / 2;
			default -> screenH - h;
		};
	}

	/** +1 when elements stacked at this anchor move down to avoid each other, -1 up. */
	int stackDir() {
		return vy == 2 ? -1 : 1;
	}
}
