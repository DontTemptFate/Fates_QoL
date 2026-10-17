package io.github.donttemptfate.fatesofa;

import com.google.gson.JsonPrimitive;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.config.Settings;
import io.github.donttemptfate.fatesofa.core.ErrorBudget;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.HudPos;
import io.github.donttemptfate.fatesofa.hud.Layout;
import io.github.donttemptfate.fatesofa.hud.Rect;
import io.github.donttemptfate.fatesofa.state.Island;
import io.github.donttemptfate.fatesofa.state.ProfileType;
import io.github.donttemptfate.fatesofa.state.ScoreboardInfo;
import io.github.donttemptfate.fatesofa.state.SkyBlockState;
import io.github.donttemptfate.fatesofa.state.TabInfo;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CoreTest {
	@Test
	public void errorBudget() {
		ErrorBudget b = new ErrorBudget();
		assertFalse(b.record(0, "a"));
		assertFalse(b.record(70_000, "b")); // first one has aged out of the window
		assertFalse(b.record(80_000, "c")); // only 2 within a minute (70k, 80k)... plus 0 out of window
		assertTrue(b.record(90_000, "d"));
		assertTrue(b.tripped());
		assertFalse(b.record(91_000, "e")); // already off
		b.reset();
		assertFalse(b.tripped());
	}

	@Test
	public void settingsRoundTrip() throws Exception {
		Path dir = Files.createTempDirectory("ofa");
		Settings s = new Settings(dir.resolve("settings.json"));
		Setting<Boolean> flag = s.declare(Setting.bool("a.on", "A", true).build());
		Setting<Integer> count = s.declare(Setting.integer("a.count", "Count", 5, 1, 10).build());
		Setting<String> mode = s.declare(Setting.choice("a.mode", "Mode", "x", List.of("x", "y")).build());
		assertTrue(s.get(flag));
		s.set(flag, false);
		s.set(count, 7);
		s.set(mode, "y");
		s.save();
		Settings again = new Settings(dir.resolve("settings.json"));
		again.declare(flag);
		again.declare(count);
		again.declare(mode);
		again.load();
		assertFalse(again.get(flag));
		assertEquals(7, (int) again.get(count));
		assertEquals("y", again.get(mode));
		// out-of-range values fall back to the default
		again.putRaw("a.count", new JsonPrimitive(99));
		assertEquals(5, (int) again.get(count));
		// setting the default removes the stored value
		again.set(mode, "x");
		assertTrue(again.isDefault(mode));
		// export / import
		String line = again.export();
		Settings third = new Settings(null);
		third.declare(flag);
		third.declare(count);
		third.declare(mode);
		assertEquals(1, third.importLine(line)); // a.on=false (count was out of range)
		assertFalse(third.get(flag));
		assertEquals(-1, third.importLine("garbage"));
		// search
		Setting<Boolean> search = Setting.bool("x", "Chest value", true).help("Shows the top items").keywords("backpack ender").build();
		assertTrue(search.matches("ender chest"));
		assertFalse(search.matches("dungeon"));
	}

	@Test
	public void brokenSettingsFileMeansDefaults() throws Exception {
		Path dir = Files.createTempDirectory("ofa");
		Path f = dir.resolve("settings.json");
		Files.writeString(f, "{not json");
		Settings s = new Settings(f);
		Setting<Boolean> flag = s.declare(Setting.bool("a.on", "A", true).build());
		s.load();
		assertTrue(s.get(flag));
		assertTrue(Files.exists(dir.resolve("settings.json.broken")));
	}

	@Test
	public void layoutAvoidsOverlapAndReserved() {
		List<Layout.Item> items = List.of(
				new Layout.Item("a", 100, 20, HudPos.at(Anchor.TOP_LEFT, 5, 5)),
				new Layout.Item("b", 100, 20, HudPos.at(Anchor.TOP_LEFT, 5, 10)),
				new Layout.Item("c", 50, 10, HudPos.at(Anchor.BOTTOM, 0, -2)));
		Rect hotbar = new Rect(229, 456, 182, 24);
		Map<String, Rect> r = Layout.place(items, 640, 480, List.of(hotbar));
		assertEquals(new Rect(5, 5, 100, 20), r.get("a"));
		assertFalse(r.get("b").overlaps(r.get("a")));
		assertTrue(r.get("b").y() >= 25);
		assertFalse(r.get("c").overlaps(hotbar));
		assertTrue(r.get("c").bottom() <= 456);
		// off-screen offsets are pulled back on screen
		Map<String, Rect> r2 = Layout.place(List.of(new Layout.Item("d", 50, 50, HudPos.at(Anchor.TOP_RIGHT, 400, -300))), 640, 480, List.of());
		assertEquals(new Rect(640 - 50 - 2, 2, 50, 50), r2.get("d"));
		// position kept when re-anchored
		HudPos p = HudPos.fromScreen(Anchor.BOTTOM_RIGHT, 500, 400, 100, 20, 640, 480, 1f);
		Rect back = Layout.place(List.of(new Layout.Item("e", 100, 20, p)), 640, 480, List.of()).get("e");
		assertEquals(500, back.x());
		assertEquals(400, back.y());
	}

	@Test
	public void scoreboard() {
		ScoreboardInfo i = ScoreboardInfo.parse("§e§lSKYBLOCK §b§lCO-OP", List.of(
				"§710/17/26 §8m123AB", "", " Late Autumn 15th", " §72:40pm §e☀", " §7⏣ §bVillage", "",
				"Purse: §61,234,567 §e(+5)", "Bits: §b12,345", "", "Objective", "§eTalk to the Elder", "", "§ewww.hypixel.net"));
		assertTrue(i.skyblock());
		assertEquals("m123AB", i.server());
		assertEquals("Village", i.area());
		assertEquals(1_234_567, i.purse(), 0);
		assertEquals(12_345, i.bits(), 0);
		assertEquals("Late Autumn 15th", i.date());
		assertEquals(List.of("Talk to the Elder"), i.objective());

		ScoreboardInfo d = ScoreboardInfo.parse("SKYBLOCK", List.of("§710/17/26 §8m55C", "", " §7⏣ §cThe Catacombs §7(F7)", "",
				"Cleared: §c45% §8(123)", "Time Elapsed: §a3m 15s"));
		assertEquals("F7", d.dungeonFloor());
		assertEquals(45, (int) d.clearedPercent());

		ScoreboardInfo s = ScoreboardInfo.parse("SKYBLOCK", List.of("Slayer Quest", "§cRevenant Horror IV", "§7(1,234/1,500) §cCombat XP"));
		assertEquals("Revenant Horror IV", s.slayerBoss());
		assertEquals("(1,234/1,500) Combat XP", s.slayerProgress());

		assertFalse(ScoreboardInfo.parse("§e§lBED WARS", List.of("Map: Lighthouse")).skyblock());
	}

	@Test
	public void tabAndState() {
		TabInfo t = TabInfo.parse(List.of("§b§lArea: §7Dwarven Mines", "Server: mini123AB", "§e§lProfile: §aKiwi ♲", "SB Level: §8[§b146§8]", "Bank: 1.2B"));
		assertEquals(Island.DWARVEN_MINES, t.island());
		assertEquals("Kiwi", t.profileName());
		assertEquals(ProfileType.IRONMAN, t.profileType());
		assertEquals(146, (int) t.skyblockLevel());
		assertEquals(Island.OTHER, Island.fromName("Some New Island"));
		assertEquals(Island.CRYSTAL_HOLLOWS, Island.fromMode("crystal_hollows"));

		SkyBlockState st = new SkyBlockState();
		Island[] seen = new Island[1];
		st.onIslandChange(i -> seen[0] = i);
		st.setOnHypixel(true);
		st.fromScoreboard(ScoreboardInfo.parse("SKYBLOCK", List.of("Purse: 5")), 1000);
		st.fromTab(t, 1000);
		assertTrue(st.onSkyBlock());
		assertEquals(Island.DWARVEN_MINES, st.island());
		assertEquals(Island.DWARVEN_MINES, seen[0]);
		// the Mod API wins over the tab list
		st.fromModApi("mini1", "crystal_hollows", 2000);
		st.fromTab(t, 2100);
		assertEquals(Island.CRYSTAL_HOLLOWS, st.island());
		st.setOnHypixel(false);
		assertNull(st.island());
	}
}
