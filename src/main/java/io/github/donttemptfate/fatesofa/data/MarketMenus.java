package io.github.donttemptfate.fatesofa.data;

import io.github.donttemptfate.fates.common.text.Fmt;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Readers for Hypixel's auction-house and bazaar menu lore (display only). */
public final class MarketMenus {
	private MarketMenus() {}

	// ---- auctions -----------------------------------------------------------------------------------

	public enum Status { ACTIVE, SOLD, EXPIRED, ENDED, UNKNOWN }

	/** A listing: price (BIN or the current/starting bid), whether it's BIN, and its status (for your own auctions). */
	public record Listing(double price, boolean bin, Status status) {
	}

	static final Pattern BIN = Pattern.compile("^Buy it now: (?<p>[\\d,.]+) coins?$");
	static final Pattern BID = Pattern.compile("^(?:Top bid|Starting bid|Highest bid): (?<p>[\\d,.]+) coins?$");
	static final Pattern STATUS = Pattern.compile("^Status: (?<s>.+?)!?$");

	public static Listing listing(List<String> lore) {
		double price = Double.NaN;
		boolean bin = false;
		Status status = Status.UNKNOWN;
		for (String raw : lore) {
			String l = raw.trim();
			Matcher b = BIN.matcher(l);
			if (b.matches()) {
				price = Fmt.parse(b.group("p"));
				bin = true;
				continue;
			}
			Matcher d = BID.matcher(l);
			if (d.matches() && Double.isNaN(price)) price = Fmt.parse(d.group("p"));
			Matcher s = STATUS.matcher(l);
			if (s.matches()) {
				String st = s.group("s").toLowerCase(Locale.ROOT);
				status = st.startsWith("sold") ? Status.SOLD : st.startsWith("expired") ? Status.EXPIRED : st.startsWith("ended") ? Status.ENDED : Status.ACTIVE;
			}
			if (l.startsWith("Ends in:") && status == Status.UNKNOWN) status = Status.ACTIVE;
		}
		return Double.isNaN(price) ? null : new Listing(price, bin, status);
	}

	/** How a price compares to a reference: +12 means 12% above it. */
	public static long percentVs(double price, double reference) {
		return reference > 0 ? Math.round((price / reference - 1) * 100) : 0;
	}

	// ---- bazaar orders ------------------------------------------------------------------------------

	/** One of your bazaar orders: buy order or sell offer, product name, amount, filled, price each. */
	public record Order(boolean buy, String product, double amount, double filled, double priceEach) {
		public boolean full() {
			return amount > 0 && filled >= amount;
		}
	}

	static final Pattern ORDER_NAME = Pattern.compile("^(?<side>BUY|SELL) (?<product>.+)$");
	static final Pattern AMOUNT = Pattern.compile("^(?:Order|Offer) amount: (?<n>[\\d,.]+)x?$");
	static final Pattern FILLED = Pattern.compile("^Filled: (?<f>[\\d,.]+[kKmM]?)/(?<t>[\\d,.]+[kKmM]?)");
	static final Pattern EACH = Pattern.compile("^Price per unit: (?<p>[\\d,.]+) coins?$");

	public static Order order(String name, List<String> lore) {
		Matcher n = ORDER_NAME.matcher(name.trim());
		if (!n.matches()) return null;
		double amount = Double.NaN, filled = 0, each = Double.NaN;
		for (String raw : lore) {
			String l = raw.trim();
			Matcher a = AMOUNT.matcher(l);
			if (a.matches()) amount = Fmt.parse(a.group("n"));
			Matcher f = FILLED.matcher(l);
			if (f.find()) {
				filled = Fmt.parse(f.group("f"));
				if (Double.isNaN(amount)) amount = Fmt.parse(f.group("t"));
			}
			Matcher e = EACH.matcher(l);
			if (e.matches()) each = Fmt.parse(e.group("p"));
		}
		if (Double.isNaN(each)) return null;
		return new Order(n.group("side").equals("BUY"), n.group("product").trim(), Double.isNaN(amount) ? 0 : amount, filled, each);
	}

	/**
	 * Outbid: a buy order below the best buy order now, or a sell offer above the best sell offer now.
	 * {@code bestBuyOrder} = highest buy order (instant-sell price), {@code bestSellOffer} = lowest sell offer (instant-buy price).
	 */
	public static boolean outbid(Order o, double bestBuyOrder, double bestSellOffer) {
		if (o.full()) return false;
		return o.buy() ? bestBuyOrder > o.priceEach() + 1e-9 : bestSellOffer > 0 && bestSellOffer < o.priceEach() - 1e-9;
	}
}
