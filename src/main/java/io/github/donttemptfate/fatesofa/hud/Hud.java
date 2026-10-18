package io.github.donttemptfate.fatesofa.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.donttemptfate.fatescommon.ui.Card;
import io.github.donttemptfate.fatesofa.FatesOfaClient;
import io.github.donttemptfate.fatesofa.config.Settings;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The HUD compositor: owns every OFA overlay. Each frame it asks the visible elements for their cards,
 * places them with {@link Layout} (anchors, safe areas, no overlaps) and draws them. Positions are
 * stored per element as anchor + offset in the settings file ("hud.<id>").
 */
public final class Hud {
	private Hud() {}

	private static final Map<String, HudElement> ELEMENTS = new LinkedHashMap<>();
	private static Settings settings;
	private static volatile boolean editing;

	public static void register(HudElement e) {
		ELEMENTS.put(e.id, e);
	}

	public static List<HudElement> elements() {
		return new ArrayList<>(ELEMENTS.values());
	}

	public static void init(Settings s) {
		settings = s;
		s.onChange(k -> lastLayout = null); // any setting may change what a card shows
		s.onChange(k -> {
			if (k.startsWith("hud.") || k.startsWith("hudhide.")) invalidate();
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(FatesOfaClient.MOD_ID, "hud"), (g, delta) -> {
			io.github.donttemptfate.fatesofa.core.Diagnostics.frame();
			if (editing) return;
			try {
				draw(g, false);
			} catch (Throwable e) {
				FatesOfaClient.LOG.warn("HUD error", e);
			}
		});
	}

	public static void editing(boolean on) {
		editing = on;
	}

	/** Positions and hidden flags read once, not every frame; cleared when they change (editor, import). */
	private static final Map<String, HudPos> POS = new java.util.concurrent.ConcurrentHashMap<>();
	private static final Map<String, Boolean> HIDDEN = new java.util.concurrent.ConcurrentHashMap<>();
	/** The last frame's layout, reused for a quarter second (cards are rebuilt 4×/s, not every frame). */
	private static volatile List<Placed> lastLayout;
	private static volatile long lastLayoutAt;
	private static volatile int lastW, lastH;
	static final long REBUILD_MS = 250;

	public static HudPos pos(HudElement e) {
		return POS.computeIfAbsent(e.id, id -> {
			JsonElement j = settings == null ? null : settings.raw("hud." + id);
			return j != null && j.isJsonObject() ? HudPos.fromJson(j.getAsJsonObject(), e.defaultPos) : e.defaultPos;
		});
	}

	public static void setPos(HudElement e, HudPos p) {
		settings.putRaw("hud." + e.id, p == null ? null : p.toJson());
		POS.remove(e.id);
		lastLayout = null;
	}

	public static boolean hidden(HudElement e) {
		return HIDDEN.computeIfAbsent(e.id, id -> {
			JsonElement j = settings == null ? null : settings.raw("hudhide." + id);
			return j != null && j.isJsonPrimitive() && j.getAsBoolean();
		});
	}

	public static void setHidden(HudElement e, boolean hide) {
		settings.putRaw("hudhide." + e.id, hide ? new com.google.gson.JsonPrimitive(true) : null);
		HIDDEN.remove(e.id);
		lastLayout = null;
	}

	/** Forget cached positions and the last layout (settings imported, a feature switched). */
	public static void invalidate() {
		POS.clear();
		HIDDEN.clear();
		lastLayout = null;
	}

	/** One placed element for this frame. */
	public record Placed(HudElement element, Card card, Rect rect, float scale) {
	}

	/**
	 * Builds and places what would be drawn. {@code sample}: the HUD editor's view (every element,
	 * example content where there's nothing live).
	 */
	public static List<Placed> layout(int screenW, int screenH, boolean sample) {
		return layout(screenW, screenH, sample, false);
	}

	/** {@code all}: in the editor, include elements of switched-off features too. */
	public static List<Placed> layout(int screenW, int screenH, boolean sample, boolean all) {
		List<HudElement> els = new ArrayList<>(ELEMENTS.values());
		els.sort(Comparator.comparingInt(e -> -e.priority));
		List<Layout.Item> items = new ArrayList<>();
		Map<String, Card> cards = new LinkedHashMap<>();
		var mc = Minecraft.getInstance();
		var ofa = io.github.donttemptfate.fatesofa.Ofa.get();
		boolean menuOpen = mc != null && mc.gui != null && mc.gui.screen() != null;
		for (HudElement e : els) {
			if (!sample && (hidden(e) || !e.owner.active())) continue;
			if (!sample && e.display != null && ofa != null && !e.display.visible(ofa.state.island(), menuOpen)) continue;
			if (sample && !all && !e.owner.active()) continue;
			Card c = null;
			if (!sample || e.owner.active()) {
				boolean vis = e.owner.guard(() -> e.visible.getAsBoolean(), false);
				if (vis) c = e.owner.guard(e.content::get, null);
			}
			if (c == null && sample) {
				try {
					c = e.sample.get();
				} catch (RuntimeException ex) {
					c = null;
				}
			}
			if (c == null || c.isEmpty()) continue;
			cards.put(e.id, c);
			items.add(new Layout.Item(e.id, c.width(), c.height(), pos(e)));
		}
		Map<String, Rect> rects = Layout.place(items, screenW, screenH, reserved(screenW, screenH));
		List<Placed> out = new ArrayList<>();
		for (Layout.Item it : items) {
			out.add(new Placed(ELEMENTS.get(it.id()), cards.get(it.id()), rects.get(it.id()), it.pos().scale()));
		}
		return out;
	}

	static void draw(GuiGraphicsExtractor g, boolean sample) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.hud.isHidden()) return;
		long now = System.currentTimeMillis();
		List<Placed> placed = lastLayout;
		if (sample || placed == null || now - lastLayoutAt >= REBUILD_MS || lastW != g.guiWidth() || lastH != g.guiHeight()) {
			placed = layout(g.guiWidth(), g.guiHeight(), sample);
			if (!sample) {
				lastLayout = placed;
				lastLayoutAt = now;
				lastW = g.guiWidth();
				lastH = g.guiHeight();
			}
		}
		for (Placed p : placed) {
			p.element().owner.guard(() -> p.card().draw(g, p.rect().x(), p.rect().y(), p.scale()));
		}
	}

	/** Screen areas vanilla uses: hotbar + status bars, and the sidebar's usual spot. */
	static List<Rect> reserved(int w, int h) {
		List<Rect> r = new ArrayList<>();
		r.add(new Rect(w / 2 - 92, h - 50, 184, 50)); // hotbar, health, food, armour, xp
		r.add(new Rect(w / 2 - 92, 0, 184, 20)); // boss bar
		r.add(new Rect(w - 124, h / 2 - 84, 124, 168)); // the scoreboard sidebar (right, centred)
		return r;
	}

	public static JsonObject positionsJson() {
		JsonObject o = new JsonObject();
		for (HudElement e : ELEMENTS.values()) o.add(e.id, pos(e).toJson());
		return o;
	}
}
