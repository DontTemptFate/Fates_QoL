package io.github.donttemptfate.fatesofa.storage;

import io.github.donttemptfate.fatesofa.data.PriceService;
import io.github.donttemptfate.fatesofa.items.Valuer;

import java.util.IdentityHashMap;
import java.util.Map;

/** What remembered pages and stacks are worth at today's prices; worked out again every few seconds. */
public final class PageValues {
	static final long FRESH_MS = 5000;

	private final PriceService prices;
	private final Map<Object, Double> cache = new IdentityHashMap<>();
	private long at;

	public PageValues(PriceService prices) {
		this.prices = prices;
	}

	void expire(long now) {
		if (now - at > FRESH_MS) {
			cache.clear();
			at = now;
		}
	}

	/** A stack's value now; NaN when nothing prices it. */
	public double of(StoredItem it) {
		long now = System.currentTimeMillis();
		expire(now);
		Double known = cache.get(it);
		if (known != null) return known;
		Valuer.Valued v = Valuer.value(prices, it.key(), it.name(), it.count(), now);
		double d = v == null ? Double.NaN : v.total();
		cache.put(it, d);
		return d;
	}

	public double of(StorageCache.Page p) {
		long now = System.currentTimeMillis();
		expire(now);
		Double known = cache.get(p);
		if (known != null) return known;
		double t = 0;
		for (StoredItem it : p.items()) {
			double v = of(it);
			if (!Double.isNaN(v)) t += v;
		}
		cache.put(p, t);
		return t;
	}
}
