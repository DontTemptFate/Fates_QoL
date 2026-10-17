package io.github.donttemptfate.fatesofa.rooms;

/**
 * The dungeon's room grid: rooms sit in 31-block cells, one block of doorway between them, starting at
 * x/z −200. Positions inside a cell are stored relative to its centre block and turned to the room's own
 * ("canonical") facing, so a room learned facing north is recognised facing any way.
 */
public final class RoomGrid {
	private RoomGrid() {}

	public static final int CELL = 32, SIZE = 31, CELLS = 6;
	/** Grid start; only tests move it. */
	public static volatile int originX = -200, originZ = -200;

	public record Cell(int i, int j) {
		public int x0() {
			return originX + i * CELL;
		}

		public int z0() {
			return originZ + j * CELL;
		}

		/** The centre block. */
		public int cx() {
			return x0() + 15;
		}

		public int cz() {
			return z0() + 15;
		}
	}

	/** The cell at a world position, or null outside the grid or in a doorway. */
	public static Cell at(double x, double z) {
		int i = (int) Math.floor((x - originX) / CELL), j = (int) Math.floor((z - originZ) / CELL);
		if (i < 0 || j < 0 || i >= CELLS || j >= CELLS) return null;
		double ox = x - (originX + i * CELL), oz = z - (originZ + j * CELL);
		if (ox >= SIZE || oz >= SIZE) return null;
		return new Cell(i, j);
	}

	/** Turns (dx, dz) by k quarter turns: (1,0) → (0,1) → (−1,0) → (0,−1). */
	public static int[] rot(int dx, int dz, int k) {
		return switch (k & 3) {
			case 0 -> new int[] {dx, dz};
			case 1 -> new int[] {-dz, dx};
			case 2 -> new int[] {-dx, -dz};
			default -> new int[] {dz, -dx};
		};
	}

	public static double[] rot(double dx, double dz, int k) {
		return switch (k & 3) {
			case 0 -> new double[] {dx, dz};
			case 1 -> new double[] {-dz, dx};
			case 2 -> new double[] {-dx, -dz};
			default -> new double[] {dz, -dx};
		};
	}

	/**
	 * World point → room offset (x, z) from the centre of the cell's centre block, in the room's facing. A block's
	 * centre (x + 0.5) comes out as whole numbers.
	 */
	public static double[] toRoom(Cell c, int rotation, double x, double z) {
		return rot(x - (c.cx() + 0.5), z - (c.cz() + 0.5), 4 - (rotation & 3));
	}

	/** Room offset → world point (inverse of {@link #toRoom}). */
	public static double[] toWorld(Cell c, int rotation, double rx, double rz) {
		double[] w = rot(rx, rz, rotation);
		return new double[] {c.cx() + 0.5 + w[0], c.cz() + 0.5 + w[1]};
	}
}
