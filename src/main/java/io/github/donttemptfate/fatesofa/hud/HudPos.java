package io.github.donttemptfate.fatesofa.hud;

import com.google.gson.JsonObject;

/**
 * Where one HUD element sits: an anchor plus an offset in GUI pixels, and a scale. Stored this way
 * (not as absolute pixels) so a window resize or GUI-scale change never loses a position.
 */
public record HudPos(Anchor anchor, int dx, int dy, float scale) {
	public HudPos {
		if (anchor == null) anchor = Anchor.TOP_LEFT;
		if (!(scale >= 0.5f && scale <= 3f)) scale = 1f;
	}

	public static HudPos at(Anchor a, int dx, int dy) {
		return new HudPos(a, dx, dy, 1f);
	}

	public HudPos withScale(float s) {
		return new HudPos(anchor, dx, dy, s);
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("a", anchor.name());
		o.addProperty("x", dx);
		o.addProperty("y", dy);
		o.addProperty("s", scale);
		return o;
	}

	public static HudPos fromJson(JsonObject o, HudPos def) {
		try {
			return new HudPos(Anchor.valueOf(o.get("a").getAsString()), o.get("x").getAsInt(), o.get("y").getAsInt(),
					o.has("s") ? o.get("s").getAsFloat() : 1f);
		} catch (RuntimeException e) {
			return def;
		}
	}

	/**
	 * The offset that keeps an element at the same screen spot ({@code x}, {@code y} = its top-left
	 * corner) when it is re-pinned to {@code a}.
	 */
	public static HudPos fromScreen(Anchor a, int x, int y, int w, int h, int screenW, int screenH, float scale) {
		return new HudPos(a, x - a.baseX(screenW, w), y - a.baseY(screenH, h), scale);
	}
}
