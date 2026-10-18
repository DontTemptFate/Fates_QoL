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

	public Highlight(Feature f, String prefix, int colorDefault) {
		this.f = f;
		color = f.setting(Setting.color(prefix + ".color", "Colour", colorDefault).rainbow().group(GROUP));
		style = f.setting(Setting.choice(prefix + ".style", "Style", "outline", STYLES).group(GROUP));
		width = f.setting(Setting.integer(prefix + ".width", "Line width", 2, 1, 6).group(GROUP).advanced());
		throughWalls = f.setting(Setting.bool(prefix + ".throughWalls", "Visible through walls", true).group(GROUP));
		maxDistance = f.setting(Setting.integer(prefix + ".maxDistance", "Max distance (blocks)", 64, 8, 256).group(GROUP).advanced());
		label = f.setting(Setting.bool(prefix + ".label", "Show a label", true).group(GROUP));
		labelScale = f.setting(Setting.decimal(prefix + ".labelScale", "Label size", 1.0, 0.5, 3.0).group(GROUP).advanced());
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
