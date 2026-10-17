package io.github.donttemptfate.fatesofa;

import io.github.donttemptfate.fatesofa.core.ActivitySession;
import io.github.donttemptfate.fatesofa.dungeon.DungeonMap;
import io.github.donttemptfate.fatesofa.dungeon.DungeonScore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LogicTest {
	@Test
	public void session() {
		ActivitySession s = new ActivitySession(60_000);
		s.add("WHEAT", 10, 0);
		s.add("WHEAT", 10, 30_000);
		s.add("WHEAT", 10, 400_000); // 370 s gap: AFK, not counted
		s.add("WHEAT", 10, 430_000);
		assertEquals(60_000, s.activeMs());
		assertEquals(40, s.count("WHEAT"), 0);
		assertEquals(2400, s.perHour(40), 0.01);
		s.pause();
		s.add("WHEAT", 1, 440_000);
		assertEquals(60_000, s.activeMs());
	}

	@Test
	public void dungeonScore() {
		assertEquals(100, DungeonScore.speedScore(300));
		assertEquals(90, DungeonScore.speedScore(600)); // ceil(115 - 25)
		assertEquals(0.85, DungeonScore.secretRequirement("F6"), 0);
		assertEquals(1.0, DungeonScore.secretRequirement("M3"), 0);
		assertEquals(360, DungeonScore.adjustedSeconds("F7", 720));
		DungeonScore.Result r = DungeonScore.estimate(new DungeonScore.Input("F7", 100, 60, 60, 0, false, 0, 5, true, false, false, 600_000, true));
		assertEquals(100, r.skill());
		assertEquals(100, r.explore());
		assertEquals(100, r.speed());
		assertEquals(7, r.bonus());
		assertEquals("S+", r.grade());
		DungeonScore.Result d = DungeonScore.estimate(new DungeonScore.Input("F7", 100, 60, 60, 2, true, 1, 5, true, false, false, 600_000, true));
		assertEquals(100 - 3 - 14, d.skill());
		assertTrue(DungeonScore.estimate(new DungeonScore.Input("F7", 50, 10, -1, 0, false, 0, 0, false, false, false, 60_000, false)).provisional());
	}

	@Test
	public void dungeonMap() {
		byte[] c = new byte[128 * 128];
		// a 6x6 grid of 16-px rooms with 4-px gaps starting at (5, 5): start room at (0,0), blood at (2,1)
		room(c, 5, 5, 16, DungeonMap.C_START);
		room(c, 5 + 20, 5, 16, DungeonMap.C_NORMAL);
		room(c, 5 + 40, 5 + 20, 16, DungeonMap.C_BLOOD);
		c[(5 + 8) * 128 + 5 + 20 + 8] = (byte) DungeonMap.C_CHECK_WHITE;
		DungeonMap.Layout l = DungeonMap.parse(c);
		assertNotNull(l);
		assertEquals(16, l.roomSize());
		assertEquals(5, l.originX());
		assertEquals(DungeonMap.RoomType.START, l.at(0, 0).type());
		assertEquals(DungeonMap.Check.WHITE, l.at(1, 0).check());
		assertEquals(DungeonMap.RoomType.BLOOD, l.at(2, 1).type());
		assertNull(l.at(3, 3));
		assertNull(DungeonMap.parse(new byte[128 * 128]));
	}

	static void room(byte[] c, int x0, int y0, int size, int colour) {
		for (int y = y0; y < y0 + size; y++) for (int x = x0; x < x0 + size; x++) c[y * 128 + x] = (byte) colour;
	}

	@Test
	public void skyblockTime() {
		var t = io.github.donttemptfate.fatesofa.activity.SkyBlockTime.class;
		var d = io.github.donttemptfate.fatesofa.activity.SkyBlockTime.at(io.github.donttemptfate.fatesofa.activity.SkyBlockTime.EPOCH_MS);
		assertEquals(1, d.year());
		assertEquals(0, d.month());
		assertEquals(1, d.day());
		long y2 = io.github.donttemptfate.fatesofa.activity.SkyBlockTime.start(2, 7, 29);
		var d2 = io.github.donttemptfate.fatesofa.activity.SkyBlockTime.at(y2 + 1000);
		assertEquals(2, d2.year());
		assertEquals("Autumn 29th, Year 2", d2.label());
		long now = 1_760_720_000_000L; // 17 Oct 2026
		var ev = io.github.donttemptfate.fatesofa.activity.SkyBlockTime.upcoming(now);
		assertEquals(8, ev.size());
		for (var e : ev) {
			assertTrue(e.name(), e.endMs() > now);
			assertTrue(e.name(), e.startMs() < e.endMs());
		}
		var jacob = ev.stream().filter(e -> e.name().startsWith("Jacob")).findFirst().get();
		assertEquals(15, (int) ((jacob.startMs() / 60_000) % 60));
		assertTrue(t != null);
	}
}
