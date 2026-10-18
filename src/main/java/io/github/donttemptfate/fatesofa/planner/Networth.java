package io.github.donttemptfate.fatesofa.planner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Networth from what OFA has seen (inventory, armour, storage pages, accessories, purse), by category, with
 * the most valuable items, plus a small daily history so the player sees the change. Display only.
 */
public final class Networth {
	/** One valued item. */
	public record Entry(String category, String key, String name, int count, double value) {
	}

	/** Totals by category, biggest first, and the top items. */
	public record Summary(double total, Map<String, Double> byCategory, List<Entry> top) {
	}

	public static Summary summarise(List<Entry> entries, int topN) {
		Map<String, Double> cat = new LinkedHashMap<>();
		double total = 0;
		for (Entry e : entries) {
			if (Double.isNaN(e.value()) || e.value() <= 0) continue;
			cat.merge(e.category(), e.value(), Double::sum);
			total += e.value();
		}
		List<Map.Entry<String, Double>> sorted = new ArrayList<>(cat.entrySet());
		sorted.sort(Map.Entry.<String, Double>comparingByValue().reversed());
		Map<String, Double> byCat = new LinkedHashMap<>();
		for (var e : sorted) byCat.put(e.getKey(), e.getValue());
		List<Entry> items = new ArrayList<>();
		for (Entry e : entries) if (!Double.isNaN(e.value()) && e.value() > 0 && !e.category().equals("Purse")) items.add(e);
		items.sort(Comparator.comparingDouble(Entry::value).reversed());
		return new Summary(total, byCat, List.copyOf(items.subList(0, Math.min(topN, items.size()))));
	}

	/** Daily totals (date → coins), newest kept; one value per day (the latest that day). */
	public static final class History {
		private final Path file;
		private final TreeMap<LocalDate, Double> days = new TreeMap<>();
		public static final int KEEP_DAYS = 90;

		public History(Path file) {
			this.file = file;
			load();
		}

		void load() {
			days.clear();
			try {
				if (file == null || !Files.isRegularFile(file)) return;
				JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
				for (var e : o.entrySet()) days.put(LocalDate.parse(e.getKey()), e.getValue().getAsDouble());
			} catch (IOException | RuntimeException e) {
				days.clear();
			}
		}

		/** Records today's total and returns the latest earlier day's total (or NaN). */
		public synchronized double record(LocalDate today, double total) {
			Map.Entry<LocalDate, Double> before = days.lowerEntry(today);
			days.put(today, total);
			while (days.size() > KEEP_DAYS) days.pollFirstEntry();
			save();
			return before == null ? Double.NaN : before.getValue();
		}

		public synchronized LocalDate previousDay(LocalDate today) {
			return days.lowerKey(today);
		}

		public synchronized Map<LocalDate, Double> all() {
			return new TreeMap<>(days);
		}

		void save() {
			if (file == null) return;
			JsonObject o = new JsonObject();
			for (var e : days.entrySet()) o.addProperty(e.getKey().toString(), e.getValue());
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, o.toString(), StandardCharsets.UTF_8);
			} catch (IOException ignored) {
				// history is a nicety: never fail the command over it
			}
		}
	}
}
