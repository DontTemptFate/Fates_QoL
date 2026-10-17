package io.github.donttemptfate.fatesofa.rooms;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Recognises a dungeon room cell from its blocks, whichever way it faces.
 *
 * <p>The id comes from the cell's centre column (the same at any facing) plus the set of a few side columns;
 * the facing comes from the order those side columns appear in. Blocks that change during a run (chests,
 * levers, heads, buttons, TNT walls…) count as air, so an opened chest doesn't change the room.
 */
public final class RoomFingerprint {
	private RoomFingerprint() {}

	/** A block id ("minecraft:stone") at a position, or null when that part of the world isn't loaded. */
	public interface Sampler {
		String block(int x, int y, int z);
	}

	/**
	 * @param id        the room cell's id (16 hex characters)
	 * @param rotation  quarter turns from the room's own facing to the world
	 * @param symmetric the cell looks the same at two facings; its facing is a guess
	 */
	public record Result(String id, int rotation, boolean symmetric) {
	}

	static final int Y_MIN = 12, Y_MAX = 140;
	static final Set<String> CHANGES = Set.of("chest", "trapped_chest", "lever", "player_head", "player_wall_head", "skeleton_skull",
			"skeleton_wall_skull", "wither_skeleton_skull", "wither_skeleton_wall_skull", "redstone_torch", "redstone_wall_torch",
			"redstone_block", "tnt", "barrier", "cobweb", "fire", "air", "cave_air", "void_air", "light");
	/** Side columns as (along, across) steps from the centre; all inside the 31-block cell. */
	static final int[][] SIDES = {{7, 0}, {13, 0}, {7, 7}, {12, 5}, {10, -9}};

	static String norm(String id) {
		if (id == null) return null;
		String path = id.startsWith("minecraft:") ? id.substring(10) : id;
		if (CHANGES.contains(path) || path.endsWith("_button") || path.endsWith("_pressure_plate")) return "air";
		return path;
	}

	static final long FNV = 0xcbf29ce484222325L, PRIME = 0x100000001b3L;

	static long mix(long h, long v) {
		for (int i = 0; i < 8; i++) {
			h ^= (v >>> (i * 8)) & 0xFF;
			h *= PRIME;
		}
		return h;
	}

	static long mix(long h, String s) {
		for (int i = 0; i < s.length(); i++) {
			h ^= s.charAt(i);
			h *= PRIME;
		}
		return h * PRIME;
	}

	/** Hash of one column; {@code solid[0]} counts non-air blocks. Null when unloaded. */
	static Long column(Sampler s, int x, int z, int[] solid) {
		long h = FNV;
		for (int y = Y_MIN; y <= Y_MAX; y++) {
			String b = norm(s.block(x, y, z));
			if (b == null) return null;
			if (!b.equals("air") && solid != null) solid[0]++;
			h = mix(h, b);
		}
		return h;
	}

	/** Null when the cell isn't loaded yet or is empty (no room there). */
	public static Result of(Sampler s, RoomGrid.Cell c) {
		int cx = c.cx(), cz = c.cz();
		int[] solid = {0};
		Long core = column(s, cx, cz, solid);
		if (core == null || solid[0] == 0) return null;
		long[] sig = new long[4];
		for (int k = 0; k < 4; k++) {
			int[] u = RoomGrid.rot(1, 0, k), v = RoomGrid.rot(1, 0, k + 1);
			long h = FNV;
			for (int[] p : SIDES) {
				Long col = column(s, cx + p[0] * u[0] + p[1] * v[0], cz + p[0] * u[1] + p[1] * v[1], null);
				if (col == null) return null;
				h = mix(h, col);
			}
			sig[k] = h;
		}
		int best = 0;
		boolean symmetric = false;
		for (int k = 1; k < 4; k++) {
			int cmp = compare(sig, k, best);
			if (cmp < 0) {
				best = k;
				symmetric = false;
			} else if (cmp == 0) symmetric = true;
		}
		long[] sorted = sig.clone();
		Arrays.sort(sorted);
		long id = mix(FNV, core);
		for (long v : sorted) id = mix(id, v);
		return new Result(String.format(Locale.ROOT, "%016x", id), best, symmetric);
	}

	/** Compares the side sequence starting at facing a with the one starting at b. */
	static int compare(long[] sig, int a, int b) {
		for (int i = 0; i < 4; i++) {
			int c = Long.compareUnsigned(sig[(a + i) & 3], sig[(b + i) & 3]);
			if (c != 0) return c;
		}
		return 0;
	}
}
