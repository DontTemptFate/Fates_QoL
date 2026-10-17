package io.github.donttemptfate.fatesofa.game;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.core.Features;
import io.github.donttemptfate.fatesofa.hud.HudEditorScreen;
import io.github.donttemptfate.fatesofa.ui.SettingsScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.Locale;

/** /ofa — client-side only; nothing here is sent to Hypixel. */
public final class Commands {
	private Commands() {}

	static void register(CommandDispatcher<FabricClientCommandSource> d) {
		d.register(ClientCommands.literal("ofa")
				.executes(c -> {
					SettingsScreen.open(null);
					return 1;
				})
				.then(ClientCommands.literal("help").executes(c -> help()))
				.then(ClientCommands.literal("settings").executes(c -> {
					SettingsScreen.open(null);
					return 1;
				}))
				.then(ClientCommands.literal("hud").executes(c -> {
					Game.openNextTick(HudEditorScreen::new);
					return 1;
				}))
				.then(ClientCommands.literal("repo")
						.executes(c -> {
							Game.info("Item data: " + Ofa.get().repo.state().name().toLowerCase(Locale.ROOT) + " · " + Ofa.get().repo.message());
							return 1;
						})
						.then(ClientCommands.literal("update").executes(c -> {
							Ofa.get().repo.checkNow();
							Game.info("Checking for newer item data…");
							return 1;
						})))
				.then(ClientCommands.literal("status").executes(c -> status()))
				.then(ClientCommands.literal("feature")
						.then(ClientCommands.argument("id", StringArgumentType.word())
								.then(ClientCommands.literal("on").executes(c -> setFeature(StringArgumentType.getString(c, "id"), true)))
								.then(ClientCommands.literal("off").executes(c -> setFeature(StringArgumentType.getString(c, "id"), false)))))
				.then(ClientCommands.literal("export").executes(c -> {
					Game.mc().keyboardHandler.setClipboard(Ofa.get().settings.export());
					Game.ok("Settings copied to the clipboard.");
					return 1;
				}))
				.then(ClientCommands.literal("import").executes(c -> {
					int n = Ofa.get().settings.importLine(Game.mc().keyboardHandler.getClipboard());
					if (n < 0) Game.error("The clipboard doesn't hold OFA settings.");
					else Game.ok("Applied " + n + " settings.");
					return 1;
				})));
	}

	static int help() {
		Game.info("/ofa — settings · /ofa hud — move HUD elements · /ofa status · /ofa repo [update] · "
				+ "/ofa feature <id> on|off · /ofa export / import (clipboard)");
		return 1;
	}

	static int status() {
		Ofa o = Ofa.get();
		var s = o.state;
		Game.info("SkyBlock: " + (s.onSkyBlock() ? "yes" : "no") + " · island " + (s.island() == null ? "?" : s.island().display)
				+ " · area " + (s.area() == null ? "?" : s.area()) + " · profile " + s.profileType().name().toLowerCase(Locale.ROOT));
		Game.info("Item data: " + o.repo.message() + " · bazaar " + o.prices.bazaarCount() + " · AH " + o.prices.ahCount()
				+ " · location channel " + (ModApi.active() ? "on" : "off"));
		long failed = Features.all().stream().filter(Feature::failed).count();
		if (failed > 0) Game.warn(failed + " feature(s) switched off after errors — see /ofa → Diagnostics.");
		return 1;
	}

	static int setFeature(String id, boolean on) {
		Feature f = Features.get(id);
		if (f == null) {
			Game.error("No feature \"" + id + "\".");
			return 0;
		}
		Ofa.get().settings.set(f.enabled, on);
		if (on) f.resetErrors();
		Game.ok(f.name + (on ? " on." : " off."));
		return 1;
	}
}
