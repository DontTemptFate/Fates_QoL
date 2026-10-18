package io.github.donttemptfate.fatesofa;

import io.github.donttemptfate.fatesofa.activity.IslandTrackers;
import io.github.donttemptfate.fatesofa.state.TabInfo;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Batch 8d: island tracker readers. */
public class IslandsTest {
	@Test
	public void tabWidgets() {
		TabInfo t = TabInfo.parse(List.of("Area: Crystal Hollows", "", "Crystals:", " Jade: ✔ Found", " Amber: ✖ Not Found", " Amethyst: Placed", "",
				"Reputation:", " Barbarian Reputation: 1,234", " Mage Reputation: 2,500", "", "Faction Quests:", " ✔ Rescue Mission", " ✖ Kill Bladesoul", "",
				"Enigma Souls: 34/52"));
		var c = IslandTrackers.crystals(t);
		assertEquals("Found", c.get("Jade"));
		assertEquals("Not Found", c.get("Amber"));
		assertEquals("Placed", c.get("Amethyst"));
		var r = IslandTrackers.reputation(t);
		assertEquals(2500, r.get("Mage"), 1e-9);
		assertArrayEquals(new int[] {1, 2}, IslandTrackers.factionQuests(t));
		assertArrayEquals(new int[] {34, 52}, IslandTrackers.enigmaSouls(t));
	}

	@Test
	public void chatAndMenus() {
		assertArrayEquals(new String[] {"corpse", "TUNGSTEN"}, IslandTrackers.chat("  TUNGSTEN CORPSE LOOT! "));
		assertEquals("mineshaft", IslandTrackers.chat("WOW! You found a Glacite Mineshaft portal!")[0]);
		assertEquals("enigma", IslandTrackers.chat("SOUL! You unlocked an Enigma Soul!")[0]);
		assertNull(IslandTrackers.chat("hello"));
		assertEquals(2, IslandTrackers.trophyTiers(List.of("Sulphur Skitter", "Bronze ✔", "Silver ✔", "Gold ✖", "Diamond ✖")));
	}
}
