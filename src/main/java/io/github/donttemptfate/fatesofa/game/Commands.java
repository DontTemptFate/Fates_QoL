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
				.then(ClientCommands.literal("planner").executes(c -> {
					Game.openNextTick(io.github.donttemptfate.fatesofa.ui.PlannerScreen::new);
					return 1;
				}))
				.then(ClientCommands.literal("reset")
						.then(ClientCommands.literal("slayer").executes(c -> {
							if (io.github.donttemptfate.fatesofa.features.SlayerFeature.INSTANCE != null) io.github.donttemptfate.fatesofa.features.SlayerFeature.INSTANCE.tracker.reset();
							Game.ok("Slayer tracker reset.");
							return 1;
						})))
				.then(ClientCommands.literal("wp")
						.then(ClientCommands.literal("add").executes(c -> wpAdd("Waypoint"))
								.then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(c -> wpAdd(StringArgumentType.getString(c, "name")))))
						.then(ClientCommands.literal("remove").then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(c -> {
							var f = io.github.donttemptfate.fatesofa.features.WaypointFeature.INSTANCE;
							boolean ok = f.waypoints.remove(StringArgumentType.getString(c, "name"), f.island());
							f.save();
							if (ok) Game.ok("Waypoint removed.");
							else Game.warn("No waypoint with that name here.");
							return 1;
						})))
						.then(ClientCommands.literal("clear").executes(c -> {
							var f = io.github.donttemptfate.fatesofa.features.WaypointFeature.INSTANCE;
							f.waypoints.clear(f.island());
							f.save();
							Game.ok("Waypoints on this island cleared.");
							return 1;
						})))
				.then(ClientCommands.literal("storage")
						.executes(c -> {
							Game.openNextTick(p -> new io.github.donttemptfate.fatesofa.ui.StorageScreen(p, ""));
							return 1;
						}))
				.then(ClientCommands.literal("find")
						.then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes(c -> {
							String q = StringArgumentType.getString(c, "text");
							Game.openNextTick(p -> new io.github.donttemptfate.fatesofa.ui.StorageScreen(p, q));
							return 1;
						})))
				.then(ClientCommands.literal("recipe")
						.then(ClientCommands.argument("item", StringArgumentType.greedyString()).executes(c -> recipe(StringArgumentType.getString(c, "item")))))
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

	static int recipe(String q) {
		var f = io.github.donttemptfate.fatesofa.features.ItemListFeature.INSTANCE;
		var idx = f == null ? null : f.index();
		if (idx == null) {
			Game.warn("Item data isn't loaded yet.");
			return 0;
		}
		String key = q.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		if (Ofa.get().repo.items().get(key) == null) {
			var hits = idx.search(q);
			if (hits.isEmpty()) {
				Game.error("No item matches \"" + q + "\".");
				return 0;
			}
			key = hits.get(0).key();
		}
		String k = key;
		Game.openNextTick(p -> new io.github.donttemptfate.fatesofa.ui.RecipeScreen(p, k, false));
		return 1;
	}

	static int wpAdd(String name) {
		var f = io.github.donttemptfate.fatesofa.features.WaypointFeature.INSTANCE;
		var p = Game.mc().player;
		if (f == null || p == null) return 0;
		var pos = p.blockPosition();
		f.waypoints.add(new io.github.donttemptfate.fatesofa.waypoints.Waypoints.Waypoint(name, pos.getX(), pos.getY(), pos.getZ(), 0x55FFFF, f.island(), 0));
		f.save();
		Game.ok("Waypoint \"" + name + "\" added at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ".");
		return 1;
	}

	static int help() {
		Game.info("/ofa — settings · /ofa hud — move HUD elements · /ofa recipe <item> · /ofa planner · /ofa storage · /ofa find <text> · /ofa wp add|remove|clear · "
				+ "/ofa status · /ofa repo [update] · /ofa feature <id> on|off · /ofa export / import (clipboard)");
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
