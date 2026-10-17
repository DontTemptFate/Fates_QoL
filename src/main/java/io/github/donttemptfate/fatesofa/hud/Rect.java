package io.github.donttemptfate.fatesofa.hud;

/** A rectangle in GUI pixels. */
public record Rect(int x, int y, int w, int h) {
	public int right() {
		return x + w;
	}

	public int bottom() {
		return y + h;
	}

	public boolean overlaps(Rect o) {
		return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
	}

	public boolean contains(double px, double py) {
		return px >= x && px < right() && py >= y && py < bottom();
	}

	public Rect moved(int ddx, int ddy) {
		return new Rect(x + ddx, y + ddy, w, h);
	}
}
