package io.github.donttemptfate.fatesofa.items;

import com.google.gson.JsonObject;
import io.github.donttemptfate.fatesofa.data.Price;
import io.github.donttemptfate.fatesofa.data.PriceService;
import io.github.donttemptfate.fatesofa.data.RepoManager;

import java.util.Set;

/**
 * Item values with upgrades for the whole mod (tooltips, chest value, storage, protection, networth):
 * {@link ItemValue} with today's prices and the NEU reference data. Which upgrades count and at what share
 * comes from the "Price lines in tooltips" settings.
 */
public final class Valuation {
	private final PriceService prices;
	private final RepoManager repo;
	private volatile ItemValue.Refs refs = ItemValue.Refs.EMPTY;
	private volatile PetLevels pets;
	private volatile String refsFor = "";
	public volatile Set<String> counted = ItemValue.CATEGORIES.keySet();
	public volatile double share = 1.0;

	public Valuation(PriceService prices, RepoManager repo) {
		this.prices = prices;
		this.repo = repo;
	}

	/** Reloads the reference data when the item data changed. */
	void refs() {
		String commit = repo == null || repo.items() == null ? null : repo.items().commit();
		String key = commit == null ? "none" : commit;
		if (key.equals(refsFor)) return;
		JsonObject ess = repo == null ? null : repo.constant("essencecosts");
		JsonObject stones = repo == null ? null : repo.constant("reforgestones");
		JsonObject pt = repo == null ? null : repo.constant("pets");
		refs = new ItemValue.Refs(ItemValue.Refs.essence(ess), ItemValue.Refs.stones(stones));
		pets = pt == null ? null : new PetLevels(pt);
		if (ess != null || commit == null) refsFor = key; // try again later if the data wasn't there yet
	}

	/** One item (by its data and key) with its upgrades; null when nothing prices it. */
	public ItemValue.Breakdown of(JsonObject data, String key, int count, long now) {
		if (key == null) return null;
		refs();
		double base = Double.NaN;
		JsonObject pet = data == null ? null : ItemValue.petInfo(data);
		if (pet != null && pets != null) {
			double exp = pet.has("exp") ? pet.get("exp").getAsDouble() : 0;
			int level = pets.level(ItemValue.str(pet, "type"), ItemValue.str(pet, "tier"), exp).level();
			Price p = prices.petValue(key, level, now);
			if (p != null) base = p.value();
		}
		if (Double.isNaN(base)) {
			Price p = prices.value(key, now);
			if (p != null) base = p.value();
		}
		ItemValue.Breakdown b = ItemValue.of(data, key, base, count, k -> {
			Price p = prices.value(k, now);
			return p == null ? Double.NaN : p.value();
		}, refs, counted, share);
		return Double.isNaN(b.base()) && b.parts().isEmpty() ? null : b;
	}

	/** Total value (base × count + upgrades), or NaN. */
	public double total(JsonObject data, String key, int count, long now) {
		ItemValue.Breakdown b = of(data, key, count, now);
		return b == null ? Double.NaN : b.total();
	}
}
