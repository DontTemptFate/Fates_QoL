package io.github.donttemptfate.fatesofa;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.donttemptfate.fatesofa.items.ItemValue;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Batch 6: item value with upgrades. */
public class ValuationTest {
	static final Map<String, Double> PRICE = Map.ofEntries(
			Map.entry("ULTIMATE_WISE;5", 2_000_000.0), Map.entry("SHARPNESS;6", 1_500_000.0), Map.entry("ESSENCE_WITHER", 2_000.0),
			Map.entry("FIRST_MASTER_STAR", 10_000_000.0), Map.entry("SECOND_MASTER_STAR", 20_000_000.0), Map.entry("RECOMBOBULATOR_3000", 6_000_000.0),
			Map.entry("HOT_POTATO_BOOK", 80_000.0), Map.entry("FUMING_POTATO_BOOK", 1_000_000.0), Map.entry("PERFECT_SAPPHIRE_GEM", 15_000_000.0),
			Map.entry("FLAWLESS_JASPER_GEM", 3_000_000.0), Map.entry("WITHER_BLOOD", 4_000_000.0), Map.entry("IMPLOSION_SCROLL", 300_000_000.0),
			Map.entry("THE_ART_OF_WAR", 9_000_000.0), Map.entry("DYE_PURE_BLACK", 40_000_000.0), Map.entry("MINOS_RELIC", 30_000_000.0),
			Map.entry("PET_SKIN_ENDER_DRAGON_BABY", 50_000_000.0));
	static final ItemValue.Prices P = k -> PRICE.getOrDefault(k, Double.NaN);

	static ItemValue.Refs refs() {
		JsonObject ess = JsonParser.parseString("{\"HYPERION\":{\"type\":\"Wither\",\"1\":150,\"2\":300,\"3\":500,\"4\":900,\"5\":1500,\"items\":{}}}").getAsJsonObject();
		JsonObject stones = JsonParser.parseString("{\"WITHER_BLOOD\":{\"reforgeName\":\"Withered\"},\"DRAGON_CLAW\":{\"reforgeName\":\"Fabled\"}}").getAsJsonObject();
		return new ItemValue.Refs(ItemValue.Refs.essence(stones.size() > 0 ? ess : null), ItemValue.Refs.stones(stones));
	}

	@Test
	public void hyperionBreakdown() {
		JsonObject d = JsonParser.parseString("""
				{"id":"HYPERION","enchantments":{"ultimate_wise":5,"sharpness":6},"upgrade_level":7,"rarity_upgrades":1,"hot_potato_count":12,
				 "gems":{"SAPPHIRE_0":"PERFECT","COMBAT_0":{"quality":"FLAWLESS","uuid":"x"},"COMBAT_0_gem":"JASPER","unlocked_slots":["COMBAT_0"]},
				 "modifier":"withered","ability_scroll":["IMPLOSION_SCROLL"],"art_of_war_count":1,"dye_item":"DYE_PURE_BLACK","uuid":"abc","timestamp":1}
				""").getAsJsonObject();
		ItemValue.Breakdown b = ItemValue.of(d, "HYPERION", 800_000_000, 1, P, refs(), ItemValue.CATEGORIES.keySet(), 1.0);
		double expected = 800e6 + 2e6 + 1.5e6 + (150 + 300 + 500 + 900 + 1500) * 2000.0 + 10e6 + 20e6 + 6e6 + 10 * 80e3 + 2 * 1e6
				+ 15e6 + 3e6 + 4e6 + 300e6 + 9e6 + 40e6;
		assertEquals(expected, b.total(), 1);
		assertEquals("Implosion Scroll", b.parts().get(0).label()); // biggest first
		assertTrue(b.parts().stream().anyMatch(p -> p.label().equals("Ultimate Wise V")));
		assertTrue(b.parts().stream().anyMatch(p -> p.label().equals("3350 Wither essence")));
		assertTrue(b.parts().stream().anyMatch(p -> p.label().equals("Withered (Wither Blood)")));
		assertEquals("scrolls", b.byCategory().get(0).category());
	}

	@Test
	public void categoriesShareAndStacks() {
		JsonObject d = JsonParser.parseString("{\"id\":\"HYPERION\",\"rarity_upgrades\":1,\"hot_potato_count\":3}").getAsJsonObject();
		ItemValue.Breakdown only = ItemValue.of(d, "HYPERION", 100, 1, P, ItemValue.Refs.EMPTY, Set.of("potato"), 0.5);
		assertEquals(100 + 3 * 80e3 * 0.5, only.total(), 1e-6);
		// a stack: base × count, no upgrades on plain items
		ItemValue.Breakdown stack = ItemValue.of(null, "ENCHANTED_DIAMOND", 1000, 64, P, ItemValue.Refs.EMPTY, Set.of(), 1);
		assertEquals(64_000, stack.total(), 1e-6);
		// unknown prices are left out, not counted as zero-value lines
		JsonObject unknown = JsonParser.parseString("{\"id\":\"X\",\"ability_scroll\":[\"NOT_A_THING\"]}").getAsJsonObject();
		assertTrue(ItemValue.of(unknown, "X", Double.NaN, 1, P, ItemValue.Refs.EMPTY, ItemValue.CATEGORIES.keySet(), 1).parts().isEmpty());
	}

	@Test
	public void petsAndBooks() {
		JsonObject pet = JsonParser.parseString("{\"id\":\"PET\",\"petInfo\":\"{\\\"type\\\":\\\"ENDER_DRAGON\\\",\\\"tier\\\":\\\"LEGENDARY\\\","
				+ "\\\"heldItem\\\":\\\"MINOS_RELIC\\\",\\\"skin\\\":\\\"ENDER_DRAGON_BABY\\\"}\"}").getAsJsonObject();
		ItemValue.Breakdown b = ItemValue.of(pet, "ENDER_DRAGON;4", 500e6, 1, P, ItemValue.Refs.EMPTY, ItemValue.CATEGORIES.keySet(), 1);
		assertEquals(500e6 + 30e6 + 50e6, b.total(), 1);
		// an enchanted book's enchant is its own price, not an upgrade on top
		JsonObject book = JsonParser.parseString("{\"id\":\"ENCHANTED_BOOK\",\"enchantments\":{\"ultimate_wise\":5}}").getAsJsonObject();
		assertEquals(2e6, ItemValue.of(book, "ULTIMATE_WISE;5", 2e6, 1, P, ItemValue.Refs.EMPTY, ItemValue.CATEGORIES.keySet(), 1).total(), 1);
	}

	@Test
	public void trimKeepsOnlyValuationKeys() {
		JsonObject d = JsonParser.parseString("{\"id\":\"HYPERION\",\"uuid\":\"u\",\"timestamp\":5,\"originTag\":\"x\",\"hot_potato_count\":10}").getAsJsonObject();
		JsonObject t = ItemValue.trim(d);
		assertNotNull(t);
		assertEquals(Set.of("id", "hot_potato_count"), t.keySet());
		assertNull(ItemValue.trim(JsonParser.parseString("{\"id\":\"DIRT\",\"uuid\":\"u\"}").getAsJsonObject()));
	}

	@Test
	public void networthSummaryAndHistory() throws Exception {
		var sum = io.github.donttemptfate.fatesofa.planner.Networth.summarise(java.util.List.of(
				new io.github.donttemptfate.fatesofa.planner.Networth.Entry("Inventory", "HYPERION", "Hyperion", 1, 900e6),
				new io.github.donttemptfate.fatesofa.planner.Networth.Entry("Storage", "ENCHANTED_DIAMOND", "Enchanted Diamond", 64, 100_000),
				new io.github.donttemptfate.fatesofa.planner.Networth.Entry("Purse", "COINS", "Purse", 1, 50e6),
				new io.github.donttemptfate.fatesofa.planner.Networth.Entry("Storage", "X", "Unpriced", 1, Double.NaN)), 5);
		assertEquals(950.1e6, sum.total(), 1);
		assertEquals("Inventory", sum.byCategory().keySet().iterator().next());
		assertEquals(2, sum.top().size()); // purse isn't an item; unpriced left out
		java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("nw");
		var h = new io.github.donttemptfate.fatesofa.planner.Networth.History(dir.resolve("networth.json"));
		java.time.LocalDate d = java.time.LocalDate.of(2026, 10, 17);
		assertTrue(Double.isNaN(h.record(d, 100)));
		assertEquals(100, h.record(d.plusDays(1), 150), 1e-9);
		assertEquals(100, h.record(d.plusDays(1), 160), 1e-9); // same day again: still compared with the day before
		var again = new io.github.donttemptfate.fatesofa.planner.Networth.History(dir.resolve("networth.json"));
		assertEquals(160, again.all().get(d.plusDays(1)), 1e-9);
	}

	@Test
	public void protectionNpcDropAndAuction() {
		var p = new io.github.donttemptfate.fatesofa.protect.Protection();
		p.confirmValue = 0;
		var f = new io.github.donttemptfate.fatesofa.protect.Protection.Facts(null, "HYPERION", "Hyperion", 900e6, 3, 100_000);
		assertEquals(io.github.donttemptfate.fatesofa.protect.Protection.Verdict.CONFIRM, p.decide(io.github.donttemptfate.fatesofa.protect.Protection.Action.NPC_SELL, f, 1000).verdict());
		assertEquals(io.github.donttemptfate.fatesofa.protect.Protection.Verdict.ALLOW, p.decide(io.github.donttemptfate.fatesofa.protect.Protection.Action.NPC_SELL, f, 1500).verdict());
		// dropping: separate threshold
		p.dropConfirmValue = 1_000_000;
		assertEquals(io.github.donttemptfate.fatesofa.protect.Protection.Verdict.CONFIRM, p.decide(io.github.donttemptfate.fatesofa.protect.Protection.Action.DROP, f, 5000).verdict());
		// auctions: far under lowest BIN or an extra zero
		var item = new io.github.donttemptfate.fatesofa.protect.Protection.Facts("u", "HYPERION", "Hyperion", 900e6, 13);
		assertEquals(io.github.donttemptfate.fatesofa.protect.Protection.Verdict.CONFIRM, p.decideAuction(item, 90e6, 800e6, 10_000).verdict());
		assertEquals(io.github.donttemptfate.fatesofa.protect.Protection.Verdict.ALLOW, p.decideAuction(item, 90e6, 800e6, 10_500).verdict());
		assertEquals(io.github.donttemptfate.fatesofa.protect.Protection.Verdict.CONFIRM, p.decideAuction(item, 9_000e6, 800e6, 20_000).verdict());
		assertEquals(io.github.donttemptfate.fatesofa.protect.Protection.Verdict.ALLOW, p.decideAuction(item, 850e6, 800e6, 30_000).verdict());
	}

	@Test
	public void updateVersions() {
		var U = io.github.donttemptfate.fatesofa.data.Updates.class;
		assertTrue(io.github.donttemptfate.fatesofa.data.Updates.newer("0.1.0-beta.1", "0.1.0-alpha.1+mc26.3"));
		assertTrue(io.github.donttemptfate.fatesofa.data.Updates.newer("0.1.0-beta.10", "0.1.0-beta.9"));
		assertTrue(io.github.donttemptfate.fatesofa.data.Updates.newer("0.1.0", "0.1.0-beta.9"));
		assertTrue(io.github.donttemptfate.fatesofa.data.Updates.newer("0.2.0-alpha.1", "0.1.9"));
		assertTrue(!io.github.donttemptfate.fatesofa.data.Updates.newer("0.1.0-alpha.1", "0.1.0-alpha.1+mc26.2"));
		assertTrue(!io.github.donttemptfate.fatesofa.data.Updates.newer("0.1.0-beta.1", "0.1.0"));
		var l = io.github.donttemptfate.fatesofa.data.Updates.parse("{\"version\":\"0.1.0-beta.1\",\"url\":\"https://evil.example/x\",\"prerelease\":true}");
		assertEquals("https://github.com/DontTemptFate/Fates_QoL/releases", l.url()); // links only ever point at the OFA repo
		assertTrue(l.prerelease());
		try {
			io.github.donttemptfate.fatesofa.data.Updates.parse("{\"version\":\"1.0; rm -rf\"}");
			throw new AssertionError("accepted a bad version");
		} catch (IllegalArgumentException expected) {
			// fine
		}
		assertNotNull(U);
	}
}
