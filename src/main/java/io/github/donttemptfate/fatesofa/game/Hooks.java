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
	private static boolean updateNoticed;

	public static void register(Ofa o) {
		Keys.register();
		Alerts.registerSink();
		ChatBus.register(); // before any feature's chat hook, so filters never hide a message from trackers
		ModApi.register(o.state);
		ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> Commands.register(d));
		if (o.settings.on(io.github.donttemptfate.fatesofa.features.General.UPDATES) && o.settings.on(io.github.donttemptfate.fatesofa.features.General.NETWORK))
			o.updates.check(o.io, null);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			try {
				tick(mc, o);
			} catch (Throwable e) {
				FatesOfaClient.LOG.warn("tick error", e);
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			o.state.setOnHypixel(Game.isHypixel(mc) || io.github.donttemptfate.fatesofa.game.Dev.test());
			o.state.worldChanged(System.currentTimeMillis());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> o.state.setOnHypixel(false));
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((mc, level) -> {
			o.state.worldChanged(System.currentTimeMillis());
			io.github.donttemptfate.fatesofa.items.Stacks.resourcesChanged(); // Hypixel's pack may have (un)loaded
			if (io.github.donttemptfate.fatesofa.features.SecretRoutes.INSTANCE != null) io.github.donttemptfate.fatesofa.features.SecretRoutes.INSTANCE.worldChanged();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			save(o);
			try {
				if (io.github.donttemptfate.fatesofa.features.StorageFeature.INSTANCE != null) io.github.donttemptfate.fatesofa.features.StorageFeature.INSTANCE.saveNow();
				if (io.github.donttemptfate.fatesofa.features.PlannerFeature.INSTANCE != null && io.github.donttemptfate.fatesofa.features.PlannerFeature.INSTANCE.data() != null)
					io.github.donttemptfate.fatesofa.features.PlannerFeature.INSTANCE.data().save();
			} catch (Throwable e) {
				FatesOfaClient.LOG.warn("couldn't save data on exit: {}", e.toString());
			}
			o.io.shutdownNow();
		});
	}

	static void tick(Minecraft mc, Ofa o) {
		Game.tickPendingScreen(mc);
		Keys.tick(mc);
		StateReader.tick(mc, o.state);
		long now = System.currentTimeMillis();
		io.github.donttemptfate.fatesofa.core.Diagnostics.tick(now);
		o.repo.tick(now);
		if (o.state.onSkyBlock()) o.prices.refresh(now);
		if (!updateNoticed && o.updates.checked() && o.state.onSkyBlock()) {
			updateNoticed = true;
			var l = o.updates.latest();
			boolean betas = o.settings.on(io.github.donttemptfate.fatesofa.features.General.BETAS);
			if (l != null && (betas || !l.prerelease()) && io.github.donttemptfate.fatesofa.data.Updates.newer(l.version(), o.version)
					&& o.settings.on(io.github.donttemptfate.fatesofa.features.General.UPDATES))
				Game.info("Fates OFA " + l.version() + " is out (you have " + o.version.replaceAll("\\+.*", "") + "). Download: " + l.url());
		}
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
