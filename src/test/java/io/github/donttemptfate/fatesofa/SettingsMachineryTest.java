package io.github.donttemptfate.fatesofa;

import io.github.donttemptfate.fatesofa.chat.CustomRules;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.config.Settings;
import io.github.donttemptfate.fatesofa.config.Values;
import io.github.donttemptfate.fatesofa.core.Alert;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Display;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.core.Highlight;
import io.github.donttemptfate.fatesofa.core.Tracker;
import io.github.donttemptfate.fatesofa.state.Island;
import org.junit.Test;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Batch 4: the new setting types, their value helpers and the option bundles. */
public class SettingsMachineryTest {
	static final class Demo extends Feature {
		final Display display;
		final Highlight highlight;
		final Alert alert;
		final Tracker tracker;

		Demo() {
			super("demo", "Demo", "A test feature.", Category.HUD, true, Risk.SAFE);
			Map<String, String> lines = new LinkedHashMap<>();
			lines.put("a", "Alpha");
			lines.put("b", "Beta");
			lines.put("c", "Gamma");
			display = new Display(this, "demo", 0xFF55FFFF, lines, List.of("a", "b"));
			highlight = new Highlight(this, "demo.hl", 0xFFFF5555);
			alert = new Alert(this, "demo.alert", "Demo alert", "{mob}!", "at {where}", 0xFFFFAA00, "minecraft:block.note_block.pling", "mob", "where");
			tracker = new Tracker(this, "demo.tracker");
		}
	}

	@Test
	public void bundlesGiveManyOptionsAndDeclareCleanly() {
		Demo d = new Demo();
		Settings s = new Settings(null);
		d.declare(s);
		assertTrue("bundles should add plenty of options: " + d.settings().size(), d.settings().size() >= 25);
		// the colour's rainbow switch is declared too, hidden from the list
		assertNotNull(s.find("demo.accent.rainbow"));
		assertTrue(s.find("demo.accent.rainbow").hidden);
		// every bundle setting belongs to a group
		for (Setting<?> x : d.settings()) assertNotNull(x.key + " has no group", x.group);
		assertTrue(d.settings().stream().anyMatch(x -> x.advanced));
	}

	@Test
	public void displayIslandsMenusLinesAndRainbow() {
		Demo d = new Demo();
		Settings s = new Settings(null);
		d.declare(s);
		s.set(d.enabled, true);
		assertTrue(d.display.visible(Island.HUB, false));
		assertTrue("everywhere even when the island is unknown", d.display.visible(null, false));
		s.set(d.display.showOn, Values.toggleIsland("", Island.GARDEN));
		assertFalse(d.display.visible(Island.HUB, false));
		assertTrue(d.display.visible(Island.GARDEN, false));
		assertFalse(d.display.visible(null, false));
		s.set(d.display.hideInMenus, true);
		assertFalse(d.display.visible(Island.GARDEN, true));
		assertEquals(List.of("a", "b"), d.display.lines());
		s.set(d.display.lines, Values.moveLine(s.get(d.display.lines), d.display.lines.options, "b", -1));
		assertEquals(List.of("b", "a"), d.display.lines());
		s.set(d.display.lines, Values.toggleLine(s.get(d.display.lines), d.display.lines.options, "c"));
		assertEquals(List.of("b", "a", "c"), d.display.lines());
		assertEquals(0.8f, d.display.alpha(), 1e-6);
		s.set(d.display.accent.rainbow, true);
		int c1 = d.display.accent(0), c2 = d.display.accent(1000);
		assertTrue("rainbow should change over time", c1 != c2);
		assertEquals(0xFF000000, c1 & 0xFF000000);
	}

	@Test
	public void alertFillsTemplatesHonoursCooldownAndIslands() {
		Demo d = new Demo();
		Settings s = new Settings(null);
		d.declare(s);
		s.set(d.enabled, true);
		List<String> got = new ArrayList<>();
		Alert.sink(new Alert.Sink() {
			public void title(String t, String sub, int rgb) {
				got.add("title " + t + " / " + sub);
			}

			public void sound(Values.Sound snd) {
				got.add("sound " + snd.id());
			}

			public void chat(String line) {
				got.add("chat " + line);
			}
		});
		try {
			assertTrue(d.alert.fire(Map.of("mob", "Inquisitor", "where", "Hub"), Island.HUB, 1000));
			assertEquals(List.of("title Inquisitor! / at Hub", "sound minecraft:block.note_block.pling"), got);
			s.set(d.alert.cooldown, 10);
			assertFalse("on cooldown", d.alert.fire(Map.of(), Island.HUB, 5000));
			assertTrue(d.alert.fire(Map.of(), Island.HUB, 20_000));
			s.set(d.alert.onlyOn, "GARDEN");
			assertFalse(d.alert.fire(Map.of(), Island.HUB, 100_000));
			s.set(d.enabled, false);
			assertFalse("a switched-off feature never alerts", d.alert.fire(Map.of(), Island.GARDEN, 200_000));
		} finally {
			Alert.sink(null);
		}
	}

	@Test
	public void trackerResets() {
		Demo d = new Demo();
		Settings s = new Settings(null);
		d.declare(s);
		long day = 86_400_000L;
		assertTrue(d.tracker.shouldReset(0, 10, false, true, ZoneOffset.UTC));
		assertFalse(d.tracker.shouldReset(0, 10, true, false, ZoneOffset.UTC));
		s.set(d.tracker.reset, "daily");
		assertFalse(d.tracker.shouldReset(day + 5, day + 500, false, false, ZoneOffset.UTC));
		assertTrue(d.tracker.shouldReset(day - 5, day + 5, false, false, ZoneOffset.UTC));
		s.set(d.tracker.reset, "island_change");
		assertTrue(d.tracker.shouldReset(0, 1, true, false, ZoneOffset.UTC));
		assertTrue(d.tracker.paused(0, 61_000));
		s.set(d.tracker.idlePause, 0);
		assertFalse(d.tracker.paused(0, 10_000_000));
	}

	@Test
	public void soundValues() {
		assertTrue(Values.sound("").silent());
		assertTrue(Values.sound("not a sound").silent());
		Values.Sound s = Values.sound("minecraft:block.note_block.pling|5|0.1");
		assertEquals(2f, s.volume(), 1e-6);
		assertEquals(0.5f, s.pitch(), 1e-6);
		assertEquals("minecraft:block.note_block.pling|2|0.5", s.encode());
		String next = Values.nextSound("minecraft:block.note_block.pling|0.5|1.5", 1);
		assertEquals("minecraft:entity.experience_orb.pickup|0.5|1.5", next);
		assertEquals("", Values.nextSound("minecraft:block.note_block.pling", -1));
		assertEquals("Pling", Values.soundName("minecraft:block.note_block.pling"));
	}

	@Test
	public void templatesRulesAndColours() {
		assertEquals("§6Hi Fate {x}", Values.fill("&6Hi {name} {x}", Map.of("name", "Fate")));
		assertEquals(List.of("x"), Values.unknownPlaceholders("{name} {x} {x}", List.of("name")));
		String r = Values.setRule("", -1, "Watchdog => hide");
		r = Values.setRule(r, -1, "  /^\\[NPC\\]/ => fold ");
		assertEquals(List.of("Watchdog => hide", "/^\\[NPC\\]/ => fold"), Values.rules(r));
		r = Values.setRule(r, 0, "");
		assertEquals(List.of("/^\\[NPC\\]/ => fold"), Values.rules(r));
		assertEquals(Integer.valueOf(0xFFFF0000), Values.parseHex("#F00"));
		assertEquals(Integer.valueOf(0x80112233), Values.parseHex("80112233"));
		assertNull(Values.parseHex("#12"));
		assertEquals("#112233", Values.hex(0xFF112233));
		assertEquals("#80112233", Values.hex(0x80112233));
		assertEquals(0x7F112233, Values.withChannel(0xFF112233, 24, 0x7F));
		assertEquals(0xFF112299, Values.withChannel(0xFF112233, 0, 0x99));
	}

	@Test
	public void customChatRules() {
		CustomRules.Parsed p = CustomRules.parseNow("# comment\nWatchdog\n/^\\[NPC\\] Kat/ => alert\nfoo => explode\n/([/ => hide\nguild => FOLD");
		assertEquals(3, p.rules().size());
		assertEquals(2, p.problems().size());
		CustomRules c = new CustomRules();
		String text = "Watchdog\n/^\\[NPC\\] Kat/ => alert\nguild => fold";
		assertEquals(CustomRules.Action.HIDE, c.match(text, "[WATCHDOG ANNOUNCEMENT] blah").action());
		assertEquals(CustomRules.Action.ALERT, c.match(text, "[NPC] Kat: your pet is ready").action());
		assertEquals(CustomRules.Action.FOLD, c.match(text, "Guild > someone joined").action());
		assertNull(c.match(text, "hello"));
	}

	@Test
	public void textTypesStoredAndChecked() {
		Settings s = new Settings(null);
		Setting<String> rules = s.declare(Setting.rules("t.rules", "Rules", "").maxLength(20).build());
		Setting<String> isl = s.declare(Setting.islands("t.isl", "Islands", "").build());
		Setting<Integer> key = s.declare(Setting.key("t.key", "Key").build());
		s.set(rules, "a => hide");
		assertEquals("a => hide", s.get(rules));
		s.set(rules, "x".repeat(21)); // too long: refused, old value stays
		assertEquals("a => hide", s.get(rules));
		s.set(isl, "HUB,GARDEN,NOT_AN_ISLAND");
		assertEquals(2, Values.islands(s.get(isl)).size());
		assertEquals(Integer.valueOf(-1), s.get(key));
		s.set(key, 82);
		String line = s.export();
		Settings other = new Settings(null);
		other.declare(Setting.rules("t.rules", "Rules", "").maxLength(20).build());
		Setting<Integer> key2 = other.declare(Setting.key("t.key", "Key").build());
		assertTrue(other.importLine(line) >= 2);
		assertEquals(Integer.valueOf(82), other.get(key2));
	}
}
