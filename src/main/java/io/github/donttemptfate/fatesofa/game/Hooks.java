package io.github.donttemptfate.fatesofa.game;

import io.github.donttemptfate.fatesofa.FatesOfaClient;
import io.github.donttemptfate.fatesofa.Ofa;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

/** Connects OFA's shared services to the game's events. Feature-specific hooks live in each feature. */
public final class Hooks {
	private Hooks() {}

	private static long lastSave;

	public static void register(Ofa o) {
		Keys.register();
		ModApi.register(o.state);
		ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> Commands.register(d));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			try {
				tick(mc, o);
			} catch (Throwable e) {
				FatesOfaClient.LOG.warn("tick error", e);
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			o.state.setOnHypixel(Game.isHypixel(mc) || Boolean.getBoolean("fatesofa.test"));
			o.state.worldChanged(System.currentTimeMillis());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> o.state.setOnHypixel(false));
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((mc, level) -> o.state.worldChanged(System.currentTimeMillis()));
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			save(o);
			o.io.shutdownNow();
		});
	}

	static void tick(Minecraft mc, Ofa o) {
		Game.tickPendingScreen(mc);
		Keys.tick(mc);
		StateReader.tick(mc, o.state);
		long now = System.currentTimeMillis();
		o.repo.tick(now);
		if (o.state.onSkyBlock()) o.prices.refresh(now);
		if (now - lastSave > 30_000 && o.settings.dirty()) {
			lastSave = now;
			save(o);
		}
	}

	static void save(Ofa o) {
		try {
			o.settings.save();
		} catch (Exception e) {
			FatesOfaClient.LOG.warn("couldn't save settings: {}", e.toString());
		}
	}
}
