package io.github.donttemptfate.fatesofa.features;

import com.google.gson.JsonObject;
import io.github.donttemptfate.fates.common.item.ItemKey;
import io.github.donttemptfate.fates.common.item.Items;
import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.SkyBlockTime;
import io.github.donttemptfate.fatesofa.activity.TabWidgets;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.ActivitySession;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.game.Alerts;
import io.github.donttemptfate.fatesofa.game.ChatBus;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;
import io.github.donttemptfate.fatesofa.state.Island;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Garden: crops per hour from your farming tool's counter (and what they'd sell to an NPC for), visitors
 * waiting, pests, composter, the next Jacob's contest, and an alert when a pest appears.
 */
public final class GardenFeature extends Feature {
	final Setting<Boolean> pestAlert = setting(Setting.bool("garden.pestAlert", "Pest alert", true)
			.help("A title and sound when a pest appears, with its plot."));
	final io.github.donttemptfate.fatesofa.core.Alert pest = new io.github.donttemptfate.fatesofa.core.Alert(this, "garden.pestAlert", "Pest alert",
			"PEST!", "Plot {plot}", 0xFF55FF55, "minecraft:block.note_block.pling|1|2", "plot");

	static final Pattern PEST = Pattern.compile(".*Pests? (?:has|have) (?:appeared|spawned) in (?:the )?Plot - (?<plot>[\\w ]+)!?.*");
	static final String[][] CROPS = {{"WHEAT", "WHEAT"}, {"CARROT", "CARROT_ITEM"}, {"POTATO", "POTATO_ITEM"}, {"PUMPKIN", "PUMPKIN"},
			{"MELON", "MELON"}, {"MUSHROOM", "RED_MUSHROOM"}, {"CACTUS", "CACTUS"}, {"CANE", "SUGAR_CANE"}, {"WART", "NETHER_STALK"},
			{"COCO", "INK_SACK-3"}};

	private final ActivitySession session = new ActivitySession(60_000);
	private String toolUuid;
	private long lastCount = -1;
	private String crop;
	private double crops;
	private int tick;

	public GardenFeature() {
		super("garden", "Garden HUD", "Crops per hour (from your tool's counter) and their NPC value, visitors, pests, composter, "
				+ "next Jacob's contest and a pest alert.", Category.FARMING, true, Risk.SAFE);
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			if (++tick % 5 == 0 && o.state.in(Island.GARDEN)) counter(mc);
		}));
		ChatBus.onChat(m -> guard(() -> {
			if (!on(pestAlert)) return;
			Pattern p = o.patterns.get("garden.pests-api.chat.pest-spawn");
			Matcher mm = (p != null ? p : PEST).matcher(m.plain());
			if (mm.matches()) {
				String plot = null;
				try {
					plot = mm.group("plot");
				} catch (IllegalArgumentException ignored) {
					// repo pattern without a plot group
				}
				pest.fire(java.util.Map.of("plot", plot == null ? "?" : plot.trim()), o.state.island(), System.currentTimeMillis());
			}
		}));
		Hud.register(new HudElement("garden", "Garden", this, HudPos.at(Anchor.LEFT, 4, 60), 20, this::card, this::sample,
				() -> o.state.in(Island.GARDEN)));
	}

	void counter(Minecraft mc) {
		if (mc.player == null) return;
		ItemStack held = mc.player.getMainHandItem();
		JsonObject d = Items.data(held);
		if (d == null) return;
		long count = Math.max(ItemKey.num(d, "mined_crops", -1), ItemKey.num(d, "farmed_cultivating", -1));
		if (count < 0) return;
		String uuid = ItemKey.str(d, "uuid");
		long now = System.currentTimeMillis();
		if (uuid == null || !uuid.equals(toolUuid)) {
			toolUuid = uuid;
			lastCount = count;
			crop = cropOf(ItemKey.str(d, "id"));
			return;
		}
		if (count > lastCount) {
			crops += count - lastCount;
			session.add("crops", count - lastCount, now);
		}
		lastCount = count;
	}

	static String cropOf(String toolId) {
		if (toolId == null) return null;
		String t = toolId.toUpperCase(Locale.ROOT);
		for (String[] c : CROPS) if (t.contains(c[0])) return c[1];
		return null;
	}

	Card card() {
		Ofa o = Ofa.get();
		long now = System.currentTimeMillis();
		Card c = Trackers.card("GARDEN", null);
		Card.KeyValues kv = Trackers.rows();
		double ph = session.perHour(crops);
		if (ph > 0) {
			Trackers.row(kv, "Crops / hour", Fmt.shortNum(ph), Ui.AQUA);
			Double npc = crop == null ? null : o.prices.npcPrice(crop);
			if (npc != null) Trackers.row(kv, "NPC value / hour", Fmt.shortNum(ph * npc), Ui.GOLD);
		}
		List<String> visitors = TabWidgets.visitors(o.state.tab());
		if (!visitors.isEmpty()) Trackers.row(kv, "Visitors", visitors.size() + ": " + String.join(", ", visitors), Ui.WHITE);
		TabWidgets.Pests pests = TabWidgets.pests(o.state.tab());
		if (pests != null && pests.alive() > 0) Trackers.row(kv, "Pests", pests.alive() + (pests.plots() != null ? " · plots " + pests.plots() : ""), Ui.RED);
		for (String l : TabWidgets.composter(o.state.tab())) {
			int colon = l.indexOf(':');
			if (colon > 0) Trackers.row(kv, "Composter " + l.substring(0, colon).toLowerCase(Locale.ROOT), l.substring(colon + 1).trim(), Ui.WHITE);
		}
		for (SkyBlockTime.Event e : SkyBlockTime.upcoming(now)) {
			if (!e.name().startsWith("Jacob")) continue;
			Trackers.row(kv, "Jacob's contest", e.running(now) ? "now · " + Fmt.duration(e.endMs() - now) + " left" : "in " + Fmt.duration(e.startMs() - now),
					e.running(now) ? Ui.GREEN : Ui.WHITE);
		}
		c.add(kv);
		return c;
	}

	Card sample() {
		Card c = Trackers.card("GARDEN", null);
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Crops / hour", "112k", Ui.AQUA);
		Trackers.row(kv, "NPC value / hour", "336k", Ui.GOLD);
		Trackers.row(kv, "Visitors", "2: Jacob, Rhys", Ui.WHITE);
		Trackers.row(kv, "Pests", "3 · plots 2, 5", Ui.RED);
		Trackers.row(kv, "Jacob's contest", "in 12m 40s", Ui.WHITE);
		c.add(kv);
		return c;
	}
}
