package io.github.donttemptfate.fatesofa.items;

import io.github.donttemptfate.fatesofa.data.Ingredient;
import io.github.donttemptfate.fatesofa.data.Price;
import io.github.donttemptfate.fatesofa.data.PriceService;
import io.github.donttemptfate.fatesofa.data.PriceTables;
import io.github.donttemptfate.fatesofa.data.Recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Values for display: one item stack (base price × count), a list of stacks (chest value, top X), and a
 * recipe's input cost (bucket of ingredients at instant-buy / lowest BIN). Prices are always labelled with
 * their source; nothing here is a buy or flip signal.
 */
public final class Valuer {
	private Valuer() {}

	/** A valued stack (for chest value lists). */
	public record Valued(String key, String name, int count, double each, Price.Kind kind) {
		public double total() {
			return each * count;
		}
	}

	public static Valued value(PriceService prices, String key, String name, int count, long now) {
		if (key == null) return null;
		Price p = prices.value(key, now);
		if (p == null) return null;
		return new Valued(key, name, count, p.value(), p.kind());
	}

	/** Same items added up (two stacks of Enchanted Diamond → one line), biggest totals first, top {@code n}. */
	public static List<Valued> top(List<Valued> items, int n) {
		List<Valued> merged = new ArrayList<>();
		outer:
		for (Valued v : items) {
			if (v == null) continue;
			for (int i = 0; i < merged.size(); i++) {
				Valued m = merged.get(i);
				if (m.key().equals(v.key()) && m.name().equals(v.name())) {
					merged.set(i, new Valued(m.key(), m.name(), m.count() + v.count(), m.each(), m.kind()));
					continue outer;
				}
			}
			merged.add(v);
		}
		merged.sort(Comparator.comparingDouble(Valued::total).reversed());
		return merged.size() > n ? List.copyOf(merged.subList(0, n)) : List.copyOf(merged);
	}

	public static double total(List<Valued> items) {
		double t = 0;
		for (Valued v : items) if (v != null) t += v.total();
		return t;
	}

	/** Cost of a recipe's inputs: what buying them would cost now (bazaar instant buy, else market value). */
	public record Cost(double total, int missing) {
	}

	public static Cost cost(PriceService prices, Recipe r, long now) {
		double total = 0;
		int missing = 0;
		for (Ingredient in : r.inputs()) {
			if (in.coins()) {
				total += in.count();
				continue;
			}
			PriceTables.Bz q = prices.bazaarQuote(in.key(), now);
			double each = q != null && q.instantBuy() > 0 ? q.instantBuy() : Double.NaN;
			if (Double.isNaN(each)) {
				Price p = prices.value(in.key(), now);
				each = p == null ? Double.NaN : p.value();
			}
			if (Double.isNaN(each)) missing++;
			else total += each * in.count();
		}
		return new Cost(total, missing);
	}
}
