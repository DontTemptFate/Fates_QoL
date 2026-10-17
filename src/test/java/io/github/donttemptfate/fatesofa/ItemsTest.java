package io.github.donttemptfate.fatesofa;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.donttemptfate.fatesofa.data.Price;
import io.github.donttemptfate.fatesofa.items.PetLevels;
import io.github.donttemptfate.fatesofa.items.Valuer;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ItemsTest {
	@Test
	public void petLevels() {
		// tiny table: levels cost 100, 200, 300, ...; legendary offset 1; max level 4 for the test pet
		JsonObject pets = JsonParser.parseString("{pet_levels:[100,200,300,400,500,600],pet_rarity_offset:{COMMON:0,LEGENDARY:1},"
				+ "custom_pet_leveling:{SMALL:{max_level:4}}}").getAsJsonObject();
		PetLevels pl = new PetLevels(pets);
		PetLevels.Level l = pl.level("SMALL", "LEGENDARY", 250);
		assertEquals(2, l.level()); // 200 to reach 2, 50 into level 2 (needs 300)
		assertEquals(50, l.expIntoLevel(), 0);
		assertEquals(300, l.expForLevel(), 0);
		assertEquals(200 + 300 + 400 - 250, l.expToMax(), 0);
		assertEquals(4, pl.level("SMALL", "LEGENDARY", 1e9).level());
	}

	@Test
	public void topValues() {
		List<Valuer.Valued> in = List.of(
				new Valuer.Valued("A", "Apple", 2, 10, Price.Kind.NPC),
				new Valuer.Valued("B", "Bone", 1, 100, Price.Kind.NPC),
				new Valuer.Valued("A", "Apple", 3, 10, Price.Kind.NPC));
		List<Valuer.Valued> top = Valuer.top(in, 5);
		assertEquals("B", top.get(0).key());
		assertEquals(5, top.get(1).count());
		assertEquals(150, Valuer.total(in), 0);
		assertEquals(1, Valuer.top(in, 1).size());
	}

	@Test
	public void storage() throws Exception {
		assertEquals("ender:3", io.github.donttemptfate.fatesofa.storage.StorageCache.pageId("Ender Chest (3/9)"));
		assertEquals("backpack:12", io.github.donttemptfate.fatesofa.storage.StorageCache.pageId("Jumbo Backpack (Slot #12)"));
		assertEquals(null, io.github.donttemptfate.fatesofa.storage.StorageCache.pageId("Chest"));
		java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("ofa");
		var c = new io.github.donttemptfate.fatesofa.storage.StorageCache(dir);
		c.profile("Kiwi");
		var it = new io.github.donttemptfate.fatesofa.storage.StoredItem(4, "minecraft:diamond", "ENCHANTED_DIAMOND", "§aEnchanted Diamond", List.of("§7Shiny"), null, null, 64);
		c.put("ender:1", "Ender Chest (1/9)", List.of(it), 1);
		assertEquals(1, c.search("enchanted dia").size());
		assertEquals(0, c.search("hyperion").size());
		c.save();
		var c2 = new io.github.donttemptfate.fatesofa.storage.StorageCache(dir);
		c2.profile("Kiwi");
		assertEquals(it, c2.pages().get(0).items().get(0));
	}
}
