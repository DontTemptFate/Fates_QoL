package io.github.donttemptfate.fatesofa.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every finished dungeon (or Kuudra) run: floor, time, score, deaths, secrets, profit. Personal bests per floor. */
public final class RunHistory {
	public record Run(String floor, long timeMs, int score, String grade, int deaths, int secrets, double profit, long atMs) {
	}

	/** Per floor: runs, fastest time, best score, average time. */
	public record Stats(String floor, int runs, long fastestMs, int bestScore, long averageMs) {
	}

	public static final int KEEP = 1000;
	private final Path file;
	private final List<Run> runs = new ArrayList<>();

	public RunHistory(Path file) {
		this.file = file;
		load();
	}

	public synchronized void add(Run r) {
		runs.add(r);
		while (runs.size() > KEEP) runs.remove(0);
		save();
	}

	public synchronized List<Run> all() {
		return new ArrayList<>(runs);
	}

	/** Fastest earlier run on this floor (or null). */
	public synchronized Run best(String floor) {
		Run best = null;
		for (Run r : runs) if (r.floor().equals(floor) && r.timeMs() > 0 && (best == null || r.timeMs() < best.timeMs())) best = r;
		return best;
	}

	public synchronized List<Stats> stats() {
		Map<String, List<Run>> by = new LinkedHashMap<>();
		for (Run r : runs) by.computeIfAbsent(r.floor(), k -> new ArrayList<>()).add(r);
		List<Stats> out = new ArrayList<>();
		for (var e : by.entrySet()) {
			long fastest = Long.MAX_VALUE, total = 0;
			int best = 0, timed = 0;
			for (Run r : e.getValue()) {
				if (r.timeMs() > 0) {
					fastest = Math.min(fastest, r.timeMs());
					total += r.timeMs();
					timed++;
				}
				best = Math.max(best, r.score());
			}
			out.add(new Stats(e.getKey(), e.getValue().size(), fastest == Long.MAX_VALUE ? -1 : fastest, best, timed == 0 ? -1 : total / timed));
		}
		return out;
	}

	void load() {
		runs.clear();
		try {
			if (file == null || !Files.isRegularFile(file)) return;
			JsonArray a = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonArray();
			for (JsonElement e : a) {
				JsonObject o = e.getAsJsonObject();
				runs.add(new Run(o.get("f").getAsString(), o.get("t").getAsLong(), o.get("s").getAsInt(), o.has("g") ? o.get("g").getAsString() : "",
						o.has("d") ? o.get("d").getAsInt() : 0, o.has("x") ? o.get("x").getAsInt() : -1, o.has("p") ? o.get("p").getAsDouble() : Double.NaN,
						o.get("at").getAsLong()));
			}
		} catch (IOException | RuntimeException e) {
			runs.clear();
		}
	}

	void save() {
		if (file == null) return;
		JsonArray a = new JsonArray();
		for (Run r : runs) {
			JsonObject o = new JsonObject();
			o.addProperty("f", r.floor());
			o.addProperty("t", r.timeMs());
			o.addProperty("s", r.score());
			o.addProperty("g", r.grade());
			o.addProperty("d", r.deaths());
			o.addProperty("x", r.secrets());
			if (!Double.isNaN(r.profit())) o.addProperty("p", r.profit());
			o.addProperty("at", r.atMs());
			a.add(o);
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, a.toString(), StandardCharsets.UTF_8);
		} catch (IOException ignored) {
			// history is a nicety
		}
	}
}
