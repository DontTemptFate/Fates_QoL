package io.github.donttemptfate.fatesofa.features;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import io.github.donttemptfate.fatesofa.FatesOfaClient;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.data.Http;
import io.github.donttemptfate.fatesofa.game.ChatBus;
import io.github.donttemptfate.fatesofa.game.Game;
import io.github.donttemptfate.fatesofa.rooms.RoomFingerprint;
import io.github.donttemptfate.fatesofa.rooms.RoomGrid;
import io.github.donttemptfate.fatesofa.rooms.RoomLearner;
import io.github.donttemptfate.fatesofa.rooms.RoomStore;
import io.github.donttemptfate.fatesofa.state.Island;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dungeon secret waypoints and routes that OFA learns while you play (no other mod's data). Each room is
 * recognised from its blocks; when the action bar's room secret count goes up, the find is saved for that
 * room. Your fastest full clear of a room becomes its route. Optionally adds the shared room pack that
 * players build together on GitHub, and share files (/ofa rooms export / import).
 */
public final class SecretRoutes extends Feature {
	public static SecretRoutes INSTANCE;

	final Setting<Boolean> waypoints = setting(Setting.bool("secrets.waypoints", "Show secrets", true)
			.help("A box on each known secret of the rooms around you; collected ones disappear."));
	final Setting<Boolean> route = setting(Setting.bool("secrets.route", "Show room route", true)
			.help("A line along the fastest clear of the room you're in."));
	final Setting<Boolean> nextOnly = setting(Setting.bool("secrets.next", "Only the nearest secret", false)
			.help("Highlight just the closest uncollected secret in your room instead of all of them."));
	final Setting<Boolean> learn = setting(Setting.bool("secrets.learn", "Learn while I play", true)
			.help("Save secrets and routes from your own runs. Saved only on this computer until you export them."));
	final Setting<Boolean> shared = setting(Setting.bool("secrets.shared", "Use the shared room pack", true)
			.help("Secrets other players found, downloaded from the OFA GitHub repository (no account, nothing uploaded).")
			.keywords("online community"));
	final Setting<Boolean> note = setting(Setting.bool("secrets.note", "Tell me when a new secret is learned", false));

	public static final String PACK_URL = "https://raw.githubusercontent.com/DontTemptFate/Fates_QoL/main/data/rooms/pack.json";
	static final Pattern COUNT = Pattern.compile("(?<found>\\d+)/(?<total>\\d+) Secrets");
	static final long PACK_EVERY_MS = 60 * 60_000L;

	final RoomStore local = new RoomStore();
	volatile RoomStore pack = new RoomStore();
	private RoomLearner learner;
	private String contributor;
	private final Map<RoomGrid.Cell, RoomLearner.Placement> placements = new HashMap<>();
	private final Set<RoomGrid.Cell> empty = new HashSet<>();
	private final Map<Integer, double[]> tracked = new HashMap<>();
	private int tick;
	private long lastSave, lastPackCheck;
	private String packEtag;

	public SecretRoutes() {
		super("secret_waypoints", "Secret waypoints & routes",
				"Dungeon secrets and room routes that OFA learns from your own runs (plus, optionally, the pack players share).",
				Category.DUNGEONS, true, Risk.HELPER);
		INSTANCE = this;
	}

	@Override
	public Set<String> keywords() {
		return Set.of("secret", "route", "waypoint", "dungeon", "rooms");
	}

	Path dir() {
		return Ofa.get().dir.resolve("rooms");
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		var raw = o.settings.raw("rooms.contributor");
		contributor = raw != null && raw.isJsonPrimitive() ? raw.getAsString() : null;
		if (contributor == null || !contributor.matches("[a-f0-9]{12}")) {
			contributor = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
			o.settings.putRaw("rooms.contributor", new com.google.gson.JsonPrimitive(contributor));
		}
		learner = new RoomLearner(local, this::placementAt, contributor);
		o.io.execute(() -> {
			RoomStore l = read(dir().resolve("learned.json"));
			if (l != null) local.merge(l);
			local.saved();
			RoomStore p = read(dir().resolve("pack.json"));
			if (p != null) pack = p;
		});
		o.state.onIslandChange(i -> newRun());
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> tick(mc)));
		ChatBus.onActionBar(m -> guard(() -> {
			if (!inDungeon() || !on(learn)) return;
			Matcher c = COUNT.matcher(m.plain());
			Minecraft mc = Minecraft.getInstance();
			if (!c.find() || mc.player == null) return;
			var got = learner.counter(Integer.parseInt(c.group("found")), Integer.parseInt(c.group("total")),
					mc.player.getX(), mc.player.getY(), mc.player.getZ(), m.atMs());
			for (var l : got) if (l.isNew() && on(note)) Game.info("Learned a new secret (" + l.kind().label.toLowerCase() + ").");
		}));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			guard(() -> {
				if (!(level instanceof ClientLevel) || !inDungeon() || !on(learn)) return;
				BlockPos p = hit.getBlockPos();
				String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath();
				RoomStore.Kind k = switch (id) {
					case "chest", "trapped_chest" -> RoomStore.Kind.CHEST;
					case "player_head", "player_wall_head" -> RoomStore.Kind.ESSENCE;
					case "lever" -> RoomStore.Kind.LEVER;
					default -> null;
				};
				if (k != null) learner.candidate(k, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, System.currentTimeMillis());
			});
			return InteractionResult.PASS;
		});
		LevelRenderEvents.BEFORE_GIZMOS.register(ctx -> guard(this::draw));
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> saveNow());
	}

	boolean inDungeon() {
		return Ofa.get().state.in(Island.DUNGEON) || Boolean.getBoolean("fatesofa.test");
	}

	void newRun() {
		placements.clear();
		empty.clear();
		tracked.clear();
		if (learner != null) learner.reset();
		saveSoon();
	}

	/** Called by the world-change hook so a new dungeon instance never reuses the last one's cells. */
	public void worldChanged() {
		newRun();
	}

	RoomLearner.Placement placementAt(double x, double z) {
		RoomGrid.Cell c = RoomGrid.at(x, z);
		return c == null ? null : placements.get(c);
	}

	void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) return;
		long now = System.currentTimeMillis();
		if (inDungeon()) {
			tick++;
			if (tick % 5 == 0) recognise(mc);
			if (on(learn)) {
				learner.player(mc.player.getX(), mc.player.getY(), mc.player.getZ(), now);
				if (tick % 2 == 0) trackEntities(mc, now);
			}
			if (on(shared) && now - lastPackCheck > PACK_EVERY_MS) fetchPack(now);
		}
		if (now - lastSave > 30_000) saveSoon();
	}

	/** Recognises up to two not-yet-known cells near you per call (a cell is read once per run). */
	void recognise(Minecraft mc) {
		RoomGrid.Cell me = RoomGrid.at(mc.player.getX(), mc.player.getZ());
		int pi = me != null ? me.i() : (int) Math.floor((mc.player.getX() - RoomGrid.originX) / RoomGrid.CELL);
		int pj = me != null ? me.j() : (int) Math.floor((mc.player.getZ() - RoomGrid.originZ) / RoomGrid.CELL);
		int done = 0;
		ClientLevel level = mc.level;
		BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
		RoomFingerprint.Sampler s = (x, y, z) -> {
			if (!level.hasChunk(x >> 4, z >> 4)) return null;
			return BuiltInRegistries.BLOCK.getKey(level.getBlockState(mp.set(x, y, z)).getBlock()).toString();
		};
		for (int d = 0; d <= 1 && done < 2; d++) {
			for (int di = -d; di <= d && done < 2; di++) {
				for (int dj = -d; dj <= d && done < 2; dj++) {
					int i = pi + di, j = pj + dj;
					if (i < 0 || j < 0 || i >= RoomGrid.CELLS || j >= RoomGrid.CELLS) continue;
					RoomGrid.Cell c = new RoomGrid.Cell(i, j);
					if (placements.containsKey(c) || empty.contains(c)) continue;
					RoomFingerprint.Result r = RoomFingerprint.of(s, c);
					done++;
					if (r == null) {
						if (level.hasChunk(c.cx() >> 4, c.cz() >> 4)) empty.add(c);
						continue;
					}
					placements.put(c, new RoomLearner.Placement(c, r.id(), r.rotation()));
				}
			}
		}
	}

	/** Items next to you and bats near you: when one vanishes it may have been a secret. */
	void trackEntities(Minecraft mc, long now) {
		Map<Integer, double[]> seen = new HashMap<>();
		Vec3 me = mc.player.position();
		for (Entity e : mc.level.entitiesForRendering()) {
			boolean item = e instanceof ItemEntity, bat = e instanceof Bat;
			if (!item && !bat) continue;
			double d2 = e.distanceToSqr(me);
			if (item ? d2 > 25 : d2 > 400) continue;
			seen.put(e.getId(), new double[] {e.getX(), e.getY(), e.getZ(), item ? 1 : 0, d2});
		}
		for (Map.Entry<Integer, double[]> was : tracked.entrySet()) {
			if (seen.containsKey(was.getKey())) continue;
			double[] p = was.getValue();
			if (p[3] == 1 && p[4] > 9) continue; // an item that vanished out of reach wasn't picked up by you
			learner.candidate(p[3] == 1 ? RoomStore.Kind.ITEM : RoomStore.Kind.BAT, p[0], p[1], p[2], now);
		}
		tracked.clear();
		tracked.putAll(seen);
	}

	/** Known secrets of a cell in world positions (yours first, then the pack's that you don't already have). */
	List<Object[]> secretsIn(RoomLearner.Placement p) {
		List<Object[]> out = new ArrayList<>();
		List<List<RoomStore.Secret>> sources = new ArrayList<>();
		sources.add(local.secrets(p.id()));
		if (on(shared)) sources.add(pack.secrets(p.id()));
		List<double[]> taken = new ArrayList<>();
		for (List<RoomStore.Secret> list : sources) {
			for (RoomStore.Secret s : list) {
				boolean dup = false;
				for (double[] t : taken) {
					double dx = t[0] - s.x, dy = t[1] - s.y, dz = t[2] - s.z;
					if (dx * dx + dy * dy + dz * dz <= RoomStore.SAME * RoomStore.SAME) dup = true;
				}
				if (dup) continue;
				taken.add(new double[] {s.x, s.y, s.z});
				double[] w = RoomGrid.toWorld(p.cell(), p.rotation(), s.x, s.z);
				out.add(new Object[] {w[0], s.y, w[1], s.kind});
			}
		}
		return out;
	}

	void draw() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !inDungeon() || placements.isEmpty()) return;
		Vec3 me = mc.player.position();
		RoomGrid.Cell here = RoomGrid.at(me.x, me.z);
		RoomLearner.Placement herePl = here == null ? null : placements.get(here);
		try (var c = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
			if (on(waypoints)) {
				Object[] nearest = null;
				double best = Double.MAX_VALUE;
				List<Object[]> shown = new ArrayList<>();
				for (RoomLearner.Placement p : placements.values()) {
					if (Math.abs(p.cell().cx() - me.x) > 48 || Math.abs(p.cell().cz() - me.z) > 48) continue;
					for (Object[] s : secretsIn(p)) {
						double x = (double) s[0], y = (double) s[1], z = (double) s[2];
						if (learner.found(x, y, z)) continue;
						shown.add(s);
						if (p == herePl) {
							double d = me.distanceToSqr(x, y, z);
							if (d < best) {
								best = d;
								nearest = s;
							}
						}
					}
				}
				for (Object[] s : shown) {
					if (on(nextOnly) && s != nearest) continue;
					drawSecret(me, s, s == nearest);
				}
			}
			if (on(route) && herePl != null) drawRoute(herePl);
		}
	}

	void drawSecret(Vec3 me, Object[] s, boolean nearest) {
		double x = (double) s[0], y = (double) s[1], z = (double) s[2];
		RoomStore.Kind k = (RoomStore.Kind) s[3];
		int argb = 0xFF000000 | k.rgb;
		double half = k == RoomStore.Kind.OTHER ? 0.3 : 0.5;
		AABB box = new AABB(x - half, y - half, z - half, x + half, y + half, z + half);
		Gizmos.cuboid(box, GizmoStyle.strokeAndFill(argb, nearest ? 3f : 2f, (argb & 0x00FFFFFF) | (nearest ? 0x60000000 : 0x30000000))).setAlwaysOnTop();
		double dist = Math.sqrt(me.distanceToSqr(x, y, z));
		Gizmos.billboardText(k.label + " " + Math.round(dist) + "m", new Vec3(x, y + 0.9, z), TextGizmo.Style.forColorAndCentered(argb)).setAlwaysOnTop();
	}

	void drawRoute(RoomLearner.Placement p) {
		RoomStore.Route rt = local.route(p.id());
		if (rt == null && on(shared)) rt = pack.route(p.id());
		if (rt == null) return;
		Vec3 prev = null;
		for (double[] pt : rt.points()) {
			double[] w = RoomGrid.toWorld(p.cell(), p.rotation(), pt[0], pt[2]);
			Vec3 v = new Vec3(w[0], pt[1] + 0.1, w[1]);
			if (prev != null) Gizmos.line(prev, v, 0xFF55FFFF, 3f).setAlwaysOnTop();
			prev = v;
		}
	}

	// ---- files ----

	static RoomStore read(Path f) {
		try {
			if (!Files.isRegularFile(f) || Files.size(f) > 16_000_000) return null;
			return RoomStore.fromJson(JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)));
		} catch (Exception e) {
			FatesOfaClient.LOG.warn("couldn't read room data {}: {}", f.getFileName(), e.toString());
			return null;
		}
	}

	static void write(Path f, String text) throws IOException {
		Files.createDirectories(f.getParent());
		Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
		Files.writeString(tmp, text, StandardCharsets.UTF_8);
		Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	void saveSoon() {
		lastSave = System.currentTimeMillis();
		if (!local.dirty()) return;
		String json = local.toJson(null).toString();
		local.saved();
		Ofa.get().io.execute(() -> {
			try {
				write(dir().resolve("learned.json"), json);
			} catch (IOException e) {
				FatesOfaClient.LOG.warn("couldn't save learned rooms: {}", e.toString());
			}
		});
	}

	public void saveNow() {
		if (!local.dirty()) return;
		try {
			write(dir().resolve("learned.json"), local.toJson(null).toString());
			local.saved();
		} catch (IOException e) {
			FatesOfaClient.LOG.warn("couldn't save learned rooms: {}", e.toString());
		}
	}

	void fetchPack(long now) {
		lastPackCheck = now;
		if (!Ofa.get().settings.on(General.NETWORK)) return;
		String etag = packEtag;
		Ofa.get().io.execute(() -> {
			try {
				Http.Response r = Http.get(PACK_URL, 16_000_000, etag);
				if (!r.ok()) return; // 304 unchanged, 404 not published yet
				RoomStore p = RoomStore.fromJson(JsonParser.parseString(r.text()));
				if (p == null) return;
				pack = p;
				packEtag = r.etag();
				write(dir().resolve("pack.json"), r.text());
			} catch (Exception e) {
				FatesOfaClient.LOG.info("room pack not updated: {}", e.toString());
			}
		});
	}

	// ---- commands ----

	public void status() {
		Game.info("Your contributor id: " + contributor + " (random, made by OFA; it's what share files carry instead of your name).");
		Game.info("Rooms you learned: " + local.roomCount() + " rooms · " + local.secretCount() + " secrets · " + local.routeCount() + " routes.");
		Game.info("Shared pack: " + pack.roomCount() + " rooms · " + pack.secretCount() + " secrets." + (active() ? "" : " (Secret waypoints are off: /ofa feature secret_waypoints on)"));
	}

	/** Writes your own finds to rooms/export/ for sharing. */
	public void export() {
		RoomStore mine = local.own(contributor);
		if (mine.secretCount() == 0 && mine.routeCount() == 0) {
			Game.warn("Nothing to share yet: run some dungeons with secret waypoints on first.");
			return;
		}
		String name = "ofa-rooms-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".json";
		Path f = dir().resolve("export").resolve(name);
		try {
			write(f, new GsonBuilder().create().toJson(mine.toJson(contributor)));
			Game.ok("Saved " + mine.secretCount() + " secrets and " + mine.routeCount() + " routes to config/fatesofa/rooms/export/" + name);
			Game.info("Send it to a friend (they put it in their rooms/import folder) or attach it to a \"Room data\" issue on GitHub to add it to everyone's pack.");
		} catch (IOException e) {
			Game.error("Couldn't write the file: " + e.getMessage());
		}
	}

	/** Merges every share file in rooms/import/ and moves them to rooms/import/done/. */
	public void importAll() {
		Path in = dir().resolve("import");
		try {
			Files.createDirectories(in);
		} catch (IOException e) {
			Game.error("Couldn't open " + in + ": " + e.getMessage());
			return;
		}
		int files = 0, added = 0, bad = 0;
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(in, "*.json")) {
			for (Path f : ds) {
				RoomStore s = read(f);
				if (s == null) {
					bad++;
					continue;
				}
				added += local.merge(s);
				files++;
				Path done = in.resolve("done");
				Files.createDirectories(done);
				Files.move(f, done.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			Game.error("Import stopped: " + e.getMessage());
		}
		saveSoon();
		if (files == 0 && bad == 0) Game.warn("Put share files (.json) in config/fatesofa/rooms/import/ first.");
		else Game.ok("Imported " + files + " file(s): " + added + " new secrets." + (bad > 0 ? " " + bad + " file(s) weren't OFA room files." : ""));
	}

	public void updatePack() {
		lastPackCheck = 0;
		packEtag = null;
		fetchPack(System.currentTimeMillis());
		Game.info("Checking for a newer shared room pack…");
	}

	// ---- test hooks ----

	/** For the in-game test: learn a find right now as if the counter had gone up. */
	public void testLearn(RoomStore.Kind kind, double x, double y, double z) {
		recognise(Minecraft.getInstance());
		var p = placementAt(x, z);
		if (p == null) throw new IllegalStateException("no room recognised at " + x + ", " + z + " (" + placements.size() + " known)");
		double[] r = RoomGrid.toRoom(p.cell(), p.rotation(), x, z);
		local.addSecret(p.id(), r[0], y, r[1], kind, contributor);
	}

	/** For the in-game test: a route through world points, stored for the room at the first point. */
	public void testRoute(List<double[]> world) {
		var p = placementAt(world.get(0)[0], world.get(0)[2]);
		List<double[]> pts = new ArrayList<>();
		for (double[] w : world) {
			double[] r = RoomGrid.toRoom(p.cell(), p.rotation(), w[0], w[2]);
			pts.add(new double[] {r[0], w[1], r[1]});
		}
		local.offerRoute(p.id(), new RoomStore.Route(15_000, pts, contributor));
	}

	public int knownCells() {
		return placements.size();
	}
}
