package io.github.donttemptfate.fatesofa;

import io.github.donttemptfate.fatesofa.data.MarketMenus;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Batch 8c: auction and bazaar menu readers. */
public class MarketsTest {
	@Test
	public void auctionListings() {
		var l = MarketMenus.listing(List.of("Hyperion", "-----------------", "Seller: [MVP+] Someone", "Buy it now: 1,100,000,000 coins", "", "Ends in: 2h 3m"));
		assertTrue(l.bin());
		assertEquals(1.1e9, l.price(), 1);
		assertEquals(MarketMenus.Status.ACTIVE, l.status());
		var mine = MarketMenus.listing(List.of("Bids: 3 bids", "Top bid: 5,000,000 coins", "Status: Sold!"));
		assertEquals(MarketMenus.Status.SOLD, mine.status());
		assertEquals(5e6, mine.price(), 1);
		assertNull(MarketMenus.listing(List.of("Just an item")));
		assertEquals(10, MarketMenus.percentVs(1.1e9, 1e9));
		assertEquals(-50, MarketMenus.percentVs(50, 100));
	}

	@Test
	public void bazaarOrders() {
		var o = MarketMenus.order("BUY Enchanted Diamond", List.of("Worth 12M coins", "", "Order amount: 1,280x", "Filled: 640/1,280 50%", "", "Price per unit: 172.3 coins"));
		assertTrue(o.buy());
		assertEquals("Enchanted Diamond", o.product());
		assertEquals(1280, o.amount(), 1e-9);
		assertEquals(640, o.filled(), 1e-9);
		assertEquals(172.3, o.priceEach(), 1e-9);
		assertTrue("someone pays more per unit now", MarketMenus.outbid(o, 175, 180));
		assertFalse(MarketMenus.outbid(o, 172.3, 180));
		var s = MarketMenus.order("SELL Enchanted Diamond", List.of("Offer amount: 64x", "Price per unit: 190 coins"));
		assertFalse(s.buy());
		assertTrue("someone sells for less now", MarketMenus.outbid(s, 170, 185));
		assertNull(MarketMenus.order("Enchanted Diamond", List.of("Price per unit: 1 coins")));
	}
}
