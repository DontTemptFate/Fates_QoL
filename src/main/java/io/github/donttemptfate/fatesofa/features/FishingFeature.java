package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.text.Strip;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.FishingTracker;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.game.Alerts;
import io.github.donttemptfate.fatesofa.game.ChatBus;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fishing: sea creatures caught (per hour), double hooks, trophy fish, an alert for rare catches and a
 * "fish on!" alert when the bobber's "!!!" appears. The player still reels in themselves.
 */
public final class FishingFeature extends Feature {
	final Setting<Boolean> biteAlert = setting(Setting.bool("fishing.bite", "\"Fish on!\" alert", true)
			.help("A sound and a big !!! when Hypixel shows the bite marker over your bobber. You still reel in yourself."));
	final Setting<Boolean> rareAlert = setting(Setting.bool("fishing.rare", "Rare sea creature alert", true)
			.help("A title and sound when an Epic or rarer sea creature appears."));

	public final FishingTracker tracker = new FishingTracker();
	private long lastBiteAt;
	private boolean loaded;

	public FishingFeature() {
		super("fishing", "Fishing tracker", "Sea creatures caught and per hour, double hooks, trophy fish, rare-catch and bite alerts.",
				Category.FISHING, true, Risk.SAFE);
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		o.repo.onLoaded(() -> {
			tracker.load(o.repo.skyhanni("SeaCreatures"));
			loaded = true;
		});
		ChatBus.onChat(m -> guard(() -> {
			if (!loaded) {
				tracker.load(o.repo.skyhanni("SeaCreatures"));
				loaded = true;
			}
			FishingTracker.Creature c = tracker.chat(m.plain(), m.atMs());
			if (c != null && on(rareAlert) && (c.rarity().equals("EPIC") || c.rarity().equals("LEGENDARY") || c.rarity().equals("MYTHIC"))) {
				Alerts.title(c.name(), c.rarity().equals("MYTHIC") ? 0xFF55FF : c.rarity().equals("LEGENDARY") ? 0xFFAA00 : 0xAA00AA, c.rarity());
				Alerts.ding();
			}
		}));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> bite(mc)));
		Hud.register(new HudElement("fishing", "Fishing tracker", this, HudPos.at(Anchor.RIGHT, -4, 20), 20, this::card, this::sample,
				() -> o.state.onSkyBlock() && tracker.total() + tracker.trophies().size() > 0));
	}

	void bite(Minecraft mc) {
		if (!on(biteAlert) || mc.player == null || mc.level == null) return;
		FishingHook hook = mc.player.fishing;
		if (hook == null) return;
		long now = System.currentTimeMillis();
		if (now - lastBiteAt < 1500) return;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof ArmorStand) || !e.hasCustomName()) continue;
			if (e.distanceToSqr(hook) > 4) continue;
			String n = Strip.clean(e.getCustomName().getString());
			if (n.equals("!!!")) {
				lastBiteAt = now;
				Alerts.title("!!!", 0xFF5555, null);
				Alerts.ding();
				return;
			}
		}
	}

	Card card() {
		Card c = Trackers.card("FISHING", tracker.total() + " CAUGHT");
		Card.KeyValues kv = Trackers.rows();
		double perHour = tracker.session.perHour(tracker.total());
		if (perHour > 0) Trackers.row(kv, "Per hour", Fmt.trimmed(Math.round(perHour)), Ui.AQUA);
		if (tracker.doubleHooks() > 0) Trackers.row(kv, "Double hooks", Integer.toString(tracker.doubleHooks()), Ui.WHITE);
		List<Map.Entry<String, Integer>> list = new ArrayList<>(tracker.creatures().entrySet());
		list.sort((a, b) -> b.getValue() - a.getValue());
		for (int i = 0; i < Math.min(5, list.size()); i++) Trackers.row(kv, list.get(i).getKey(), Integer.toString(list.get(i).getValue()), Ui.WHITE);
		int trophies = tracker.trophies().values().stream().mapToInt(Integer::intValue).sum();
		if (trophies > 0) Trackers.row(kv, "Trophy fish", Integer.toString(trophies), Ui.GOLD);
		c.add(kv);
		return c;
	}

	Card sample() {
		Card c = Trackers.card("FISHING", "48 CAUGHT");
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Per hour", "212", Ui.AQUA);
		Trackers.row(kv, "Double hooks", "6", Ui.WHITE);
		Trackers.row(kv, "Night Squid", "21", Ui.WHITE);
		c.add(kv);
		return c;
	}
}
