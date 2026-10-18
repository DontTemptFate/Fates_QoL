package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.dungeon.DungeonMap;
import io.github.donttemptfate.fatesofa.dungeon.DungeonRun;
import io.github.donttemptfate.fatesofa.dungeon.DungeonScore;
import io.github.donttemptfate.fatesofa.game.Alerts;
import io.github.donttemptfate.fatesofa.game.ChatBus;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;
import io.github.donttemptfate.fatesofa.state.Island;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;
import java.util.Map;

/**
 * Dungeons: a clean map (from the map item you carry), the score estimate with S / S+ in view, run splits
 * (blood open, watcher, boss, end) and key pickup alerts. Only what the game already shows you.
 */
public final class DungeonFeature extends Feature {
	static final String MAP = "Map card", SCORE = "Score card";
	final Setting<Boolean> map = setting(Setting.bool("dungeon.map", "Show the map", true).group(MAP));
	final io.github.donttemptfate.fatesofa.core.Display mapDisplay = new io.github.donttemptfate.fatesofa.core.Display(this, "dungeon.mapcard", MAP,
			0xFFFF55FF, null, null, false);
	final Setting<Integer> cell = setting(Setting.integer("dungeon.map.cell", "Room size", 14, 8, 24).group(MAP));
	final Setting<Integer> gap = setting(Setting.integer("dungeon.map.gap", "Gap between rooms", 3, 1, 6).group(MAP).advanced());
	final Setting<Boolean> me = setting(Setting.bool("dungeon.map.me", "Show where you are", true).group(MAP));
	final Setting<Integer> meColour = setting(Setting.color("dungeon.map.meColour", "Your marker", 0xFFFFFFFF).rainbow().group(MAP).advanced());
	final Setting<Boolean> ticks = setting(Setting.bool("dungeon.map.ticks", "Cleared / failed marks", true).group(MAP));
	final Setting<Boolean> unexplored = setting(Setting.bool("dungeon.map.unexplored", "Show unopened rooms", true).group(MAP).advanced());
	final java.util.Map<DungeonMap.RoomType, Setting<Integer>> roomColours = new java.util.EnumMap<>(DungeonMap.RoomType.class);

	final Setting<Boolean> score = setting(Setting.bool("dungeon.score", "Score estimate", true).group(SCORE));
	final Setting<Boolean> splits = setting(Setting.bool("dungeon.splits", "Run splits", true).group(SCORE));
	final io.github.donttemptfate.fatesofa.core.Display scoreDisplay = new io.github.donttemptfate.fatesofa.core.Display(this, "dungeon.scorecard", SCORE,
			0xFFFF55FF, io.github.donttemptfate.fatesofa.core.Display.lines("score", "Score and grade", "breakdown", "Skill / explore / speed / bonus",
					"secrets", "Secrets", "crypts", "Crypts", "deaths", "Deaths", "puzzles", "Puzzles", "splits", "Splits", "time", "Run time"),
			List.of("score", "breakdown", "secrets", "crypts", "deaths", "puzzles", "splits", "time"), false);

	final io.github.donttemptfate.fatesofa.core.Alert s270 = new io.github.donttemptfate.fatesofa.core.Alert(this, "dungeon.alert270", "270 score alert",
			"270 SCORE", "S reached", 0xFFFFAA00, "minecraft:entity.player.levelup|0.6|1.2");
	final io.github.donttemptfate.fatesofa.core.Alert s300 = new io.github.donttemptfate.fatesofa.core.Alert(this, "dungeon.alert300", "300 score alert",
			"300 SCORE", "S+ reached", 0xFFFFAA00, "minecraft:entity.player.levelup|0.8|1.5");
	final io.github.donttemptfate.fatesofa.core.Alert key = new io.github.donttemptfate.fatesofa.core.Alert(this, "dungeon.keyAlert", "Key pickup alert",
			"{key} KEY", "picked up", 0xFF55FF55, "minecraft:entity.experience_orb.pickup", "key");
	final io.github.donttemptfate.fatesofa.core.Alert crypts = new io.github.donttemptfate.fatesofa.core.Alert(this, "dungeon.cryptsAlert", "5 crypts alert",
			false, "5 CRYPTS", "all bonus crypts done", 0xFF55FF55, "minecraft:block.note_block.chime");
	final io.github.donttemptfate.fatesofa.core.Alert puzzleFail = new io.github.donttemptfate.fatesofa.core.Alert(this, "dungeon.puzzleFail", "Puzzle failed alert",
			true, "PUZZLE FAILED", "{puzzle}", 0xFFFF5555, "minecraft:entity.villager.no", "puzzle");
	private final java.util.Set<String> failedSeen = new java.util.HashSet<>();
	private boolean saidCrypts;

	private DungeonRun run = new DungeonRun();
	private volatile DungeonMap.Layout layout;
	private int tick;
	private boolean said270, said300, mapWarned;

	public DungeonFeature() {
		super("dungeons", "Dungeons", "Clean dungeon map, score estimate (S / S+), run splits and key alerts — from what the game shows you.",
				Category.DUNGEONS, true, Risk.SAFE);
		for (DungeonMap.RoomType t : DungeonMap.RoomType.values()) {
			if (t == DungeonMap.RoomType.UNKNOWN) continue;
			String n = t.name().charAt(0) + t.name().substring(1).toLowerCase(java.util.Locale.ROOT);
			roomColours.put(t, setting(Setting.color("dungeon.map.colour." + t.name().toLowerCase(java.util.Locale.ROOT), n + " rooms", colour(t)).group(MAP).advanced()));
		}
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		o.state.onIslandChange(i -> {
			if (i == Island.DUNGEON) {
				run = new DungeonRun();
				said270 = said300 = mapWarned = saidCrypts = false;
				failedSeen.clear();
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			if (!o.state.in(Island.DUNGEON) || ++tick % 10 != 0) return;
			long now = System.currentTimeMillis();
			run.scoreboard(o.state.scoreboard(), now);
			run.tab(o.state.tab());
			layout = readMap(mc);
			DungeonScore.Result r = run.score(paulEzpz());
			if (!said270 && r.total() >= 270) {
				said270 = true;
				s270.fire(Map.of(), Island.DUNGEON, now);
			}
			if (!said300 && r.total() >= 300) {
				said300 = true;
				s300.fire(Map.of(), Island.DUNGEON, now);
			}
			if (!saidCrypts && run.crypts >= 5) {
				saidCrypts = true;
				crypts.fire(Map.of(), Island.DUNGEON, now);
			}
			for (Map.Entry<String, String> p : run.puzzles.entrySet())
				if (p.getValue().equals("✖") && failedSeen.add(p.getKey())) puzzleFail.fire(Map.of("puzzle", p.getKey()), Island.DUNGEON, now);
		}));
		ChatBus.onChat(m -> guard(() -> {
			if (!o.state.in(Island.DUNGEON)) return;
			int before = run.keys.size();
			run.chat(m.plain(), m.atMs());
			if (run.keys.size() > before) key.fire(Map.of("key", run.keys.get(run.keys.size() - 1).toUpperCase()), Island.DUNGEON, m.atMs());
		}));
		Hud.register(new HudElement("dungeon_map", "Dungeon map", this, HudPos.at(Anchor.TOP_LEFT, 4, 4), 40, this::mapCard, this::sampleMap,
				() -> on(map) && o.state.in(Island.DUNGEON) && layout != null).display(mapDisplay));
		Hud.register(new HudElement("dungeon_score", "Dungeon score", this, HudPos.at(Anchor.TOP_LEFT, 4, 110), 35, this::scoreCard, this::sampleScore,
				() -> (on(score) || on(splits)) && o.state.in(Island.DUNGEON)).display(scoreDisplay));
	}

	/** Paul's EZPZ perk (+10 score) when he is mayor or minister, from Hypixel's election data. */
	boolean paulEzpz() {
		var m = Ofa.get().election.mayor(System.currentTimeMillis());
		return m != null && m.hasPerk("EZPZ");
	}

	DungeonMap.Layout readMap(Minecraft mc) {
		if (mc.player == null || mc.level == null) return null;
		ItemStack st = mc.player.getInventory().getItem(8);
		MapId id = st.get(DataComponents.MAP_ID);
		if (id == null) return null;
		MapItemSavedData data = mc.level.getMapData(id);
		if (data == null) return null;
		DungeonMap.Layout l = DungeonMap.parse(data.colors);
		if (l == null && !mapWarned) {
			// Hypixel may have changed the map's colours: say so once per run instead of silently showing nothing
			mapWarned = true;
			io.github.donttemptfate.fatesofa.FatesOfaClient.LOG.warn("Dungeon map item present but no rooms recognised (map colours changed?)");
		}
		return l;
	}

	static int colour(DungeonMap.RoomType t) {
		return switch (t) {
			case START -> 0xFF55FF55;
			case BLOOD -> 0xFFFF5555;
			case PUZZLE -> 0xFFAA55FF;
			case MINIBOSS -> 0xFFFFFF55;
			case FAIRY -> 0xFFFF77FF;
			case TRAP -> 0xFFFF9933;
			case UNEXPLORED -> 0xFF444444;
			case NORMAL -> 0xFF8A6A40;
			case UNKNOWN -> 0xFF666666;
		};
	}

	/** The map: one square per room, a tick for cleared rooms, a dot for you. */
	Card mapCard() {
		DungeonMap.Layout l = layout;
		if (l == null) return null;
		Card c = new Card(mapDisplay.alpha(), 0, 260);
		c.add(Card.Line.of(Card.seg("MAP" + (run.floor != null ? " · " + run.floor : ""), Ui.Role.LABEL, mapDisplay.accent(System.currentTimeMillis()))));
		c.add(new MapEl(l, on(me) ? playerCell() : null, look()));
		return c;
	}

	/** How the map is drawn, from the Map card settings. */
	record Look(int cell, int gap, boolean ticks, boolean unexplored, int me, java.util.Map<DungeonMap.RoomType, Integer> colours) {
	}

	Look look() {
		java.util.Map<DungeonMap.RoomType, Integer> cols = new java.util.EnumMap<>(DungeonMap.RoomType.class);
		for (DungeonMap.RoomType t : DungeonMap.RoomType.values()) {
			Setting<Integer> st = roomColours.get(t);
			cols.put(t, st == null || store() == null ? colour(t) : get(st) | 0xFF000000);
		}
		int meC = store() == null ? 0xFFFFFFFF : io.github.donttemptfate.fatesofa.config.Values.color(get(meColour), on(meColour.rainbow), System.currentTimeMillis());
		return store() == null ? new Look(14, 3, true, true, meC, cols) : new Look(get(cell), get(gap), on(ticks), on(unexplored), meC, cols);
	}

	double[] playerCell() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return null;
		// dungeon rooms sit on a 32-block grid starting at x/z -200
		return new double[] {(mc.player.getX() + 200) / 32.0, (mc.player.getZ() + 200) / 32.0};
	}

	record MapEl(DungeonMap.Layout l, double[] me, Look look) implements Card.El {
		public int prefWidth(int max) {
			return l.cols() * (look.cell() + look.gap());
		}

		public int height(int w) {
			return l.rows() * (look.cell() + look.gap());
		}

		public void draw(GuiGraphicsExtractor g, int x, int y, int w) {
			int cellPx = look.cell(), gapPx = look.gap(), in = Math.max(2, cellPx / 3);
			for (DungeonMap.Room r : l.rooms()) {
				if (r.type() == DungeonMap.RoomType.UNEXPLORED && !look.unexplored()) continue;
				int rx = x + r.gx() * (cellPx + gapPx), ry = y + r.gy() * (cellPx + gapPx);
				g.fill(rx, ry, rx + cellPx, ry + cellPx, look.colours().getOrDefault(r.type(), colour(r.type())));
				if (!look.ticks()) continue;
				if (r.check() == DungeonMap.Check.WHITE) g.fill(rx + in, ry + in, rx + cellPx - in, ry + cellPx - in, 0xFFFFFFFF);
				if (r.check() == DungeonMap.Check.GREEN) g.fill(rx + in, ry + in, rx + cellPx - in, ry + cellPx - in, 0xFF00CC00);
				if (r.check() == DungeonMap.Check.FAILED) g.fill(rx + in, ry + in, rx + cellPx - in, ry + cellPx - in, 0xFFCC0000);
			}
			if (me != null && me[0] >= 0 && me[1] >= 0 && me[0] < l.cols() && me[1] < l.rows()) {
				int px = x + (int) (Math.floor(me[0]) * (cellPx + gapPx) + (me[0] % 1) * cellPx);
				int py = y + (int) (Math.floor(me[1]) * (cellPx + gapPx) + (me[1] % 1) * cellPx);
				g.fill(px - 2, py - 2, px + 2, py + 2, 0xFF000000);
				g.fill(px - 1, py - 1, px + 1, py + 1, look.me());
			}
		}
	}

	Card scoreCard() {
		Card c = Trackers.card(scoreDisplay, "DUNGEON", run.floor == null ? null : run.floor);
		DungeonScore.Result r = run.score(paulEzpz());
		Card.KeyValues kv = Trackers.rows();
		Card.KeyValues sp = Trackers.rows();
		scoreDisplay.each(Map.of(
				"score", () -> {
					if (!on(score)) return;
					int col = r.total() >= 300 ? Ui.GOLD : r.total() >= 270 ? Ui.GREEN : Ui.WHITE;
					c.add(new Card.Hero(List.of(Card.seg(Integer.toString(r.total()), Ui.Role.HERO_S, col)), Card.seg(r.grade(), Ui.Role.TAG, col),
							col == Ui.GOLD ? Ui.BOX_GOLD : Ui.BOX_GREEN, List.of(Card.seg(r.provisional() ? "ESTIMATE" : "SCORE", Ui.Role.LABEL, Ui.GRAY))));
				},
				"breakdown", () -> {
					if (!on(score)) return;
					Card.Grid gr = new Card.Grid(4);
					gr.cell("SKILL", Card.seg(Integer.toString(r.skill()), Ui.Role.VALUE, Ui.WHITE));
					gr.cell("EXPLORE", Card.seg(Integer.toString(r.explore()), Ui.Role.VALUE, Ui.WHITE));
					gr.cell("SPEED", Card.seg(Integer.toString(r.speed()), Ui.Role.VALUE, Ui.WHITE));
					gr.cell("BONUS", Card.seg(Integer.toString(r.bonus()), Ui.Role.VALUE, Ui.WHITE));
					c.add(gr);
				},
				"secrets", () -> {
					int total = run.totalSecrets();
					if (on(score) && run.secretsFound >= 0) Trackers.row(kv, "Secrets", run.secretsFound + (total > 0 ? " / " + total : ""), Ui.AQUA);
				},
				"crypts", () -> {
					if (on(score)) Trackers.row(kv, "Crypts", run.crypts + " / 5", run.crypts >= 5 ? Ui.GREEN : Ui.WHITE);
				},
				"deaths", () -> {
					if (on(score) && run.deaths > 0) Trackers.row(kv, "Deaths", Integer.toString(run.deaths), Ui.RED);
				},
				"puzzles", () -> {
					if (!on(score)) return;
					for (Map.Entry<String, String> p : run.puzzles.entrySet()) {
						String st = p.getValue();
						Trackers.row(kv, p.getKey(), st.equals("✔") ? "done" : st.equals("✖") ? "failed" : "open", st.equals("✔") ? Ui.GREEN : st.equals("✖") ? Ui.RED : Ui.YELLOW);
					}
				},
				"splits", () -> {
					if (!on(splits) || run.startedAt <= 0) return;
					split(sp, "Blood open", run.bloodAt);
					split(sp, "Watcher done", run.watcherAt);
					split(sp, run.bossName == null ? "Boss" : run.bossName, run.bossAt);
					split(sp, "Run end", run.endAt);
				},
				"time", () -> {
					if (on(splits) && run.endAt < 0 && run.elapsedMs >= 0) Trackers.row(sp, "Time", Fmt.clock(run.elapsedMs), Ui.GRAY);
				}));
		if (!kv.isEmpty()) c.add(kv);
		if (!sp.isEmpty()) c.add(sp);
		return c;
	}

	void split(Card.KeyValues kv, String name, long at) {
		if (at > 0) Trackers.row(kv, name, Fmt.clock(at - run.startedAt), Ui.WHITE);
	}

	Card sampleScore() {
		Card c = Trackers.card(scoreDisplay, "DUNGEON", "F7");
		c.add(new Card.Hero(List.of(Card.seg("287", Ui.Role.HERO_S, Ui.GREEN)), Card.seg("S", Ui.Role.TAG, Ui.GREEN), Ui.BOX_GREEN,
				List.of(Card.seg("ESTIMATE", Ui.Role.LABEL, Ui.GRAY))));
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Secrets", "41 / 60", Ui.AQUA);
		Trackers.row(kv, "Crypts", "5 / 5", Ui.GREEN);
		Trackers.row(kv, "Blood open", "1:12", Ui.WHITE);
		c.add(kv);
		return c;
	}

	Card sampleMap() {
		byte[] colours = new byte[128 * 128];
		int[][] rooms = {{0, 0, DungeonMap.C_START}, {1, 0, DungeonMap.C_NORMAL}, {2, 0, DungeonMap.C_PUZZLE}, {1, 1, DungeonMap.C_NORMAL},
				{2, 1, DungeonMap.C_MINIBOSS}, {2, 2, DungeonMap.C_BLOOD}, {0, 1, DungeonMap.C_UNEXPLORED}};
		for (int[] r : rooms) {
			for (int yy = 0; yy < 16; yy++) for (int xx = 0; xx < 16; xx++) colours[(5 + r[1] * 20 + yy) * 128 + 5 + r[0] * 20 + xx] = (byte) r[2];
		}
		DungeonMap.Layout l = DungeonMap.parse(colours);
		Card c = new Card(mapDisplay.alpha(), 0, 260);
		c.add(Card.Line.of(Card.seg("MAP · F7", Ui.Role.LABEL, mapDisplay.accent(System.currentTimeMillis()))));
		c.add(new MapEl(l, new double[] {1.4, 0.6}, look()));
		return c;
	}
}
