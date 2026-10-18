package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.item.Items;
import io.github.donttemptfate.fates.common.text.Strip;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.IslandTrackers;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Display;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;
import io.github.donttemptfate.fatesofa.state.Island;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.Map;

/**
 * Crimson Isle: faction reputation and today's faction quests (tab list), the trophy fish tiers you have (read
 * when you open Odger's trophy fish menu) and the Kuudra keys you carry.
 */
public final class CrimsonIsleTracker extends Feature {
	final Display display = new Display(this, "crimson", 0xFFFF5555, Display.lines("reputation", "Reputation", "quests", "Faction quests done",
			"trophy", "Trophy fish tiers caught", "keys", "Kuudra keys carried"), List.of("reputation", "quests", "trophy", "keys"));
	private volatile int trophyTiers = -1, trophyTotal;
	private int tick;

	public CrimsonIsleTracker() {
		super("crimson_isle", "Crimson Isle", "Faction reputation and quests, trophy fish tiers caught (from Odger's menu) and Kuudra keys you carry.",
				Category.COMBAT, true, Risk.SAFE);
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			if (++tick % 20 != 0 || !(mc.gui.screen() instanceof AbstractContainerScreen<?> s)) return;
			if (!Strip.clean(s.getTitle().getString()).contains("Trophy Fish")) return;
			int tiers = 0, fish = 0;
			for (Slot sl : s.getMenu().slots) {
				if (sl.container instanceof Inventory || sl.getItem().isEmpty()) continue;
				List<String> lore = Items.lorePlain(sl.getItem());
				if (lore.stream().noneMatch(l -> l.trim().startsWith("Bronze"))) continue;
				fish++;
				tiers += IslandTrackers.trophyTiers(lore);
			}
			if (fish > 0) {
				trophyTiers = tiers;
				trophyTotal = fish * 4;
			}
		}));
		Hud.register(new HudElement("crimson", "Crimson Isle", this, HudPos.at(Anchor.LEFT, 4, -100), 17, this::card, this::sample,
				() -> o.state.in(Island.CRIMSON_ISLE) || o.state.in(Island.KUUDRA)).display(display));
	}

	int keys() {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player == null) return 0;
		int n = 0;
		for (int i = 0; i < 36; i++) {
			String key = Items.key(mc.player.getInventory().getItem(i));
			if (key != null && key.startsWith("KUUDRA") && key.endsWith("KEY")) n += mc.player.getInventory().getItem(i).getCount();
		}
		return n;
	}

	Card card() {
		Ofa o = Ofa.get();
		Card c = Trackers.card(display, "CRIMSON ISLE", null);
		Card.KeyValues kv = Trackers.rows();
		display.each(Map.of(
				"reputation", () -> {
					for (var e : IslandTrackers.reputation(o.state.tab()).entrySet()) Trackers.row(kv, e.getKey(), Trackers.num(display, e.getValue()), Ui.GOLD);
				},
				"quests", () -> {
					int[] q = IslandTrackers.factionQuests(o.state.tab());
					if (q != null) Trackers.row(kv, "Quests", q[0] + "/" + q[1], q[0] >= q[1] ? Ui.GREEN : Ui.WHITE);
				},
				"trophy", () -> {
					if (trophyTiers >= 0) Trackers.row(kv, "Trophy fish", trophyTiers + "/" + trophyTotal + " tiers", Ui.AQUA);
				},
				"keys", () -> {
					int k = keys();
					if (k > 0) Trackers.row(kv, "Kuudra keys", Integer.toString(k), Ui.WHITE);
				}));
		if (kv.isEmpty()) return null;
		c.add(kv);
		return c;
	}

	Card sample() {
		Card c = Trackers.card(display, "CRIMSON ISLE", null);
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Mage", "12.4k", Ui.GOLD);
		Trackers.row(kv, "Quests", "3/5", Ui.WHITE);
		Trackers.row(kv, "Trophy fish", "41/72 tiers", Ui.AQUA);
		c.add(kv);
		return c;
	}
}
