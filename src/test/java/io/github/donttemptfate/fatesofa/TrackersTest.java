package io.github.donttemptfate.fatesofa;

import io.github.donttemptfate.fatesofa.activity.ItemFlow;
import io.github.donttemptfate.fatesofa.activity.MenuReaders;
import io.github.donttemptfate.fatesofa.activity.SkillXp;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Batch 8a: skill XP, item flow, menu readers. */
public class TrackersTest {
	@Test
	public void skillXpFromActionBar() {
		var g = SkillXp.parse("1,234/1,500❤     +12.5 Farming (1,234,567/2,000,000)     300/300✎ Mana");
		assertEquals("Farming", g.skill());
		assertEquals(12.5, g.amount(), 1e-9);
		assertEquals(2_000_000, g.needed(), 1e-6);
		var p = SkillXp.parse("+5 Combat (45.2%)");
		assertEquals(45.2, p.percent(), 1e-9);
		assertTrue(Double.isNaN(p.current()));
		assertNull(SkillXp.parse("1,234/1,500❤"));
		SkillXp x = new SkillXp();
		assertTrue(x.actionBar("+10 Mining (100/1,000)", 0) != null);
		assertNull("the same bar again is not a second gain", x.actionBar("+10 Mining (100/1,000)", 500));
		assertTrue(x.actionBar("+10 Mining (110/1,000)", 600) != null);
		x.actionBar("+10 Mining (120/1,000)", 70_000);
		var s = x.skills().get("Mining");
		assertEquals(30, s.gained, 1e-9);
		assertTrue(s.perHour() > 0 && s.toLevelMs() > 0);
	}

	@Test
	public void sacksAndInventory() {
		var c = ItemFlow.sackLines("Added items:\n +1,280 Enchanted Carrot (Agronomy Sack)\n +5 Wheat (Agronomy Sack)\n\nRemoved items:\n -3 Wheat (Agronomy Sack)", 10);
		assertEquals(2, c.size());
		assertEquals("Enchanted Carrot", c.get(0).name());
		assertEquals(1280, c.get(0).amount());
		assertTrue(ItemFlow.SACKS_MSG.matcher("[Sacks] +1,285 items. (Last 30s.)").matches());
		var grew = ItemFlow.grew(Map.of("A", 3, "B", 10), Map.of("A", 5, "B", 4, "C", 1));
		assertEquals(Map.of("A", 2, "C", 1), grew);
		assertTrue(ItemFlow.grew(null, Map.of("A", 1)).isEmpty());
		ItemFlow f = new ItemFlow();
		f.add(new ItemFlow.Change("Wheat", "WHEAT", 5, "inventory", 1000));
		f.add(new ItemFlow.Change("wheat", null, 3, "sacks", 2000));
		assertEquals(8, f.total("WHEAT").amount);
		assertEquals("WHEAT", f.total("wheat").key);
		assertEquals(1, f.recent(61_500, 60_000, 10).size());
	}

	@Test
	public void menuReaders() {
		var col = MenuReaders.collection(List.of("View all your Wheat Collection", "", "Progress to Wheat IX: 45.3%", "               12,345/25k"));
		assertEquals("Wheat", col.name());
		assertEquals("IX", col.nextTier());
		assertEquals(25_000, col.needed(), 1e-6);
		assertArrayEquals(new String[] {"Snow", "XI"}, MenuReaders.minion("Snow Minion XI"));
		assertNull(MenuReaders.minion("Minion Shop"));
		assertEquals(((24 + 4) * 60 + 20) * 60_000L, MenuReaders.timeLeft(List.of("Enchanted Lava Bucket", "Time Remaining: 1d 4h 20m")));
		var up = MenuReaders.upgrade(List.of("Rabbit Bro", "Produces +2 Chocolate per second", "", "Cost", "12,500 Chocolate", "", "Click to hire!"));
		assertEquals(2, up.gainPerSecond(), 1e-9);
		assertEquals(6250, up.costPerChocolate(), 1e-6);
		assertNull(MenuReaders.upgrade(List.of("Some button")));
		assertEquals(12_345, MenuReaders.perSecond(List.of("Chocolate Production", "Per Second: 12,345")), 1e-6);
	}
}
