package io.github.donttemptfate.fatesofa.planner;

import io.github.donttemptfate.fatesofa.data.Ingredient;
import io.github.donttemptfate.fatesofa.data.ItemRepo;
import io.github.donttemptfate.fatesofa.data.Recipe;
import io.github.donttemptfate.fatesofa.data.RepoItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * "Use what you have": crafting and forge recipes you can make right now from items you hold or have
 * stored, and how the result's value compares with what the inputs would sell for.
 */
public final class Crafts {
	public record Option(String output, String name, Recipe recipe, int times, double outputValue, double inputValue) {
		public double gain() {
			return outputValue - inputValue;
		}
	}

	public static List<Option> fromOwned(ItemRepo repo, Map<String, Double> owned, ToDoubleFunction<String> value, int limit) {
		List<Option> out = new ArrayList<>();
		for (RepoItem it : repo.all()) {
			for (Recipe r : it.recipes()) {
				if (r.type() != Recipe.Type.CRAFTING && r.type() != Recipe.Type.FORGE) continue;
				if (r.inputs().isEmpty()) continue;
				int times = Integer.MAX_VALUE;
				double inputValue = 0;
				boolean ok = true;
				for (Ingredient in : r.inputs()) {
					if (in.coins()) {
						ok = false;
						break;
					}
					double have = owned.getOrDefault(in.key(), 0.0);
					int t = (int) Math.floor(have / in.count());
					times = Math.min(times, t);
					double v = value.applyAsDouble(in.key());
					if (Double.isNaN(v)) ok = false;
					else inputValue += v * in.count();
				}
				if (!ok || times <= 0 || times == Integer.MAX_VALUE) continue;
				double outV = value.applyAsDouble(r.output());
				if (Double.isNaN(outV)) continue;
				outV *= Math.max(1, r.outputCount());
				out.add(new Option(r.output(), it.plainName(), r, Math.min(times, 9999), outV, inputValue));
			}
		}
		out.sort(Comparator.comparingDouble(Option::gain).reversed());
		return out.size() > limit ? List.copyOf(out.subList(0, limit)) : out;
	}
}
