package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.game.ChatBus;
import io.github.donttemptfate.fatesofa.state.Island;
import io.github.donttemptfate.fatesofa.waypoints.Waypoints;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Waypoints in the world: your own (/ofa wp add) and positions shared in chat ("x: 1, y: 2, z: 3", shown for a
 * few minutes). A box, the name and the distance, visible through walls.
 */
public final class WaypointFeature extends Feature {
	public static WaypointFeature INSTANCE;
	final Setting<Boolean> fromChat = setting(Setting.bool("waypoints.chat", "Waypoints from chat coordinates", true)
			.help("When someone shares \"x: …, y: …, z: …\" in chat, show it for a few minutes."));
	final Setting<Integer> keepMinutes = setting(Setting.integer("waypoints.keep", "Keep shared waypoints (minutes)", 3, 1, 30));
	final io.github.donttemptfate.fatesofa.core.Highlight look = new io.github.donttemptfate.fatesofa.core.Highlight(this, "waypoints.look", 0xFF55FFFF);
	final Setting<Boolean> ownColours = setting(Setting.bool("waypoints.ownColours", "Use each waypoint's own colour", true).group(io.github.donttemptfate.fatesofa.core.Highlight.GROUP));
	final Setting<Boolean> distance = setting(Setting.bool("waypoints.distance", "Show the distance", true).group(io.github.donttemptfate.fatesofa.core.Highlight.GROUP));
	final Setting<String> chatOn = setting(Setting.islands("waypoints.chat.on", "Chat coordinates only on", "").advanced()
			.help("Islands where shared coordinates become waypoints. Nothing picked = everywhere."));

	public final Waypoints waypoints = new Waypoints();

	public WaypointFeature() {
		super("waypoints", "Waypoints", "Your own waypoints per island (/ofa wp add) and positions shared in chat, drawn in the world.",
				Category.GENERAL, true, Risk.SAFE);
		INSTANCE = this;
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		waypoints.fromJson(o.settings.raw("waypoints.saved"));
		o.settings.onChange(k -> {
			if (k.equals("waypoints.saved")) waypoints.fromJson(o.settings.raw("waypoints.saved")); // imported
		});
		ChatBus.onChat(m -> guard(() -> {
			if (!on(fromChat) || !o.state.onSkyBlock() || !io.github.donttemptfate.fatesofa.config.Values.allows(get(chatOn), o.state.island())) return;
			Waypoints.Waypoint w = Waypoints.fromChat(m.plain(), island(), m.atMs(), get(keepMinutes) * 60_000L);
			if (w != null) waypoints.add(w);
		}));
		LevelRenderEvents.BEFORE_GIZMOS.register(ctx -> guard(this::draw));
	}

	public String island() {
		Island i = Ofa.get().state.island();
		return i == null ? (io.github.donttemptfate.fatesofa.game.Dev.test() ? "TEST" : "NONE") : i.name();
	}

	public void save() {
		Ofa.get().settings.putRaw("waypoints.saved", waypoints.toJson());
	}

	void draw() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		var list = waypoints.on(island(), System.currentTimeMillis());
		if (list.isEmpty()) return;
		try (var c = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
			long now = System.currentTimeMillis();
			for (Waypoints.Waypoint w : list) {
				int argb = on(ownColours) ? 0xFF000000 | w.color() : look.color(now);
				BlockPos pos = new BlockPos(w.x(), w.y(), w.z());
				double dist = Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(pos)));
				if (!look.inRange(dist)) continue;
				io.github.donttemptfate.fatesofa.game.Draw.box(look, new AABB(pos), argb, 0);
				io.github.donttemptfate.fatesofa.game.Draw.label(look, w.name() + (on(distance) ? " (" + Math.round(dist) + "m)" : ""),
						Vec3.atCenterOf(pos).add(0, 1.2, 0), argb);
			}
		}
	}
}
