package io.github.donttemptfate.fatesofa;

import io.github.donttemptfate.fatesofa.dungeon.PuzzleSolvers;
import io.github.donttemptfate.fatesofa.dungeon.RewardChest;
import io.github.donttemptfate.fatesofa.dungeon.RunHistory;
import org.junit.Test;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Batch 8b: run history, reward chests, puzzle solvers. */
public class DungeonExtrasTest {
	@Test
	public void runHistory() throws Exception {
		var f = Files.createTempDirectory("runs").resolve("runs.json");
		RunHistory h = new RunHistory(f);
		h.add(new RunHistory.Run("F7", 420_000, 301, "S+", 0, 55, Double.NaN, 1));
		h.add(new RunHistory.Run("F7", 390_000, 287, "S", 1, 48, 1.2e6, 2));
		h.add(new RunHistory.Run("M3", 300_000, 300, "S+", 0, 30, Double.NaN, 3));
		assertEquals(390_000, h.best("F7").timeMs());
		var again = new RunHistory(f);
		var st = again.stats();
		assertEquals(2, st.size());
		assertEquals("F7", st.get(0).floor());
		assertEquals(2, st.get(0).runs());
		assertEquals(301, st.get(0).bestScore());
		assertEquals(405_000, st.get(0).averageMs());
		assertEquals(1.2e6, again.all().get(1).profit(), 1e-6);
	}

	@Test
	public void rewardChestCost() {
		var c = RewardChest.cost(List.of("Open Reward Chest", "", "Cost", "250,000 Coins", "Dungeon Chest Key", "", "Click to open!"));
		assertEquals(250_000, c.coins(), 1e-6);
		assertEquals(1, c.items().size());
		assertEquals("Dungeon Chest Key", c.items().get(0)[0]);
		assertEquals(0, RewardChest.cost(List.of("Cost", "FREE")).coins(), 1e-6);
		assertNull(RewardChest.cost(List.of("Some lore")));
		assertArrayEquals(new String[] {"ESSENCE_WITHER", "3"}, RewardChest.essence("Wither Essence x3"));
		assertTrue(RewardChest.TITLE.matcher("Bedrock Chest").matches());
		assertTrue(RewardChest.TITLE.matcher("Paid Chest").matches());
	}

	@Test
	public void creeperBeams() {
		double[] creeper = {0, 1, 0};
		List<double[]> lanterns = List.of(new double[] {-8, 1, 0}, new double[] {8, 1, 0}, new double[] {0, 1, -8}, new double[] {0, 1, 8},
				new double[] {-8, 6, -8}, new double[] {7, 0, 8});
		var pairs = PuzzleSolvers.creeperPairs(lanterns, creeper, 1.0, 4);
		assertEquals(2, pairs.size());
		Set<String> got = new HashSet<>();
		for (int[] p : pairs) got.add(Math.min(p[0], p[1]) + "-" + Math.max(p[0], p[1]));
		assertEquals(Set.of("0-1", "2-3"), got);
	}

	@Test
	public void iceFillCoversEveryTile() {
		Set<Long> cells = new HashSet<>();
		for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) if (!(x == 2 && z == 2)) cells.add(PuzzleSolvers.key(x, z));
		var path = PuzzleSolvers.iceFill(cells, 0, 0);
		assertNotNull(path);
		assertEquals(cells.size(), path.size());
		for (int i = 1; i < path.size(); i++) assertEquals(1, Math.abs(path.get(i)[0] - path.get(i - 1)[0]) + Math.abs(path.get(i)[1] - path.get(i - 1)[1]));
		// a 3x3 board from a corner works; a parity-impossible start returns null
		Set<Long> small = new HashSet<>();
		for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) small.add(PuzzleSolvers.key(x, z));
		assertNotNull(PuzzleSolvers.iceFill(small, 0, 0));
		assertNull(PuzzleSolvers.iceFill(small, 1, 0));
	}

	@Test
	public void blazeOrder() {
		assertEquals(List.of(2, 0, 1), PuzzleSolvers.blazeOrder(List.of(500.0, 900.0, 100.0), false));
		assertEquals(List.of(1, 0, 2), PuzzleSolvers.blazeOrder(List.of(500.0, 900.0, 100.0), true));
	}
}
