package io.github.donttemptfate.fatesofa;

import io.github.donttemptfate.fatesofa.dungeon.DungeonRun;
import io.github.donttemptfate.fatesofa.dungeon.TerminalHelper;
import io.github.donttemptfate.fatesofa.dungeon.ThreeWeirdos;
import io.github.donttemptfate.fatesofa.state.ScoreboardInfo;
import io.github.donttemptfate.fatesofa.state.TabInfo;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DungeonTest {
	@Test
	public void run() {
		DungeonRun r = new DungeonRun();
		r.scoreboard(ScoreboardInfo.parse("SKYBLOCK", List.of(" ⏣ The Catacombs (F7)", "Cleared: 82% (300)", "Time Elapsed: 6m 05s")), 400_000);
		r.tab(TabInfo.parse(List.of("Secrets Found: 30", "Secrets Found: 50.0%", "Crypts: 4", "Team Deaths: 1", "Puzzles: (3)",
				"Three Weirdos: [✔]", "Creeper Beams: [✖]")));
		assertEquals("F7", r.floor);
		assertEquals(365_000, r.elapsedMs);
		assertEquals(60, r.totalSecrets());
		assertEquals(1, r.failedPuzzles());
		assertEquals(4, r.crypts);
		r.chat("The BLOOD DOOR has been opened!", 200_000);
		r.chat("[BOSS] The Watcher: You have proven yourself. You may pass.", 260_000);
		r.chat("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!", 270_000);
		r.chat("Fate has obtained Wither Key!", 1);
		assertEquals("Maxor", r.bossName);
		assertEquals(List.of("Wither"), r.keys);
		var s = r.score(false);
		assertTrue(s.provisional());
		assertEquals(100 - 2 - 14, s.skill());
	}

	@Test
	public void terminals() {
		var slots = List.of(new TerminalHelper.SlotInfo(10, "", "minecraft:red_stained_glass_pane", 3, false),
				new TerminalHelper.SlotInfo(11, "", "minecraft:red_stained_glass_pane", 1, false),
				new TerminalHelper.SlotInfo(12, "", "minecraft:lime_stained_glass_pane", 2, false));
		assertEquals(List.of(11, 10), TerminalHelper.hint("Click in order!", slots));
		var items = List.of(new TerminalHelper.SlotInfo(1, "Apple", "minecraft:apple", 1, false),
				new TerminalHelper.SlotInfo(2, "Arrow", "minecraft:arrow", 1, true),
				new TerminalHelper.SlotInfo(3, "Bone", "minecraft:bone", 1, false));
		assertEquals(List.of(1), TerminalHelper.hint("What starts with: 'A'?", items));
		var wool = List.of(new TerminalHelper.SlotInfo(5, "Blue Wool", "minecraft:blue_wool", 1, false),
				new TerminalHelper.SlotInfo(6, "Red Wool", "minecraft:red_wool", 1, false));
		assertEquals(List.of(5), TerminalHelper.hint("Select all the BLUE items!", wool));
		assertEquals(List.of(), TerminalHelper.hint("Large Chest", wool));
	}

	@Test
	public void weirdos() {
		assertEquals("Baxter", ThreeWeirdos.answer("[NPC] Baxter: My chest has the reward and I'm telling the truth!"));
		assertNull(ThreeWeirdos.answer("[NPC] Hope: The reward is in my chest!"));
	}
}
