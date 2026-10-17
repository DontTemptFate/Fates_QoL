package io.github.donttemptfate.fates.common;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.donttemptfate.fates.common.item.ItemKey;
import io.github.donttemptfate.fates.common.item.Rarity;
import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.text.Strip;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CommonTest {
	@Test
	public void shortNumbers() {
		assertEquals("999", Fmt.shortNum(999));
		assertEquals("1.2k", Fmt.shortNum(1_200));
		assertEquals("34k", Fmt.shortNum(34_000));
		assertEquals("1.25M", Fmt.shortNum(1_250_000));
		assertEquals("120M", Fmt.shortNum(120_000_000));
		assertEquals("1M", Fmt.shortNum(999_999));
		assertEquals("-2.5M", Fmt.shortNum(-2_500_000));
		assertEquals("+1.5k", Fmt.signedShort(1_500));
		assertEquals("1.4B", Fmt.shortNum(1_400_000_000));
	}

	@Test
	public void parsing() {
		assertEquals(1_234_567, Fmt.parse("1,234,567"), 0);
		assertEquals(1_200_000, Fmt.parse("1.2M"), 0.01);
		assertEquals(950_000, Fmt.parse("950k"), 0.01);
		assertTrue(Double.isNaN(Fmt.parse("abc")));
		assertEquals(14, Fmt.roman("XIV"));
		assertEquals(50, Fmt.roman("L"));
		assertEquals(0, Fmt.roman("Hello"));
		assertEquals(7, Fmt.level("VII"));
		assertEquals(23, Fmt.level("23"));
	}

	@Test
	public void durations() {
		assertEquals("45s", Fmt.duration(45_000));
		assertEquals("3m 05s", Fmt.duration(185_000));
		assertEquals("2h 05m", Fmt.duration(7_500_000));
		assertEquals("1:05", Fmt.clock(65_000));
		assertEquals("1:02:03", Fmt.clock(3_723_000));
	}

	@Test
	public void stripping() {
		assertEquals("Purse: 1,234", Strip.clean("§6Purse: §61,234"));
		// emoji and private-use icons Hypixel inserts are dropped, SkyBlock symbols kept
		assertEquals("⏣ Hub", Strip.clean("§7⏣ §bHub👾"));
		assertEquals("Late Autumn 15th", Strip.clean(" Late Autumn 15th"));
		assertEquals("crystalhollows", Strip.key("§5Crystal Hollows"));
	}

	@Test
	public void itemKeys() {
		assertEquals("HYPERION", ItemKey.of(json("{id:'HYPERION',uuid:'x'}")));
		assertEquals("INK_SACK-3", ItemKey.of(json("{id:'INK_SACK:3'}")));
		assertEquals("ENDER_DRAGON;4", ItemKey.of(json("{id:'PET',petInfo:'{\"type\":\"ENDER_DRAGON\",\"tier\":\"LEGENDARY\"}'}")));
		assertEquals("ULTIMATE_WISE;5", ItemKey.of(json("{id:'ENCHANTED_BOOK',enchantments:{ultimate_wise:5}}")));
		assertEquals("ENCHANTED_BOOK", ItemKey.of(json("{id:'ENCHANTED_BOOK',enchantments:{sharpness:5,smite:5}}")));
		assertEquals("ZOMBIE_SLASH_RUNE;3", ItemKey.of(json("{id:'RUNE',runes:{ZOMBIE_SLASH:3}}")));
		assertNull(ItemKey.of(json("{foo:1}")));
		assertEquals("PET", ItemKey.of(json("{id:'PET',petInfo:'broken'}")));
	}

	@Test
	public void rarityLine() {
		assertEquals(Rarity.LEGENDARY, Rarity.fromLine("§6§lLEGENDARY SWORD"));
		assertEquals(Rarity.MYTHIC, Rarity.fromLine("§d§l§ka§r §d§lMYTHIC ACCESSORY §d§l§ka"));
		assertEquals(Rarity.VERY_SPECIAL, Rarity.fromLine("§c§lVERY SPECIAL HATCESSORY"));
		assertEquals(Rarity.COMMON, Rarity.fromLine("§f§lCOMMON"));
		assertNull(Rarity.fromLine("§7Damage: §c+100"));
	}

	/** The shared library must never depend on a mod's own code, so it can move to its own repo. */
	@Test
	public void commonDependsOnNoMod() throws IOException {
		Path root = Path.of("common/src/main/java");
		if (!Files.isDirectory(root)) root = Path.of("/home/claude/fates_qol/common/src/main/java");
		if (!Files.isDirectory(root)) return;
		Pattern bad = Pattern.compile("import io\\.github\\.donttemptfate\\.(fatesofa|fatesflipper)");
		try (Stream<Path> files = Files.walk(root)) {
			files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
				try {
					assertTrue("common imports a mod: " + p, !bad.matcher(Files.readString(p)).find());
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}

	static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}
}
