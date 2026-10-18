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
	final Setting<Boolean> map = setting(Setting.bool("dungeon.map", "Map", true));
	final Setting<Boolean> score = setting(Setting.bool("dungeon.score", "Score estimate", true));
	final Setting<Boolean> splits = setting(Setting.bool("dungeon.splits", "Run splits", true));
	final Setting<Boolean> keyAlert = setting(Setting.bool("dungeon.keyAlert", "Key pickup alert", true));
	final Setting<Boolean> scoreAlert = setting(Setting.bool("dungeon.scoreAlert", "Alert at 270 and 300 score", true));

	private DungeonRun run = new DungeonRun();
	private volatile DungeonMap.Layout layout;
	private int tick;
	private boolean said270, said300, mapWarned;

	public DungeonFeature() {
		super("dungeons", "Dungeons", "Clean dungeon map, score estimate (S / S+), run splits and key alerts — from what the game shows you.",
				Category.DUNGEONS, true, Risk.SAFE);
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		o.state.onIslandChange(i -> {
			if (i == Island.DUNGEON) {
				run = new DungeonRun();
				said270 = said300 = mapWarned = false;
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			if (!o.state.in(Island.DUNGEON) || ++tick % 10 != 0) return;
			long now = System.currentTimeMillis();
			run.scoreboard(o.state.scoreboard(), now);
			run.tab(o.state.tab());
			layout = readMap(mc);
			if (on(scoreAlert)) {
				DungeonScore.Result r = run.score(paulEzpz());
				if (!said270 && r.total() >= 270) {
					said270 = true;
					Alerts.title("270 SCORE", 0xFFAA00, "S reached");
				}
				if (!said300 && r.total() >= 300) {
					said300 = true;
					Alerts.title("300 SCORE", 0xFFAA00, "S+ reached");
				}
			}
		}));
		ChatBus.onChat(m -> guard(() -> {
			if (!o.state.in(Island.DUNGEON)) return;
			int before = run.keys.size();
			run.chat(m.plain(), m.atMs());
			if (on(keyAlert) && run.keys.size() > before) Alerts.title(run.keys.get(run.keys.size() - 1).toUpperCase() + " KEY", 0x55FF55, "picked up");
		}));
		Hud.register(new HudElement("dungeon_map", "Dungeon map", this, HudPos.at(Anchor.TOP_LEFT, 4, 4), 40, this::mapCard, this::sampleMap,
				() -> on(map) && o.state.in(Island.DUNGEON) && layout != null));
		Hud.register(new HudElement("dungeon_score", "Dungeon score", this, HudPos.at(Anchor.TOP_LEFT, 4, 110), 35, this::scoreCard, this::sampleScore,
				() -> (on(score) || on(splits)) && o.state.in(Island.DUNGEON)));
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
		Card c = new Card(0.80f, 0, 200);
		c.add(Card.Line.of(Card.seg("MAP" + (run.floor != null ? " · " + run.floor : ""), Ui.Role.LABEL, Ui.accent())));
		c.add(new MapEl(l, playerCell()));
		return c;
	}

	double[] playerCell() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return null;
		// dungeon rooms sit on a 32-block grid starting at x/z -200
		return new double[] {(mc.player.getX() + 200) / 32.0, (mc.player.getZ() + 200) / 32.0};
	}

	record MapEl(DungeonMap.Layout l, double[] me) implements Card.El {
		static final int CELL = 14, GAP = 3;

		public int prefWidth(int max) {
			return l.cols() * (CELL + GAP);
		}

		public int height(int w) {
			return l.rows() * (CELL + GAP);
		}

		public void draw(GuiGraphicsExtractor g, int x, int y, int w) {
			for (DungeonMap.Room r : l.rooms()) {
				int rx = x + r.gx() * (CELL + GAP), ry = y + r.gy() * (CELL + GAP);
				g.fill(rx, ry, rx + CELL, ry + CELL, colour(r.type()));
				if (r.check() == DungeonMap.Check.WHITE) g.fill(rx + 5, ry + 5, rx + CELL - 5, ry + CELL - 5, 0xFFFFFFFF);
				if (r.check() == DungeonMap.Check.GREEN) g.fill(rx + 5, ry + 5, rx + CELL - 5, ry + CELL - 5, 0xFF00CC00);
				if (r.check() == DungeonMap.Check.FAILED) g.fill(rx + 5, ry + 5, rx + CELL - 5, ry + CELL - 5, 0xFFCC0000);
			}
			if (me != null && me[0] >= 0 && me[1] >= 0 && me[0] < l.cols() && me[1] < l.rows()) {
				int px = x + (int) (Math.floor(me[0]) * (CELL + GAP) + (me[0] % 1) * CELL);
				int py = y + (int) (Math.floor(me[1]) * (CELL + GAP) + (me[1] % 1) * CELL);
				g.fill(px - 2, py - 2, px + 2, py + 2, 0xFF000000);
				g.fill(px - 1, py - 1, px + 1, py + 1, 0xFFFFFFFF);
			}
		}
	}

	Card scoreCard() {
		long now = System.currentTimeMillis();
		Card c = Trackers.card("DUNGEON", run.floor == null ? null : run.floor);
		if (on(score)) {
			DungeonScore.Result r = run.score(paulEzpz());
			int col = r.total() >= 300 ? Ui.GOLD : r.total() >= 270 ? Ui.GREEN : Ui.WHITE;
			c.add(new Card.Hero(List.of(Card.seg(Integer.toString(r.total()), Ui.Role.HERO_S, col)), Card.seg(r.grade(), Ui.Role.TAG, col),
					col == Ui.GOLD ? Ui.BOX_GOLD : Ui.BOX_GREEN, List.of(Card.seg(r.provisional() ? "ESTIMATE" : "SCORE", Ui.Role.LABEL, Ui.GRAY))));
			Card.Grid gr = new Card.Grid(4);
			gr.cell("SKILL", Card.seg(Integer.toString(r.skill()), Ui.Role.VALUE, Ui.WHITE));
			gr.cell("EXPLORE", Card.seg(Integer.toString(r.explore()), Ui.Role.VALUE, Ui.WHITE));
			gr.cell("SPEED", Card.seg(Integer.toString(r.speed()), Ui.Role.VALUE, Ui.WHITE));
			gr.cell("BONUS", Card.seg(Integer.toString(r.bonus()), Ui.Role.VALUE, Ui.WHITE));
			c.add(gr);
			Card.KeyValues kv = Trackers.rows();
			int total = run.totalSecrets();
			if (run.secretsFound >= 0) Trackers.row(kv, "Secrets", run.secretsFound + (total > 0 ? " / " + total : ""), Ui.AQUA);
			Trackers.row(kv, "Crypts", run.crypts + " / 5", run.crypts >= 5 ? Ui.GREEN : Ui.WHITE);
			if (run.deaths > 0) Trackers.row(kv, "Deaths", Integer.toString(run.deaths), Ui.RED);
			for (Map.Entry<String, String> p : run.puzzles.entrySet()) {
				String s = p.getValue();
				Trackers.row(kv, p.getKey(), s.equals("✔") ? "done" : s.equals("✖") ? "failed" : "open", s.equals("✔") ? Ui.GREEN : s.equals("✖") ? Ui.RED : Ui.YELLOW);
			}
			c.add(kv);
		}
		if (on(splits) && run.startedAt > 0) {
			Card.KeyValues kv = Trackers.rows();
			split(kv, "Blood open", run.bloodAt);
			split(kv, "Watcher done", run.watcherAt);
			split(kv, run.bossName == null ? "Boss" : run.bossName, run.bossAt);
			split(kv, "Run end", run.endAt);
			if (!kv.isEmpty()) c.add(kv);
			if (run.endAt < 0 && run.elapsedMs >= 0) c.add(Card.Line.of(Card.seg("Time " + Fmt.clock(run.elapsedMs), Ui.Role.SMALL, Ui.GRAY)));
		}
		return c;
	}

	void split(Card.KeyValues kv, String name, long at) {
		if (at > 0) Trackers.row(kv, name, Fmt.clock(at - run.startedAt), Ui.WHITE);
	}

	Card sampleScore() {
		Card c = Trackers.card("DUNGEON", "F7");
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
		Card c = new Card(0.80f, 0, 200);
		c.add(Card.Line.of(Card.seg("MAP · F7", Ui.Role.LABEL, Ui.accent())));
		c.add(new MapEl(l, new double[] {1.4, 0.6}));
		return c;
	}
}
