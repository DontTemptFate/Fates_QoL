package io.github.donttemptfate.fatesofa.dungeon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure solvers for dungeon puzzles (HELPER features): they only compute what to highlight; the player does
 * every click and every step.
 */
public final class PuzzleSolvers {
	private PuzzleSolvers() {}

	// ---- Creeper Beams: pairs of sea lanterns whose line passes through the creeper -----------------

	/**
	 * The lantern pairs (indexes into {@code lanterns}) whose straight line passes closest to the creeper, at most
	 * {@code max} pairs, each lantern used once. Points are {x, y, z} block centres.
	 */
	public static List<int[]> creeperPairs(List<double[]> lanterns, double[] creeper, double maxMiss, int max) {
		record Cand(int a, int b, double miss) {
		}
		List<Cand> c = new ArrayList<>();
		for (int i = 0; i < lanterns.size(); i++) {
			for (int j = i + 1; j < lanterns.size(); j++) {
				double[] a = lanterns.get(i), b = lanterns.get(j);
				if (dist(a, b) < 6) continue; // beams cross the room
				double miss = segmentDistance(a, b, creeper);
				if (miss <= maxMiss) c.add(new Cand(i, j, miss));
			}
		}
		c.sort(Comparator.comparingDouble(Cand::miss));
		Set<Integer> used = new HashSet<>();
		List<int[]> out = new ArrayList<>();
		for (Cand x : c) {
			if (out.size() >= max) break;
			if (used.contains(x.a()) || used.contains(x.b())) continue;
			used.add(x.a());
			used.add(x.b());
			out.add(new int[] {x.a(), x.b()});
		}
		return out;
	}

	static double dist(double[] a, double[] b) {
		double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** Distance from point p to the segment a–b. */
	public static double segmentDistance(double[] a, double[] b, double[] p) {
		double[] ab = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
		double[] ap = {p[0] - a[0], p[1] - a[1], p[2] - a[2]};
		double len2 = ab[0] * ab[0] + ab[1] * ab[1] + ab[2] * ab[2];
		double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, (ap[0] * ab[0] + ap[1] * ab[1] + ap[2] * ab[2]) / len2));
		double[] q = {a[0] + ab[0] * t, a[1] + ab[1] * t, a[2] + ab[2] * t};
		return dist(q, p);
	}

	// ---- Ice Fill: a path from the start over every ice tile exactly once ---------------------------

	/**
	 * A walk over every cell of {@code cells} (grid {x, z}) starting at {@code start}, moving to 4-neighbours and
	 * never repeating a cell; null when none exists (or the search runs out of steps). Small boards only (≤ 64).
	 */
	public static List<int[]> iceFill(Set<Long> cells, int sx, int sz) {
		if (!cells.contains(key(sx, sz)) || cells.size() > 64) return null;
		List<int[]> path = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		path.add(new int[] {sx, sz});
		seen.add(key(sx, sz));
		int[] budget = {200_000};
		return walk(cells, seen, path, budget) ? path : null;
	}

	static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	static boolean walk(Set<Long> cells, Set<Long> seen, List<int[]> path, int[] budget) {
		if (seen.size() == cells.size()) return true;
		if (--budget[0] < 0) return false;
		int[] at = path.get(path.size() - 1);
		List<int[]> next = new ArrayList<>();
		for (int[] d : DIRS) {
			int nx = at[0] + d[0], nz = at[1] + d[1];
			if (cells.contains(key(nx, nz)) && !seen.contains(key(nx, nz))) next.add(new int[] {nx, nz});
		}
		// fewest onward moves first (Warnsdorff): finds a full walk quickly on these boards
		next.sort(Comparator.comparingInt(n -> onward(cells, seen, n)));
		for (int[] n : next) {
			seen.add(key(n[0], n[1]));
			path.add(n);
			if (walk(cells, seen, path, budget)) return true;
			path.remove(path.size() - 1);
			seen.remove(key(n[0], n[1]));
		}
		return false;
	}

	static int onward(Set<Long> cells, Set<Long> seen, int[] n) {
		int c = 0;
		for (int[] d : DIRS) {
			long k = key(n[0] + d[0], n[1] + d[1]);
			if (cells.contains(k) && !seen.contains(k)) c++;
		}
		return c;
	}

	public static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	// ---- Higher or Lower (blazes): kill order by health ---------------------------------------------

	/** Indexes of {@code health} from lowest to highest (or highest to lowest when {@code highestFirst}). */
	public static List<Integer> blazeOrder(List<Double> health, boolean highestFirst) {
		List<Integer> idx = new ArrayList<>();
		for (int i = 0; i < health.size(); i++) idx.add(i);
		idx.sort(Comparator.comparingDouble(health::get));
		if (highestFirst) java.util.Collections.reverse(idx);
		return idx;
	}
}
