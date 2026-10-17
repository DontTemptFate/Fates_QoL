package io.github.donttemptfate.fatesofa.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.donttemptfate.fates.common.ui.Card;
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
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(FatesOfaClient.MOD_ID, "hud"), (g, delta) -> {
			if (!editing) draw(g, false);
		});
	}

	public static void editing(boolean on) {
		editing = on;
	}

	public static HudPos pos(HudElement e) {
		JsonElement j = settings == null ? null : settings.raw("hud." + e.id);
		return j != null && j.isJsonObject() ? HudPos.fromJson(j.getAsJsonObject(), e.defaultPos) : e.defaultPos;
	}

	public static void setPos(HudElement e, HudPos p) {
		settings.putRaw("hud." + e.id, p == null ? null : p.toJson());
	}

	public static boolean hidden(HudElement e) {
		JsonElement j = settings == null ? null : settings.raw("hudhide." + e.id);
		return j != null && j.isJsonPrimitive() && j.getAsBoolean();
	}

	public static void setHidden(HudElement e, boolean hide) {
		settings.putRaw("hudhide." + e.id, hide ? new com.google.gson.JsonPrimitive(true) : null);
	}

	/** One placed element for this frame. */
	public record Placed(HudElement element, Card card, Rect rect, float scale) {
	}

	/**
	 * Builds and places what would be drawn. {@code sample}: the HUD editor's view (every element,
	 * example content where there's nothing live).
	 */
	public static List<Placed> layout(int screenW, int screenH, boolean sample) {
		List<HudElement> els = new ArrayList<>(ELEMENTS.values());
		els.sort(Comparator.comparingInt(e -> -e.priority));
		List<Layout.Item> items = new ArrayList<>();
		Map<String, Card> cards = new LinkedHashMap<>();
		for (HudElement e : els) {
			if (!sample && (hidden(e) || !e.owner.active())) continue;
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
		for (Placed p : layout(g.guiWidth(), g.guiHeight(), sample)) {
			p.element().owner.guard(() -> p.card().draw(g, p.rect().x(), p.rect().y(), p.scale()));
		}
	}

	/** Screen areas vanilla uses: hotbar + status bars, and the sidebar's usual spot. */
	static List<Rect> reserved(int w, int h) {
		List<Rect> r = new ArrayList<>();
		r.add(new Rect(w / 2 - 92, h - 50, 184, 50)); // hotbar, health, food, armour, xp
		r.add(new Rect(w / 2 - 92, 0, 184, 20)); // boss bar
		return r;
	}

	public static JsonObject positionsJson() {
		JsonObject o = new JsonObject();
		for (HudElement e : ELEMENTS.values()) o.add(e.id, pos(e).toJson());
		return o;
	}
}
