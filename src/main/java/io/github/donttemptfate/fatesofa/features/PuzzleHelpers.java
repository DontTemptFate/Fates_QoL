package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatescommon.text.Fmt;
import io.github.donttemptfate.fatescommon.text.LegacyText;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.core.Highlight;
import io.github.donttemptfate.fatesofa.dungeon.PuzzleSolvers;
import io.github.donttemptfate.fatesofa.game.Draw;
import io.github.donttemptfate.fatesofa.state.Island;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dungeon puzzle hints (HELPER: tolerated by the community, not covered by Hypixel's written rules, so off by
 * default): Creeper Beams pairs, an Ice Fill path and the Higher or Lower kill order. Lines and boxes only; you
 * do every click and every step.
 */
public final class PuzzleHelpers extends Feature {
	static final Pattern BLAZE = Pattern.compile("Blaze (?<hp>[\\d,.]+[kKmM]?)/[\\d,.]+[kKmM]?❤");
	static final int[] PAIR_COLOURS = {0xFF55FF55, 0xFF55FFFF, 0xFFFFFF55, 0xFFFF55FF};

	final Setting<Boolean> creeper = setting(Setting.bool("puzzles.creeper", "Creeper Beams pairs", true).group("Creeper Beams")
			.help("Lines between the sea lanterns whose beam goes through the creeper, one colour per pair."));
	final Setting<Boolean> ice = setting(Setting.bool("puzzles.ice", "Ice Fill path", true).group("Ice Fill")
			.help("A line over every ice tile from where you stand. You walk it yourself."));
	final Setting<Integer> iceColour = setting(Setting.color("puzzles.ice.colour", "Path colour", 0xFF55FFFF).rainbow().group("Ice Fill"));
	final Setting<Boolean> blaze = setting(Setting.bool("puzzles.blaze", "Higher or Lower order", true).group("Higher or Lower")
			.help("Numbers over the blazes in health order; the next one is boxed."));
	final Setting<String> blazeOrder = setting(Setting.choice("puzzles.blaze.order", "Order", "lowest", List.of("lowest", "highest")).group("Higher or Lower")
			.labels(java.util.Map.of("lowest", "Lowest health first", "highest", "Highest health first")));
	final Highlight look = new Highlight(this, "puzzles.look", 0xFF55FF55);

	private volatile List<double[][]> beams = List.of();
	private volatile List<Vec3> icePath = List.of();
	private volatile List<Entity> blazes = List.of();
	private int tick;

	public PuzzleHelpers() {
		super("puzzle_helpers", "Dungeon puzzle hints", "Creeper Beams pairs, an Ice Fill path and the Higher or Lower kill order, drawn in the world. "
				+ "Hints only: you do every click and step.", Category.DUNGEONS, false, Risk.HELPER);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			if (++tick % 10 != 0) return;
			if (mc.player == null || mc.level == null || !(Ofa.get().state.in(Island.DUNGEON) || io.github.donttemptfate.fatesofa.game.Dev.test())) {
				beams = List.of();
				icePath = List.of();
				blazes = List.of();
				return;
			}
			beams = on(creeper) ? findBeams(mc) : List.of();
			icePath = on(ice) ? findIcePath(mc) : List.of();
			blazes = on(blaze) ? findBlazes(mc) : List.of();
		}));
		LevelRenderEvents.BEFORE_GIZMOS.register(ctx -> guard(this::draw));
	}

	/** The creeper nearby and the sea lanterns around it: lantern pairs whose line goes through it. */
	List<double[][]> findBeams(Minecraft mc) {
		Creeper c = null;
		double best = 24 * 24;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof Creeper cr && e.distanceToSqr(mc.player) < best) {
				best = e.distanceToSqr(mc.player);
				c = cr;
			}
		}
		if (c == null) return List.of();
		List<double[]> lanterns = new ArrayList<>();
		BlockPos cp = c.blockPosition();
		for (int dx = -15; dx <= 15; dx++) {
			for (int dz = -15; dz <= 15; dz++) {
				for (int dy = -3; dy <= 12; dy++) {
					BlockPos p = cp.offset(dx, dy, dz);
					if (mc.level.getBlockState(p).is(Blocks.SEA_LANTERN)) lanterns.add(new double[] {p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5});
				}
			}
		}
		double[] centre = {c.getX(), c.getY() + c.getBbHeight() / 2, c.getZ()};
		List<double[][]> out = new ArrayList<>();
		for (int[] pr : PuzzleSolvers.creeperPairs(lanterns, centre, 1.2, 4)) out.add(new double[][] {lanterns.get(pr[0]), lanterns.get(pr[1])});
		return out;
	}

	/** The ice board under the player (ice tiles connected to where they stand) and a walk over every tile. */
	List<Vec3> findIcePath(Minecraft mc) {
		BlockPos under = mc.player.blockPosition().below();
		boolean onBoard = mc.level.getBlockState(under).is(Blocks.ICE) || mc.level.getBlockState(under).is(Blocks.PACKED_ICE);
		if (!onBoard) return List.of();
		int y = under.getY();
		Set<Long> cells = new HashSet<>();
		java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
		q.add(new int[] {under.getX(), under.getZ()});
		cells.add(PuzzleSolvers.key(under.getX(), under.getZ()));
		while (!q.isEmpty() && cells.size() <= 64) {
			int[] at = q.poll();
			for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int nx = at[0] + d[0], nz = at[1] + d[1];
				long k = PuzzleSolvers.key(nx, nz);
				if (cells.contains(k) || Math.abs(nx - under.getX()) > 10 || Math.abs(nz - under.getZ()) > 10) continue;
				if (!mc.level.getBlockState(new BlockPos(nx, y, nz)).is(Blocks.ICE)) continue; // walked tiles turn to packed ice
				cells.add(k);
				q.add(new int[] {nx, nz});
			}
		}
		if (cells.size() < 2) return List.of();
		List<int[]> path = PuzzleSolvers.iceFill(cells, under.getX(), under.getZ());
		if (path == null) return List.of();
		List<Vec3> out = new ArrayList<>();
		for (int[] p : path) out.add(new Vec3(p[0] + 0.5, y + 1.05, p[1] + 0.5));
		return out;
	}

	/** Higher or Lower blazes (their health tags), in kill order. */
	List<Entity> findBlazes(Minecraft mc) {
		List<Entity> tags = new ArrayList<>();
		List<Double> hp = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof ArmorStand) || !e.hasCustomName() || e.distanceToSqr(mc.player) > 40 * 40) continue;
			Matcher m = BLAZE.matcher(LegacyText.clean(e.getCustomName().getString()));
			if (!m.find()) continue;
			tags.add(e);
			hp.add(Fmt.parse(m.group("hp")));
		}
		if (tags.size() < 2) return List.of();
		List<Entity> out = new ArrayList<>();
		for (int i : PuzzleSolvers.blazeOrder(hp, get(blazeOrder).equals("highest"))) out.add(tags.get(i));
		return out;
	}

	void draw() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || (beams.isEmpty() && icePath.isEmpty() && blazes.isEmpty())) return;
		long now = System.currentTimeMillis();
		try (var c = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
			int i = 0;
			for (double[][] b : beams) {
				int col = PAIR_COLOURS[i++ % PAIR_COLOURS.length];
				for (double[] l : b) Draw.box(look, new AABB(l[0] - 0.5, l[1] - 0.5, l[2] - 0.5, l[0] + 0.5, l[1] + 0.5, l[2] + 0.5), col, 0);
				Draw.line(look, new Vec3(b[0][0], b[0][1], b[0][2]), new Vec3(b[1][0], b[1][1], b[1][2]), col);
			}
			int iceCol = io.github.donttemptfate.fatesofa.config.Values.color(get(iceColour), on(iceColour.rainbow), now);
			for (int k = 1; k < icePath.size(); k++) Draw.line(look, icePath.get(k - 1), icePath.get(k), iceCol);
			for (int k = 0; k < blazes.size(); k++) {
				Entity e = blazes.get(k);
				Vec3 at = e.position().add(0, 0.6, 0);
				int col = k == 0 ? look.color(now) : 0xFFFFFFFF;
				Draw.label(look, Integer.toString(k + 1), at, col);
				if (k == 0) Draw.box(look, new AABB(e.getX() - 0.5, e.getY() - 2.0, e.getZ() - 0.5, e.getX() + 0.5, e.getY(), e.getZ() + 0.5), col, 1);
			}
		}
	}

	/** For the in-game test. */
	public int beamCount() {
		return beams.size();
	}

	public int icePathLength() {
		return icePath.size();
	}

	public int blazeCount() {
		return blazes.size();
	}
}
