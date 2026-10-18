package io.github.donttemptfate.fatesofa.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How much time each feature costs: once a second the features' hook timings are collected and
 * divided by the frames drawn, giving milliseconds per frame. Shown on the Diagnostics page.
 */
public final class Diagnostics {
	private Diagnostics() {}

	/** One feature's cost over the last sample. */
	public record Cost(Feature feature, double msPerFrame, long calls) {
	}

	private static volatile long frames;
	private static long lastSampleAt;
	private static volatile List<Cost> last = List.of();
	private static volatile double fps;
	private static final Map<String, Double> PEAK = new LinkedHashMap<>();

	/** Called once per drawn frame. */
	public static void frame() {
		frames++;
	}

	/** Called every client tick; samples once a second. */
	public static synchronized void tick(long nowMs) {
		if (lastSampleAt == 0) {
			lastSampleAt = nowMs;
			for (Feature f : Features.all()) f.takeTiming();
			frames = 0;
			return;
		}
		long span = nowMs - lastSampleAt;
		if (span < 1000) return;
		long fr = Math.max(1, frames);
		frames = 0;
		lastSampleAt = nowMs;
		fps = fr * 1000.0 / span;
		List<Cost> out = new ArrayList<>();
		for (Feature f : Features.all()) {
			long[] t = f.takeTiming();
			if (t[1] == 0) continue;
			double ms = t[0] / 1e6 / fr;
			out.add(new Cost(f, ms, t[1]));
			PEAK.merge(f.id, ms, Math::max);
		}
		out.sort(Comparator.comparingDouble(c -> -c.msPerFrame()));
		last = List.copyOf(out);
	}

	public static List<Cost> costs() {
		return last;
	}

	public static double fps() {
		return fps;
	}

	public static synchronized double peak(String featureId) {
		return PEAK.getOrDefault(featureId, 0.0);
	}

	public static double totalMs() {
		double t = 0;
		for (Cost c : last) t += c.msPerFrame();
		return t;
	}

	/** For tests. */
	static synchronized void reset() {
		frames = 0;
		lastSampleAt = 0;
		last = List.of();
		PEAK.clear();
	}
}
