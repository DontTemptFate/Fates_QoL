package io.github.donttemptfate.fatesofa.core;

import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.config.Values;

import java.util.List;

/** The "Highlight" option bundle for things drawn in the world: colour, style, width, range, label. */
public final class Highlight {
	public static final String GROUP = "Highlight";
	public static final List<String> STYLES = List.of("outline", "filled", "both");

	private final Feature f;
	public final Setting<Integer> color;
	public final Setting<String> style;
	public final Setting<Integer> width;
	public final Setting<Boolean> throughWalls;
	public final Setting<Integer> maxDistance;
	public final Setting<Boolean> label;
	public final Setting<Double> labelScale;
	public final Setting<Integer> fillOpacity;

	public Highlight(Feature f, String prefix, int colorDefault) {
		this.f = f;
		color = f.setting(Setting.color(prefix + ".color", "Colour", colorDefault).rainbow().group(GROUP));
		style = f.setting(Setting.choice(prefix + ".style", "Style", "outline", STYLES).group(GROUP));
		width = f.setting(Setting.integer(prefix + ".width", "Line width", 2, 1, 6).group(GROUP).advanced());
		throughWalls = f.setting(Setting.bool(prefix + ".throughWalls", "Visible through walls", true).group(GROUP));
		maxDistance = f.setting(Setting.integer(prefix + ".maxDistance", "Max distance (blocks)", 64, 8, 256).group(GROUP).advanced());
		label = f.setting(Setting.bool(prefix + ".label", "Show a label", true).group(GROUP));
		labelScale = f.setting(Setting.decimal(prefix + ".labelScale", "Label size", 1.0, 0.5, 3.0).group(GROUP).advanced());
		fillOpacity = f.setting(Setting.integer(prefix + ".fillOpacity", "Fill opacity %", 25, 5, 100).group(GROUP).advanced());
	}

	public float width() {
		return f.store() == null ? width.def : f.get(width);
	}

	public boolean throughWalls() {
		return f.store() == null || f.on(throughWalls);
	}

	public boolean label() {
		return f.store() == null || f.on(label);
	}

	public float labelScale() {
		return f.store() == null ? 1f : f.get(labelScale).floatValue();
	}

	/** The fill colour for {@code argb}: same colour at the fill opacity. */
	public int fill(int argb) {
		int pct = f.store() == null ? fillOpacity.def : f.get(fillOpacity);
		return (argb & 0x00FFFFFF) | (Math.round(pct * 2.55f) << 24);
	}

	public int color(long nowMs) {
		if (f.store() == null) return color.def;
		return Values.color(f.get(color), f.on(color.rainbow), nowMs);
	}

	public boolean outline() {
		String s = f.store() == null ? style.def : f.get(style);
		return !s.equals("filled");
	}

	public boolean filled() {
		String s = f.store() == null ? style.def : f.get(style);
		return !s.equals("outline");
	}

	public boolean inRange(double distance) {
		return distance <= (f.store() == null ? maxDistance.def : f.get(maxDistance));
	}
}
