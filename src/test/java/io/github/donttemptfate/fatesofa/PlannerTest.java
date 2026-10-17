package io.github.donttemptfate.fatesofa;

import com.google.gson.JsonParser;
import io.github.donttemptfate.fatesofa.data.Ingredient;
import io.github.donttemptfate.fatesofa.data.ItemRepo;
import io.github.donttemptfate.fatesofa.data.Recipe;
import io.github.donttemptfate.fatesofa.data.RepoItem;
import io.github.donttemptfate.fatesofa.planner.Accessories;
import io.github.donttemptfate.fatesofa.planner.Crafts;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlannerTest {
	static RepoItem acc(String key, String rarityLine) {
		return new RepoItem(key, "§a" + key, "minecraft:player_head", 0, List.of("§7Some text", rarityLine), List.of(), "", null, null);
	}

	@Test
	public void accessories() {
		ItemRepo repo = ItemRepo.of(List.of(
				acc("POTATO_TALISMAN", "§f§lCOMMON ACCESSORY"),
				acc("POTATO_RING", "§a§lUNCOMMON ACCESSORY"),
				acc("CHEAP_ONE", "§9§lRARE ACCESSORY"),
				acc("PRICEY_ONE", "§6§lLEGENDARY ACCESSORY"),
				new RepoItem("SWORD", "§fSword", "minecraft:iron_sword", 0, List.of("§f§lCOMMON SWORD"), List.of(), "", null, null)));
		var ups = Accessories.upgrades(JsonParser.parseString("{talisman_upgrades:{POTATO_TALISMAN:['POTATO_RING']}}").getAsJsonObject());
		Map<String, Double> prices = Map.of("POTATO_RING", 1000.0, "CHEAP_ONE", 800.0, "PRICEY_ONE", 16_000_000.0, "POTATO_TALISMAN", 100.0);
		var s = Accessories.missing(repo, Set.of("POTATO_TALISMAN"), ups, Set.of(), k -> prices.getOrDefault(k, Double.NaN), true);
		assertEquals(3, s.size());
		assertEquals("CHEAP_ONE", s.get(0).key()); // 800 / 8 MP = 100 per MP
		assertEquals("POTATO_RING", s.get(1).key()); // 1000 / (5 - 3) = 500 per MP
		assertEquals(2, s.get(1).mpGain());
		// owning the ring hides the talisman
		var s2 = Accessories.missing(repo, Set.of("POTATO_RING"), ups, Set.of(), k -> prices.getOrDefault(k, Double.NaN), true);
		assertTrue(s2.stream().noneMatch(x -> x.key().startsWith("POTATO")));
		// ironman: only craftable ones (none here)
		assertEquals(0, Accessories.missing(repo, Set.of(), ups, Set.of(), k -> 1, false).size());
	}

	@Test
	public void crafts() {
		Recipe r = new Recipe(Recipe.Type.CRAFTING, "ENCHANTED_DIAMOND", 1, List.of(new Ingredient("DIAMOND", 160)), null, 0, null);
		ItemRepo repo = ItemRepo.of(List.of(new RepoItem("ENCHANTED_DIAMOND", "§aEnchanted Diamond", "minecraft:diamond", 0, List.of(), List.of(r), "", null, null)));
		Map<String, Double> val = Map.of("DIAMOND", 8.0, "ENCHANTED_DIAMOND", 1500.0);
		var o = Crafts.fromOwned(repo, Map.of("DIAMOND", 500.0), k -> val.getOrDefault(k, Double.NaN), 10);
		assertEquals(1, o.size());
		assertEquals(3, o.get(0).times());
		assertEquals(1500 - 1280, o.get(0).gain(), 0.01);
	}
}
