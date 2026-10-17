package io.github.donttemptfate.fatesofa.dungeon;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the dungeon map item the player carries (128×128 map colours) into a grid of rooms: type,
 * explored state and checkmark. Only what the map already shows; nothing hidden is revealed.
 *
 * <p>Colour ids are Minecraft map colour bytes as Hypixel draws them; they are kept in one place so a
 * change is a one-line fix (and checked by tests).
 */
public final class DungeonMap {
	public enum RoomType { NORMAL, START, BLOOD, PUZZLE, MINIBOSS, FAIRY, TRAP, UNEXPLORED, UNKNOWN }

	public enum Check { NONE, WHITE, GREEN, FAILED }

	public record Room(int gx, int gy, RoomType type, Check check) {
	}

	public record Layout(int originX, int originY, int roomSize, int gap, int cols, int rows, List<Room> rooms) {
		public Room at(int gx, int gy) {
			for (Room r : rooms) if (r.gx() == gx && r.gy() == gy) return r;
			return null;
		}
	}

	// Map colour bytes (base colour × 4 + shade) used by Hypixel's dungeon map.
	public static final int C_NORMAL = 63;
	public static final int C_START = 30;
	public static final int C_BLOOD = 18;
	public static final int C_PUZZLE = 66;
	public static final int C_MINIBOSS = 74;
	public static final int C_FAIRY = 82;
	public static final int C_TRAP = 62;
	public static final int C_UNEXPLORED = 85;
	public static final int C_CHECK_WHITE = 34;
	public static final int C_CHECK_GREEN = 30;
	public static final int C_CHECK_FAILED = 18;

	static RoomType type(int c) {
		return switch (c) {
			case C_NORMAL -> RoomType.NORMAL;
			case C_START -> RoomType.START;
			case C_BLOOD -> RoomType.BLOOD;
			case C_PUZZLE -> RoomType.PUZZLE;
			case C_MINIBOSS -> RoomType.MINIBOSS;
			case C_FAIRY -> RoomType.FAIRY;
			case C_TRAP -> RoomType.TRAP;
			case C_UNEXPLORED -> RoomType.UNEXPLORED;
			default -> RoomType.UNKNOWN;
		};
	}

	static boolean isRoomColour(int c) {
		return type(c) != RoomType.UNKNOWN;
	}

	/** Parses the map; null when no room grid can be found (not a dungeon map, or not drawn yet). */
	public static Layout parse(byte[] colours) {
		if (colours == null || colours.length != 128 * 128) return null;
		// 1. the start room: its top-left corner and its width give room size and grid origin
		int sx = -1, sy = -1;
		outer:
		for (int y = 0; y < 128; y++) {
			for (int x = 0; x < 128; x++) {
				if ((colours[y * 128 + x] & 0xFF) == C_START) {
					sx = x;
					sy = y;
					break outer;
				}
			}
		}
		if (sx < 0) return null;
		int size = 0;
		while (sx + size < 128 && (colours[sy * 128 + sx + size] & 0xFF) == C_START) size++;
		if (size < 10 || size > 24) return null;
		int gap = 4;
		int step = size + gap;
		int ox = sx % step;
		int oy = sy % step;
		int cols = Math.min(6, (128 - ox + gap) / step);
		int rows = Math.min(6, (128 - oy + gap) / step);
		List<Room> rooms = new ArrayList<>();
		for (int gy = 0; gy < rows; gy++) {
			for (int gx = 0; gx < cols; gx++) {
				int x0 = ox + gx * step, y0 = oy + gy * step;
				int corner = colours[(y0 + 1) * 128 + x0 + 1] & 0xFF;
				if (!isRoomColour(corner)) continue;
				int centre = colours[(y0 + size / 2) * 128 + x0 + size / 2] & 0xFF;
				Check check = Check.NONE;
				if (centre != corner) {
					if (centre == C_CHECK_WHITE) check = Check.WHITE;
					else if (centre == C_CHECK_GREEN && corner != C_START) check = Check.GREEN;
					else if (centre == C_CHECK_FAILED && corner != C_BLOOD) check = Check.FAILED;
				}
				rooms.add(new Room(gx, gy, type(corner), check));
			}
		}
		return new Layout(ox, oy, size, gap, cols, rows, List.copyOf(rooms));
	}
}
