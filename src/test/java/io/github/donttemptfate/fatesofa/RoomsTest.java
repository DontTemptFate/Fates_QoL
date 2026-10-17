package io.github.donttemptfate.fatesofa;

import com.google.gson.JsonParser;
import io.github.donttemptfate.fatesofa.record.RecordSanitizer;
import io.github.donttemptfate.fatesofa.rooms.RoomFingerprint;
import io.github.donttemptfate.fatesofa.rooms.RoomGrid;
import io.github.donttemptfate.fatesofa.rooms.RoomLearner;
import io.github.donttemptfate.fatesofa.rooms.RoomStore;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RoomsTest {
	static final String[] BLOCKS = {"minecraft:stone", "minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:mossy_stone_bricks",
			"minecraft:oak_planks", "minecraft:andesite", "minecraft:air", "minecraft:air", "minecraft:air"};

	/** A made-up room: a block for every (room x, y, room z), from a seed. */
	record FakeRoom(long seed) {
		String at(int rx, int y, int rz) {
			if (y < 60 || y > 100) return "minecraft:air";
			long h = seed ^ (rx * 73856093L) ^ (y * 19349663L) ^ (rz * 83492791L);
			h ^= h >>> 17;
			h *= 0x9E3779B97F4A7C15L;
			return BLOCKS[(int) Math.floorMod(h >>> 20, BLOCKS.length)];
		}
	}

	/** A world with rooms placed in cells at given facings. */
	static final class World implements RoomFingerprint.Sampler {
		final Map<RoomGrid.Cell, Object[]> placed = new HashMap<>();

		void place(RoomGrid.Cell c, FakeRoom r, int rotation) {
			placed.put(c, new Object[] {r, rotation});
		}

		public String block(int x, int y, int z) {
			RoomGrid.Cell c = RoomGrid.at(x + 0.5, z + 0.5);
			if (c == null) return "minecraft:air";
			Object[] p = placed.get(c);
			if (p == null) return "minecraft:air";
			int[] r = RoomGrid.rot(x - c.cx(), z - c.cz(), 4 - (int) p[1]);
			return ((FakeRoom) p[0]).at(r[0], y, r[1]);
		}
	}

	@Test
	public void sameRoomAnyFacingGetsSameIdAndPositions() {
		FakeRoom room = new FakeRoom(42);
		World w = new World();
		RoomGrid.Cell[] cells = {new RoomGrid.Cell(0, 0), new RoomGrid.Cell(1, 0), new RoomGrid.Cell(2, 3), new RoomGrid.Cell(5, 5)};
		for (int k = 0; k < 4; k++) w.place(cells[k], room, k);
		// a chest at room offset (4, 70, -6) in the fake room's own frame, placed at each facing
		double[] canon = null;
		String id = null;
		for (int k = 0; k < 4; k++) {
			RoomFingerprint.Result r = RoomFingerprint.of(w, cells[k]);
			assertNotNull(r);
			if (id == null) id = r.id();
			assertEquals("same room, facing " + k, id, r.id());
			int[] world = RoomGrid.rot(4, -6, k);
			double wx = cells[k].cx() + world[0] + 0.5, wz = cells[k].cz() + world[1] + 0.5;
			double[] got = RoomGrid.toRoom(cells[k], r.rotation(), wx, wz);
			if (canon == null) canon = got;
			assertEquals(canon[0], got[0], 1e-9);
			assertEquals(canon[1], got[1], 1e-9);
			double[] back = RoomGrid.toWorld(cells[k], r.rotation(), got[0], got[1]);
			assertEquals(wx, back[0], 1e-9);
			assertEquals(wz, back[1], 1e-9);
		}
		// a different room gets a different id; an empty cell none
		w.place(new RoomGrid.Cell(3, 3), new FakeRoom(7), 0);
		assertNotEquals(id, RoomFingerprint.of(w, new RoomGrid.Cell(3, 3)).id());
		assertNull(RoomFingerprint.of(w, new RoomGrid.Cell(4, 4)));
		// unloaded world: no answer yet
		assertNull(RoomFingerprint.of((x, y, z) -> null, cells[0]));
	}

	@Test
	public void openedChestsDontChangeTheRoom() {
		FakeRoom room = new FakeRoom(9);
		World w = new World();
		RoomGrid.Cell c = new RoomGrid.Cell(1, 1);
		w.place(c, room, 2);
		String before = RoomFingerprint.of(w, c).id();
		RoomFingerprint.Sampler withChest = (x, y, z) -> x == c.cx() && z == c.cz() && y == 75 ? "minecraft:chest" : w.block(x, y, z);
		RoomFingerprint.Sampler withAir = (x, y, z) -> x == c.cx() && z == c.cz() && y == 75 ? "minecraft:air" : w.block(x, y, z);
		assertEquals(RoomFingerprint.of(withChest, c).id(), RoomFingerprint.of(withAir, c).id());
		assertNotNull(before);
	}

	@Test
	public void gridCells() {
		assertEquals(new RoomGrid.Cell(0, 0), RoomGrid.at(-200, -200));
		assertEquals(new RoomGrid.Cell(0, 0), RoomGrid.at(-169.5, -170));
		assertNull(RoomGrid.at(-168.5, -180)); // doorway between cells
		assertEquals(new RoomGrid.Cell(1, 0), RoomGrid.at(-168, -180));
		assertNull(RoomGrid.at(0, 0));
		assertNull(RoomGrid.at(-201, -180));
	}

	@Test
	public void learnsSecretsAndRoutesFromTheCounter() {
		RoomStore store = new RoomStore();
		RoomGrid.Cell cell = new RoomGrid.Cell(2, 2);
		RoomLearner.Placement place = new RoomLearner.Placement(cell, "00000000000000aa", 1);
		RoomLearner l = new RoomLearner(store, (x, z) -> cell.equals(RoomGrid.at(x, z)) ? place : null, "me1");
		double cx = cell.cx() + 0.5, cz = cell.cz() + 0.5;
		long t = 1_000_000;
		l.player(cx, 70, cz - 14, t);
		assertTrue(l.counter(0, 2, cx, 70, cz - 14, t).isEmpty()); // entering: baseline, route starts
		assertTrue(l.recordingRoute());
		for (int i = 0; i < 10; i++) l.player(cx, 70, cz - 14 + i * 2, t += 500);
		l.candidate(RoomStore.Kind.CHEST, cx + 3.5, 71.5, cz + 2.5, t);
		List<RoomLearner.Learned> got = l.counter(1, 2, cx, 70, cz + 4, t += 200);
		assertEquals(1, got.size());
		assertEquals(RoomStore.Kind.CHEST, got.get(0).kind());
		assertTrue(got.get(0).isNew());
		assertTrue(l.found(cx + 3.5, 71.5, cz + 2.5));
		// the count went up with nothing to point at: the player's spot, kind OTHER
		got = l.counter(2, 2, cx - 5, 70, cz + 5, t += 4000);
		assertEquals(RoomStore.Kind.OTHER, got.get(0).kind());
		assertFalse(l.recordingRoute()); // room done: route kept
		RoomStore.Room r = store.room("00000000000000aa");
		assertEquals(2, r.secrets.size());
		assertNotNull(r.route);
		assertTrue(r.route.points().size() >= 5);
		// the chest's room offset turns back into the same world spot
		RoomStore.Secret chest = r.secrets.get(0);
		double[] w = RoomGrid.toWorld(cell, 1, chest.x, chest.z);
		assertEquals(cx + 3.5, w[0], 1e-9);
		assertEquals(cz + 2.5, w[1], 1e-9);

		// next run: the same chest again is not new; a slower clear doesn't replace the route
		l.reset();
		long ms = r.route.ms();
		l.player(cx, 70, cz - 14, t += 60_000);
		l.counter(0, 2, cx, 70, cz - 14, t);
		l.candidate(RoomStore.Kind.CHEST, cx + 3.5, 71.5, cz + 2.5, t += 30_000);
		assertFalse(l.counter(1, 2, cx, 70, cz, t).get(0).isNew());
		l.player(cx + 3, 70, cz, t += 100);
		l.candidate(RoomStore.Kind.BAT, cx - 5.2, 74, cz + 5.1, t += 100);
		l.counter(2, 2, cx, 70, cz, t);
		assertEquals(2, store.room("00000000000000aa").secrets.size());
		assertEquals(RoomStore.Kind.BAT, store.room("00000000000000aa").secrets.get(1).kind); // a precise find replaced the guess
		assertEquals(ms, store.room("00000000000000aa").route.ms());
	}

	@Test
	public void walkingIntoAnotherRoomIsntASecret() {
		RoomStore store = new RoomStore();
		RoomGrid.Cell a = new RoomGrid.Cell(0, 0), b = new RoomGrid.Cell(1, 0);
		RoomLearner l = new RoomLearner(store, (x, z) -> {
			RoomGrid.Cell c = RoomGrid.at(x, z);
			return c == null ? null : new RoomLearner.Placement(c, c.equals(a) ? "000000000000000a" : "000000000000000b", 0);
		}, "me");
		long t = 5_000_000;
		l.player(a.cx(), 70, a.cz(), t);
		l.counter(0, 5, a.cx(), 70, a.cz(), t);
		l.player(b.cx(), 70, b.cz(), t += 10_000); // walked into room B, which shows 1/5 (a teammate found one)
		assertTrue(l.counter(1, 5, b.cx(), 70, b.cz(), t += 300).isEmpty());
		assertEquals(0, store.secretCount());
	}

	@Test
	public void shareFilesRoundTripAndOnlyCarryYourOwnFinds() {
		RoomStore store = new RoomStore();
		store.addSecret("00000000000000aa", 3, 70, -4, RoomStore.Kind.CHEST, "me");
		store.addSecret("00000000000000aa", 3.4, 70, -4.2, RoomStore.Kind.CHEST, "friend"); // same secret
		store.addSecret("00000000000000aa", -9, 72, 5, RoomStore.Kind.ITEM, "friend");
		store.offerRoute("00000000000000aa", new RoomStore.Route(20_000, List.of(new double[] {0, 70, -15}, new double[] {3, 70, -4}), "me"));
		assertEquals(2, store.secretCount());
		assertEquals(2, store.room("00000000000000aa").secrets.get(0).seen());

		RoomStore mine = store.own("me");
		assertEquals(1, mine.secretCount());
		assertEquals(1, mine.routeCount());

		RoomStore back = RoomStore.fromJson(JsonParser.parseString(store.toJson("me").toString()));
		assertEquals(2, back.secretCount());
		assertEquals(2, back.room("00000000000000aa").secrets.get(0).seen());
		assertEquals(20_000, back.room("00000000000000aa").route.ms());

		// merging a friend's file adds only what's new
		RoomStore other = new RoomStore();
		other.addSecret("00000000000000aa", 3, 70, -4, RoomStore.Kind.CHEST, "third");
		other.addSecret("00000000000000bb", 1, 80, 1, RoomStore.Kind.BAT, "third");
		assertEquals(1, store.merge(other));
		assertEquals(3, store.room("00000000000000aa").secrets.get(0).seen());

		// the shared pack carries counts instead of ids
		RoomStore pack = RoomStore.fromJson(JsonParser.parseString(
				"{\"type\":\"ofa-rooms\",\"v\":1,\"rooms\":{\"00000000000000aa\":{\"s\":[[3,70,-4,\"CHEST\",5]]}}}"));
		assertEquals(5, pack.room("00000000000000aa").secrets.get(0).seen());
	}

	@Test
	public void badShareFilesAreRejectedOrTrimmed() {
		assertNull(RoomStore.fromJson(JsonParser.parseString("{\"type\":\"other\"}")));
		assertNull(RoomStore.fromJson(JsonParser.parseString("[1,2]")));
		RoomStore st = RoomStore.fromJson(JsonParser.parseString("{\"type\":\"ofa-rooms\",\"rooms\":{"
				+ "\"NOT-AN-ID\":{\"s\":[[1,70,1,\"CHEST\",[\"a\"]]]},"
				+ "\"00000000000000aa\":{\"s\":[[999,70,1,\"CHEST\",[\"a\"]],[1,70,1,\"WEIRD\",[\"a\"]],[1,\"x\",1,\"CHEST\"],[2,70,2,\"BAT\",[\"bad name!\"]]],"
				+ "\"r\":{\"ms\":-5,\"p\":[[0,70,0],[1,70,1]]}}}}"));
		assertNotNull(st);
		assertEquals(1, st.roomCount());
		assertEquals(1, st.secretCount()); // only the bat, with no valid contributor
		assertEquals(0, st.routeCount());
		// a huge room list is capped
		Random rnd = new Random(1);
		RoomStore big = new RoomStore();
		for (int i = 0; i < RoomStore.MAX_ROOMS + 50; i++) big.addSecret(String.format("%016x", rnd.nextLong()), 1, 70, 1, RoomStore.Kind.CHEST, "a");
		assertEquals(RoomStore.MAX_ROOMS, big.roomCount());
	}

	@Test
	public void recorderLeavesOutWhatPlayersTypeAndHidesNames() {
		RecordSanitizer s = new RecordSanitizer("DontTemptFate", List.of("Steve_99", "Alex", "!fake"));
		assertTrue(s.typedByPlayer("Party > [MVP+] Steve_99: go mines"));
		assertTrue(s.typedByPlayer("Guild > Alex [Officer]: hi"));
		assertTrue(s.typedByPlayer("From [VIP] Someone: you there?"));
		assertTrue(s.typedByPlayer("[287] ♲ [MVP+] RandomGuy: selling stuff"));
		assertTrue(s.typedByPlayer("[MVP++] Notch: hello"));
		assertTrue(s.typedByPlayer("someone: hey"));
		assertTrue(s.typedByPlayer("Steve_99 ♲: hi"));
		assertFalse(s.typedByPlayer("[NPC] Jacob: Your contest starts soon!"));
		assertFalse(s.typedByPlayer("[BOSS] Maxor: WELL WELL WELL"));
		assertFalse(s.typedByPlayer("RARE DROP! Revenant Viscera (+80% ✯ Magic Find)"));
		assertFalse(s.typedByPlayer("BONUS: Double drops!"));
		assertFalse(s.typedByPlayer("Steve_99 has obtained Wither Key!"));

		assertEquals("{player} has obtained Wither Key!", s.clean("Steve_99 has obtained Wither Key!"));
		assertEquals("§b[MVP§c+§b] {me}§f joined", s.clean("§b[MVP§c+§b] DontTemptFate§f joined"));
		assertEquals("§a{player} §7and Alexander", s.clean("§aAlex §7and Alexander"));
		// names OFA never saw nearby, after a rank or a SkyBlock level
		assertEquals("Seller: §b[MVP§c+§b] {player}", s.clean("Seller: §b[MVP§c+§b] Stranger1"));
		assertEquals(" [436] ♲ {player} (Mage 50)", s.clean(" [436] ♲ Someone_x (Mage 50)"));
		assertEquals("§8[§7300§8] §b{player}§f ok", s.clean("§8[§7300§8] §bWho_Else§f ok"));
		assertEquals("Purse: 1,234 · [NPC] Jacob", s.clean("Purse: 1,234 · [NPC] Jacob"));
	}
}
