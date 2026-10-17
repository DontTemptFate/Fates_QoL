package io.github.donttemptfate.fatesofa.dungeon;

/**
 * Dungeon score estimate from what the player can see (scoreboard, tab list, chat), following the
 * formulas on the official SkyBlock wiki's "Dungeon Score" page (which itself marks Explore and Speed
 * as not fully confirmed). Always shown as an estimate; parts that are unknown make it "provisional".
 */
public final class DungeonScore {
	private DungeonScore() {}

	/** Inputs; -1 / null where unknown. */
	public record Input(
			String floor,          // "F7", "M3", "E"
			int clearedPercent,    // rooms cleared, from the scoreboard
			int secretsFound,
			int totalSecrets,      // -1 when not known yet
			int deaths,
			boolean spiritFirstDeath,
			int failedPuzzles,
			int crypts,
			boolean mimicKilled,
			boolean princeKilled,
			boolean paulEzpz,
			long elapsedMs,
			boolean bossKilled) {
	}

	public record Result(int skill, int explore, int speed, int bonus, int total, String grade, boolean provisional) {
	}

	public static Result estimate(Input in) {
		boolean provisional = in.totalSecrets() < 0 || in.floor() == null;
		double deathPenalty = Math.max(0, in.deaths() * 2 - (in.spiritFirstDeath() && in.deaths() > 0 ? 1 : 0));
		int skill = clamp((int) Math.floor(100 - deathPenalty - in.failedPuzzles() * 14), 0, 100);

		int roomPart = clamp((int) Math.floor(60 * Math.max(0, in.clearedPercent()) / 100.0), 0, 60);
		int secretPart = 0;
		if (in.totalSecrets() > 0) {
			double need = secretRequirement(in.floor()) * in.totalSecrets();
			secretPart = clamp((int) Math.floor(40 * in.secretsFound() / Math.max(1, need)), 0, 40);
		}
		int explore = roomPart + secretPart;

		int speed;
		if (in.bossKilled()) {
			speed = speedScore(adjustedSeconds(in.floor(), in.elapsedMs() / 1000));
		} else {
			// before the boss dies the wiki counts the share of rooms cleared; assume the run will finish in time
			speed = speedScore(adjustedSeconds(in.floor(), in.elapsedMs() / 1000));
			provisional = true;
		}

		int bonus = Math.min(5, Math.max(0, in.crypts()));
		if (in.mimicKilled() && floorNumber(in.floor()) >= 6) bonus += 2;
		if (in.princeKilled()) bonus += 1;
		if (in.paulEzpz()) bonus += 10;

		int total = skill + explore + speed + bonus;
		return new Result(skill, explore, speed, bonus, total, grade(total), provisional);
	}

	/** Share of a floor's secrets needed for the full 40 secret points. */
	public static double secretRequirement(String floor) {
		if (floor == null) return 1.0;
		if (floor.startsWith("M")) return 1.0;
		return switch (floorNumber(floor)) {
			case 0, 1 -> 0.3;
			case 2 -> 0.4;
			case 3 -> 0.5;
			case 4 -> 0.6;
			case 5 -> 0.7;
			case 6 -> 0.85;
			default -> 1.0;
		};
	}

	/** Seconds counted for speed after the floor's allowance (wiki table). */
	public static long adjustedSeconds(String floor, long seconds) {
		int n = floorNumber(floor);
		boolean master = floor != null && floor.startsWith("M");
		long minus;
		if (master) minus = n == 6 ? 120 : n == 7 ? 360 : 0;
		else minus = n == 4 || n == 6 ? 240 : n == 7 ? 360 : 120;
		return Math.max(0, seconds - minus);
	}

	public static int speedScore(long t) {
		if (t < 480) return 100;
		if (t < 600) return (int) Math.ceil(140 - t / 12.0);
		if (t < 840) return (int) Math.ceil(115 - t / 24.0);
		if (t < 1140) return (int) Math.ceil(108 - t / 30.0);
		if (t < 3940) return (int) Math.ceil(98.5 - t / 40.0);
		return 0;
	}

	public static String grade(int total) {
		if (total >= 300) return "S+";
		if (total >= 270) return "S";
		if (total >= 230) return "A";
		if (total >= 160) return "B";
		if (total >= 100) return "C";
		return "D";
	}

	public static int floorNumber(String floor) {
		if (floor == null || floor.length() < 2) return 0;
		try {
			return Integer.parseInt(floor.substring(1));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
