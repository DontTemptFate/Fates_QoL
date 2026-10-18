package io.github.donttemptfate.fatesofa.data;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Item values without any login: bazaar straight from Hypixel's keyless API, NPC sell prices from
 * Hypixel's item list, auction-house lowest BIN / median from the fatesflipper.com public price file.
 * Refreshed lazily (only while something asks), each source on its own schedule, off the game thread.
 */
public final class PriceService {
	public enum BazaarMode { INSTANT_SELL, SELL_OFFER }

	public enum AhMode { PLAIN, LOWEST_BIN, MEDIAN }

	static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
	static final String ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items";
	public static final String AH_URL = "https://fatesflipper.com/public/prices.json";

	static final long BAZAAR_EVERY = 60_000;
	static final long NPC_EVERY = 6 * 3_600_000L;
	static final long AH_EVERY = 5 * 60_000;
	static final long FAIL_BACKOFF = 5 * 60_000;

	private final Executor io;
	private final Consumer<String> log;

	private volatile Map<String, PriceTables.Bz> bazaar = Map.of();
	private volatile Map<String, Double> npc = Map.of();
	private volatile Map<String, PriceTables.Ah> ah = Map.of();
	private volatile long bazaarAt, npcAt, ahAt;
	private volatile long bazaarTry, npcTry, ahTry;
	private volatile long ahBackoffUntil;
	private final AtomicBoolean bzBusy = new AtomicBoolean(), npcBusy = new AtomicBoolean(), ahBusy = new AtomicBoolean();

	public volatile boolean enabled = true;
	public volatile boolean ahEnabled = true;
	public volatile BazaarMode bazaarMode = BazaarMode.INSTANT_SELL;
	public volatile AhMode ahMode = AhMode.PLAIN;
	private volatile String ahEtag;

	public PriceService(Executor io, Consumer<String> log) {
		this.io = io;
		this.log = log;
	}

	/** Value of one item (by NEU key), or null when no source knows it. Never blocks. */
	public Price value(String key, long now) {
		if (key == null || !enabled) return null;
		refresh(now);
		Map<String, PriceTables.Bz> bz = bazaar;
		String bzId = PriceTables.bazaarId(key, bz);
		if (bzId != null) {
			PriceTables.Bz q = bz.get(bzId);
			double v = bazaarMode == BazaarMode.SELL_OFFER ? q.instantBuy() : q.instantSell();
			if (v > 0 && !Double.isNaN(v)) {
				return new Price(v, bazaarMode == BazaarMode.SELL_OFFER ? Price.Kind.BAZAAR_SELL_OFFER : Price.Kind.BAZAAR_INSTANT_SELL, bazaarAt);
			}
		}
		PriceTables.Ah a = ah.get(key);
		Price market = null;
		if (a != null) {
			market = ahPrice(a);
		}
		Double n = npc.get(key);
		Price npcP = n != null && n > 0 ? new Price(n, Price.Kind.NPC, npcAt) : null;
		if (market == null) return npcP;
		if (npcP == null) return market;
		return npcP.value() > market.value() ? npcP : market;
	}

	/** Bazaar quote by NEU key (for craft costs, which buy instead of sell). */
	public PriceTables.Bz bazaarQuote(String key, long now) {
		if (!enabled) return null;
		refresh(now);
		String id = PriceTables.bazaarId(key, bazaar);
		return id == null ? null : bazaar.get(id);
	}

	public Double npcPrice(String key) {
		return npc.get(key);
	}

	public PriceTables.Ah ahQuote(String key) {
		return ah.get(key);
	}

	public long bazaarAt() {
		return bazaarAt;
	}

	public long ahAt() {
		return ahAt;
	}

	public int bazaarCount() {
		return bazaar.size();
	}

	public int ahCount() {
		return ah.size();
	}

	/** An auction entry as a price in the chosen mode (plain copy, lowest BIN or median); null without a usable number. */
	Price ahPrice(PriceTables.Ah a) {
		if (a == null) return null;
		return switch (ahMode) {
			case MEDIAN -> a.median() > 0 ? new Price(a.median(), Price.Kind.AH_MEDIAN, ahAt) : a.lbin() > 0 ? new Price(a.lbin(), Price.Kind.AH_LOWEST_BIN, ahAt) : null;
			case LOWEST_BIN -> a.lbin() > 0 ? new Price(a.lbin(), Price.Kind.AH_LOWEST_BIN, ahAt) : null;
			case PLAIN -> a.p25() > 0 ? new Price(a.p25(), Price.Kind.AH_PLAIN, ahAt) : a.lbin() > 0 ? new Price(a.lbin(), Price.Kind.AH_LOWEST_BIN, ahAt) : null;
		};
	}

	/** A pet's auction value at its level (level-priced data when the file has it, else the all-levels price). */
	public Price petValue(String key, int level, long now) {
		if (key == null || !enabled) return null;
		refresh(now);
		PriceTables.Ah a = ah.get(key);
		if (a == null) return null;
		PriceTables.Ah at = a.atLevel(level);
		Price p = ahPrice(at != null ? at : a);
		return p != null ? p : ahPrice(a);
	}

	/** Starts any refresh that is due. Cheap to call every frame. */
	public void refresh(long now) {
		if (!enabled) return;
		if (now - bazaarAt > BAZAAR_EVERY && now - bazaarTry > 15_000 && bzBusy.compareAndSet(false, true)) {
			bazaarTry = now;
			io.execute(() -> {
				try {
					Http.Response r = Http.get(BAZAAR_URL, 32L * 1024 * 1024, null);
					if (r.ok()) {
						bazaar = PriceTables.bazaar(r.text());
						bazaarAt = System.currentTimeMillis();
					} else log.accept("bazaar prices: HTTP " + r.status());
				} catch (Exception e) {
					log.accept("bazaar prices: " + e);
				} finally {
					bzBusy.set(false);
				}
			});
		}
		if (now - npcAt > NPC_EVERY && now - npcTry > FAIL_BACKOFF && npcBusy.compareAndSet(false, true)) {
			npcTry = now;
			io.execute(() -> {
				try {
					Http.Response r = Http.get(ITEMS_URL, 64L * 1024 * 1024, null);
					if (r.ok()) {
						npc = PriceTables.npc(r.text());
						npcAt = System.currentTimeMillis();
					} else log.accept("NPC prices: HTTP " + r.status());
				} catch (Exception e) {
					log.accept("NPC prices: " + e);
				} finally {
					npcBusy.set(false);
				}
			});
		}
		if (ahEnabled && now - ahAt > AH_EVERY && now - ahTry > 60_000 && now > ahBackoffUntil && ahBusy.compareAndSet(false, true)) {
			ahTry = now;
			io.execute(() -> {
				try {
					// "anything new?": the server answers 304 with no body when our copy is current
					Http.Response r = Http.get(AH_URL, 32L * 1024 * 1024, ah.isEmpty() ? null : ahEtag);
					if (r.ok()) {
						ah = PriceTables.ah(r.text());
						ahEtag = r.etag();
						ahAt = System.currentTimeMillis();
					} else if (r.status() == 304) {
						ahAt = System.currentTimeMillis();
					} else {
						// 404 = not published: check again in 30 minutes; 429 / 503 = wait as told, keep the last copy
						long wait = r.status() == 404 ? 30 * 60_000L
								: r.status() == 429 || r.status() == 503 ? Math.max(60, r.retryAfterS()) * 1000 : FAIL_BACKOFF;
						ahBackoffUntil = System.currentTimeMillis() + wait;
					}
				} catch (Exception e) {
					ahBackoffUntil = System.currentTimeMillis() + FAIL_BACKOFF;
				} finally {
					ahBusy.set(false);
				}
			});
		}
	}

	/** For tests and offline use: sets the tables directly. */
	public void load(Map<String, PriceTables.Bz> bz, Map<String, Double> npcPrices, Map<String, PriceTables.Ah> ahPrices, long now) {
		if (bz != null) {
			bazaar = bz;
			bazaarAt = now;
		}
		if (npcPrices != null) {
			npc = npcPrices;
			npcAt = now;
		}
		if (ahPrices != null) {
			ah = ahPrices;
			ahAt = now;
		}
	}
}
