package io.github.donttemptfate.fatesofa.test;

import io.github.donttemptfate.fatesofa.FatesOfaClient;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.core.Features;
import io.github.donttemptfate.fatesofa.data.PriceTables;
import io.github.donttemptfate.fatesofa.data.RepoManager;
import io.github.donttemptfate.fatesofa.features.ProtectionFeature;
import io.github.donttemptfate.fatesofa.hud.HudEditorScreen;
import io.github.donttemptfate.fatesofa.ui.RecipeScreen;
import io.github.donttemptfate.fatesofa.ui.SettingsScreen;
import io.github.donttemptfate.fatesofa.ui.StorageScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Map;

/**
 * In-game smoke test (CI): the mod loads in a real client, every feature initialises without switching
 * itself off, and its screens and overlays draw. Screenshots go to build/run/clientGameTest/screenshots.
 */
@SuppressWarnings("UnstableApiUsage")
public class FatesOfaGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		System.setProperty("fatesofa.test", "true"); // the test world isn't SkyBlock
		check(Ofa.get() != null, "OFA didn't initialise");
		Ofa o = Ofa.get();
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			ctx.waitTicks(40);
			ctx.takeScreenshot("00_world");
			o.state.setOnHypixel(true);

			// settings pages
			ctx.runOnClient(mc -> mc.gui.setScreen(new SettingsScreen(null)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("01_settings_start");
			for (Category c : Category.values()) {
				if (Features.all().stream().noneMatch(f -> f.category == c)) continue;
				ctx.runOnClient(mc -> mc.gui.setScreen(new SettingsScreen(null, c)));
				ctx.waitTicks(5);
				ctx.takeScreenshot("02_settings_" + c.name().toLowerCase());
			}
			ctx.runOnClient(mc -> mc.gui.setScreen(new HudEditorScreen(null)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("03_hud_editor");

			// item data from GitHub (the CI runner can reach it)
			ctx.waitFor(mc -> o.repo.state() == RepoManager.State.READY, 1200);
			check(o.repo.items().size() > 1000, "item data too small: " + o.repo.items().size());
			FatesOfaClient.LOG.info("[gametest] item data: {}", o.repo.message());
			// prices without the network: fixed numbers
			o.prices.enabled = true;
			o.prices.ahEnabled = false;
			o.prices.load(Map.of("ENCHANTED_DIAMOND", new PriceTables.Bz(180, 170, 1, 1, 1, 1)),
					Map.of("ENCHANTED_DIAMOND", 1280.0), Map.of("HYPERION", new PriceTables.Ah(1.1e9, 1.2e9, 4, 1)), System.currentTimeMillis());

			ctx.runOnClient(mc -> mc.gui.setScreen(new RecipeScreen(null, "HYPERION", false)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("04_recipe_hyperion");
			ctx.runOnClient(mc -> mc.gui.setScreen(new RecipeScreen(null, "REFINED_MITHRIL", false)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("05_recipe_forge");
			ctx.runOnClient(mc -> mc.gui.setScreen(new RecipeScreen(null, "ENCHANTED_DIAMOND", true)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("06_used_in");

			// a chest with SkyBlock items: chest value, rarity backgrounds, item list beside it
			SimpleContainer chest = new SimpleContainer(27);
			chest.setItem(0, sb(Items.DIAMOND, "ENCHANTED_DIAMOND", "§aEnchanted Diamond", "§a§lUNCOMMON", 64));
			chest.setItem(1, sb(Items.IRON_SWORD, "HYPERION", "§6Hyperion", "§6§lLEGENDARY DUNGEON SWORD", 1));
			chest.setItem(2, sb(Items.DIAMOND, "ENCHANTED_DIAMOND", "§aEnchanted Diamond", "§a§lUNCOMMON", 10));
			ctx.runOnClient(mc -> mc.gui.setScreen(new ContainerScreen(ChestMenu.threeRows(0, mc.player.getInventory(), chest),
					mc.player.getInventory(), Component.literal("Large Chest"))));
			ctx.waitTicks(20);
			ctx.takeScreenshot("07_chest_value_item_list");

			// protection: lock the Hyperion, then a THROW click on it must be stopped
			ctx.runOnClient(mc -> {
				o.protection.toggleItem("hyperion", "HYPERION");
				boolean blocked = ProtectionFeature.blockClick(0, 1, 0, ContainerInput.THROW, mc.player);
				check(blocked, "dropping a locked item wasn't stopped");
				check(!ProtectionFeature.blockClick(0, 0, 0, ContainerInput.THROW, mc.player), "an unlocked item was stopped");
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("08_locked_item");

			// trackers fed with real-looking messages
			ctx.runOnClient(mc -> {
				var slayer = io.github.donttemptfate.fatesofa.features.SlayerFeature.INSTANCE;
				io.github.donttemptfate.fatesofa.game.ChatBus.inject(Component.literal("  SLAYER QUEST STARTED!"), false);
				io.github.donttemptfate.fatesofa.game.ChatBus.inject(Component.literal("RARE DROP! Revenant Viscera (+80% ✯ Magic Find)"), false);
				io.github.donttemptfate.fatesofa.game.ChatBus.inject(Component.literal("  SLAYER QUEST COMPLETE!"), false);
				check(slayer.tracker.kills().size() == 1, "slayer kill not counted");
			});
			// a waypoint a few blocks ahead, drawn in the world
			ctx.runOnClient(mc -> {
				mc.gui.setScreen(null);
				var p = mc.player.blockPosition();
				io.github.donttemptfate.fatesofa.features.WaypointFeature.INSTANCE.waypoints.add(new io.github.donttemptfate.fatesofa.waypoints.Waypoints.Waypoint(
						"Test spot", p.getX() + 4, p.getY(), p.getZ() + 4, 0x55FFFF, "TEST", 0));
				mc.player.setYRot(-45f);
				mc.player.setXRot(20f);
			});
			ctx.waitTicks(20);
			ctx.takeScreenshot("08b_waypoint");
			ctx.runOnClient(mc -> mc.gui.setScreen(new io.github.donttemptfate.fatesofa.ui.PlannerScreen(null)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("09a_planner");
			ctx.runOnClient(mc -> mc.gui.setScreen(new HudEditorScreen(null)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("09b_hud_editor_all_samples");

			// storage overview (empty and with a remembered page)
			ctx.runOnClient(mc -> mc.gui.setScreen(new StorageScreen(null, "")));
			ctx.waitTicks(10);
			ctx.takeScreenshot("09_storage_empty");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(5);

			// play recorder: game text is written with names hidden; what players type is not
			ctx.runOnClient(mc -> {
				io.github.donttemptfate.fatesofa.record.PlayRecorder.start();
				String me = mc.player.getName().getString();
				io.github.donttemptfate.fatesofa.game.ChatBus.inject(Component.literal("Party > [MVP+] " + me + ": secret party talk"), false);
				io.github.donttemptfate.fatesofa.game.ChatBus.inject(Component.literal("[287] ♲ [MVP+] Stranger: public chat"), false);
				io.github.donttemptfate.fatesofa.game.ChatBus.inject(Component.literal(me + " has obtained Wither Key!"), false);
				io.github.donttemptfate.fatesofa.game.ChatBus.inject(Component.literal("§71/5 Secrets"), true);
			});
			ctx.waitTicks(30);
			ctx.runOnClient(mc -> {
				io.github.donttemptfate.fatesofa.record.PlayRecorder.stop(true);
				try (var files = java.nio.file.Files.list(o.dir.resolve("recordings"))) {
					var f = files.filter(x -> x.toString().endsWith(".jsonl")).findFirst().orElseThrow();
					String text = java.nio.file.Files.readString(f);
					FatesOfaClient.LOG.info("[gametest] recording: {} bytes", text.length());
					check(text.contains("{me} has obtained Wither Key!"), "game chat missing from the recording");
					check(!text.contains("secret party talk") && !text.contains("public chat"), "player chat was recorded");
					check(!text.contains(mc.player.getName().getString()), "a player name was recorded");
					check(text.contains("\"k\":\"sidebar\"") || text.contains("\"k\":\"tab\"") || text.contains("\"k\":\"location\""), "no state lines recorded");
					check(text.contains("1/5 Secrets"), "action bar missing");
				} catch (java.io.IOException e) {
					throw new AssertionError(e);
				}
			});

			// secret waypoints: the room under the player is recognised from its blocks; learned secrets and a route are drawn
			ctx.runOnClient(mc -> {
				var sr = io.github.donttemptfate.fatesofa.features.SecretRoutes.INSTANCE;
				o.settings.set(sr.enabled, true);
				var p = mc.player.blockPosition();
				io.github.donttemptfate.fatesofa.rooms.RoomGrid.originX = p.getX() - 2 * 32 - 15;
				io.github.donttemptfate.fatesofa.rooms.RoomGrid.originZ = p.getZ() - 2 * 32 - 15;
				// the test world is flat: read the ground instead of dungeon heights
				io.github.donttemptfate.fatesofa.rooms.RoomFingerprint.yMin = p.getY() - 6;
				io.github.donttemptfate.fatesofa.rooms.RoomFingerprint.yMax = p.getY() + 4;
				sr.worldChanged();
				double x = p.getX() + 0.5, y = p.getY() + 0.5, z = p.getZ() + 0.5;
				sr.testLearn(io.github.donttemptfate.fatesofa.rooms.RoomStore.Kind.CHEST, x + 4, y, z + 6);
				sr.testLearn(io.github.donttemptfate.fatesofa.rooms.RoomStore.Kind.BAT, x - 3, y + 2, z + 8);
				sr.testLearn(io.github.donttemptfate.fatesofa.rooms.RoomStore.Kind.ESSENCE, x + 1, y, z + 11);
				sr.testRoute(List.of(new double[] {x, y - 0.5, z}, new double[] {x + 2, y - 0.5, z + 3}, new double[] {x + 4, y - 0.5, z + 6},
						new double[] {x - 3, y - 0.5, z + 8}, new double[] {x + 1, y - 0.5, z + 11}));
				check(sr.knownCells() >= 1, "no room recognised under the player");
				mc.player.setYRot(0f);
				mc.player.setXRot(30f);
			});
			ctx.waitTicks(20);
			ctx.takeScreenshot("10_secret_waypoints");
			ctx.runOnClient(mc -> io.github.donttemptfate.fatesofa.features.SecretRoutes.INSTANCE.status());

			for (Feature f : Features.all()) {
				check(!f.failed(), "feature switched itself off: " + f.id + " — " + f.errors().lastError());
			}
			FatesOfaClient.LOG.info("[gametest] {} features OK", Features.all().size());
		}
	}

	/** A stack that looks like a SkyBlock item: id (+uuid for unstackables), name, rarity line. */
	static ItemStack sb(Item item, String id, String name, String rarityLine, int count) {
		ItemStack st = new ItemStack(item, count);
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		if (count == 1) tag.putString("uuid", id.toLowerCase());
		st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		st.set(DataComponents.CUSTOM_NAME, io.github.donttemptfate.fates.common.text.Texts.fromLegacy(name));
		st.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("A test item"), io.github.donttemptfate.fates.common.text.Texts.fromLegacy(rarityLine))));
		return st;
	}

	static void check(boolean ok, String msg) {
		if (!ok) throw new AssertionError(msg);
	}

	static Minecraft mc() {
		return Minecraft.getInstance();
	}
}
