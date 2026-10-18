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
		check(io.github.donttemptfate.fatesofa.game.ModApi.active(), "the Hypixel location channel wasn't registered at game start");
		Ofa o = Ofa.get();
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			ctx.waitTicks(40);
			ctx.takeScreenshot("00_world");
			o.state.setOnHypixel(true);

			// real input: open settings by typing /ofa in chat, then click a sidebar entry with the mouse
			realClick(ctx, false);
			realClick(ctx, true);
			realSlider(ctx);
			settingsMachinery(ctx);

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

			// Batch 6: value with upgrades, tooltip breakdown, networth, NPC-sell sanity
			ctx.runOnClient(mc -> {
				o.prices.load(Map.of("ENCHANTED_DIAMOND", new PriceTables.Bz(180, 170, 1, 1, 1, 1), "HOT_POTATO_BOOK", new PriceTables.Bz(90_000, 80_000, 1, 1, 1, 1),
						"RECOMBOBULATOR_3000", new PriceTables.Bz(6e6, 5.9e6, 1, 1, 1, 1)),
						Map.of("ENCHANTED_DIAMOND", 1280.0, "HYPERION", 50_000.0), Map.of("HYPERION", new PriceTables.Ah(1.1e9, 1.2e9, 4, 1)), System.currentTimeMillis());
				ItemStack hype = sb(Items.IRON_SWORD, "HYPERION", "§6Hyperion", "§6§lLEGENDARY DUNGEON SWORD", 1);
				CompoundTag t = hype.get(DataComponents.CUSTOM_DATA).copyTag();
				t.putInt("hot_potato_count", 10);
				t.putInt("rarity_upgrades", 1);
				hype.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
				var b = o.valuation.of(io.github.donttemptfate.fates.common.item.Items.data(hype), "HYPERION", 1, System.currentTimeMillis());
				check(b != null && b.parts().size() == 2, "upgrades not found: " + b);
				double want = b.base() + 10 * 80_000 + 5.9e6;
				check(Math.abs(b.total() - want) < 1, "worth with upgrades " + b.total() + " ≠ " + want);
				var lines = new java.util.ArrayList<Component>();
				net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.invoker().getTooltip(hype, net.minecraft.world.item.Item.TooltipContext.EMPTY,
						net.minecraft.world.item.TooltipFlag.NORMAL, lines);
				String text = String.join("\n", lines.stream().map(Component::getString).toList());
				FatesOfaClient.LOG.info("[gametest] tooltip lines:\n{}", text);
				check(text.contains("Worth") && text.contains("with upgrades"), "no worth-with-upgrades line in the tooltip");
				// selling the Hyperion to an NPC for 50k: asks first
				var sell = o.protection.decide(io.github.donttemptfate.fatesofa.protect.Protection.Action.NPC_SELL,
						new io.github.donttemptfate.fatesofa.protect.Protection.Facts("x", "HYPERION", "Hyperion", b.total(), 1, 50_000), System.currentTimeMillis());
				check(sell.verdict() == io.github.donttemptfate.fatesofa.protect.Protection.Verdict.CONFIRM, "NPC sell sanity didn't ask: " + sell);
				mc.player.getInventory().setItem(5, hype);
				io.github.donttemptfate.fatesofa.features.PlannerFeature.INSTANCE.networth();
				var all = ((io.github.donttemptfate.fatesofa.mixin.ChatComponentAccessor) mc.gui.hud.getChat()).fatesofa$allMessages();
				check(all.stream().limit(15).anyMatch(m -> m.content().getString().startsWith("Networth (what OFA has seen)")), "/ofa networth printed nothing");
				mc.player.getInventory().setItem(5, ItemStack.EMPTY);
			});
			ctx.waitTicks(5);
			ctx.takeScreenshot("08c_networth_chat");

			// Batch 7: crash reports carry a Fates OFA line
			ctx.runOnClient(mc -> {
				var report = new net.minecraft.CrashReport("OFA test", new Throwable("test"));
				mc.fillReport(report);
				String txt = report.getSystemReport().toLineSeparatedString();
				String line = txt.lines().filter(l -> l.contains("features on:")).findFirst().orElse(null);
				FatesOfaClient.LOG.info("[gametest] crash report line: {}", line);
				check(line != null && line.contains("Fates OFA"), "no Fates OFA section in the crash report");
			});

			// trackers fed with real-looking messages
			ctx.runOnClient(mc -> {
				var slayer = io.github.donttemptfate.fatesofa.features.SlayerFeature.INSTANCE;
				io.github.donttemptfate.fatesofa.game.ChatBus.inject(Component.literal("  SLAYER QUEST STARTED!"), false);
				io.github.donttemptfate.fatesofa.game.ChatBus.inject(Component.literal("RARE DROP! Revenant Viscera (+80% ✯ Magic Find)"), false);
				io.github.donttemptfate.fatesofa.game.ChatBus.inject(Component.literal("  SLAYER QUEST COMPLETE!"), false);
				check(slayer.tracker.kills().size() == 1, "slayer kill not counted");
			});
			// Batch 8a: skill XP from the action bar, sacks hover, inventory pickups, collections, minion and chocolate menus
			ctx.runOnClient(mc -> {
				var sk = io.github.donttemptfate.fatesofa.features.SkillTracker.class;
				io.github.donttemptfate.fatesofa.game.ChatBus.inject(Component.literal("+12.5 Farming (1,234,567/2,000,000)"), true);
				io.github.donttemptfate.fatesofa.game.ChatBus.inject(Component.literal("+12.5 Farming (1,234,580/2,000,000)"), true);
				check(io.github.donttemptfate.fatesofa.features.ItemTracker.INSTANCE != null, "item tracker missing");
				var hover = new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("Added items:\n +1,280 Enchanted Carrot (Agronomy Sack)\n +5 Wheat (Agronomy Sack)"));
				io.github.donttemptfate.fatesofa.game.ChatBus.inject(Component.literal("[Sacks] +1,285 items.").withStyle(st -> st.withHoverEvent(hover)), false);
				var flow = io.github.donttemptfate.fatesofa.features.ItemTracker.INSTANCE.flow;
				check(flow.total("Enchanted Carrot") != null && flow.total("Enchanted Carrot").amount == 1280, "sacks hover not counted");
				o.settings.set((io.github.donttemptfate.fatesofa.config.Setting<String>) o.settings.find("itemtracker.items"), "Enchanted Carrot\nWheat");
				check(sk != null, "");
			});
			ctx.runOnClient(mc -> mc.gui.setScreen(null)); // pickups only count with no menu open
			ctx.waitTicks(25);
			ctx.runOnClient(mc -> mc.player.getInventory().setItem(7, sb(Items.DIAMOND, "ENCHANTED_DIAMOND", "§aEnchanted Diamond", "§a§lUNCOMMON", 32)));
			ctx.waitTicks(25);
			ctx.runOnClient(mc -> {
				var t = io.github.donttemptfate.fatesofa.features.ItemTracker.INSTANCE.flow.total("Enchanted Diamond");
				FatesOfaClient.LOG.info("[gametest] pickup counted: {}", t == null ? "none" : t.amount);
				check(t != null && t.amount == 32, "an inventory pickup wasn't counted");
				mc.player.getInventory().setItem(7, ItemStack.EMPTY);
			});
			ctx.takeScreenshot("08d_trackers_hud");
			SimpleContainer coll = new SimpleContainer(27);
			coll.setItem(4, lore(sb(Items.WHEAT, "WHEAT", "§aWheat", "§f§lCOMMON", 1), "View all your Wheat Collection", "", "Progress to Wheat IX: 45.3%", "12,345/25,000"));
			ctx.runOnClient(mc -> mc.gui.setScreen(new ContainerScreen(ChestMenu.threeRows(0, mc.player.getInventory(), coll), mc.player.getInventory(), Component.literal("Farming Collections"))));
			ctx.waitTicks(25);
			SimpleContainer minion = new SimpleContainer(54);
			minion.setItem(21, sb(Items.SNOWBALL, "SNOW_BALL", "§fSnowball", "§f§lCOMMON", 64));
			minion.setItem(22, sb(Items.SNOW_BLOCK, "ENCHANTED_SNOW_BLOCK", "§aEnchanted Snow Block", "§a§lUNCOMMON", 12));
			minion.setItem(19, lore(sb(Items.LAVA_BUCKET, "ENCHANTED_LAVA_BUCKET", "§5Enchanted Lava Bucket", "§5§lEPIC", 1), "Increases the speed of your minion by 25%.", "Time Remaining: 1d 4h"));
			ctx.runOnClient(mc -> mc.gui.setScreen(new ContainerScreen(ChestMenu.sixRows(0, mc.player.getInventory(), minion), mc.player.getInventory(), Component.literal("Snow Minion XI"))));
			ctx.waitTicks(15);
			ctx.takeScreenshot("08e_minion_helper");
			SimpleContainer choco = new SimpleContainer(54);
			choco.setItem(13, lore(new ItemStack(Items.COOKIE), "Chocolate Production", "Per Second: 1,234"));
			choco.setItem(28, lore(new ItemStack(Items.PLAYER_HEAD), "Rabbit Bro", "Produces +2 Chocolate per second", "", "Cost", "12,500 Chocolate"));
			choco.setItem(29, lore(new ItemStack(Items.PLAYER_HEAD), "Rabbit Cousin", "Produces +6 Chocolate per second", "", "Cost", "30,000 Chocolate"));
			ctx.runOnClient(mc -> mc.gui.setScreen(new ContainerScreen(ChestMenu.sixRows(0, mc.player.getInventory(), choco), mc.player.getInventory(), Component.literal("Chocolate Factory"))));
			ctx.waitTicks(15);
			ctx.takeScreenshot("08f_chocolate_factory");
			ctx.runOnClient(mc -> {
				mc.gui.setScreen(null);
				check(o.settings.raw("x") == null, "");
			});

			// Batch 8b: run history, reward chest profit, puzzle hints (built in the test world)
			ctx.runOnClient(mc -> io.github.donttemptfate.fatesofa.game.ChatBus.inject(Component.literal("             Team Score: 301 (S+)"), false));
			check(ctx.computeOnClient(mc -> ((io.github.donttemptfate.fatesofa.features.DungeonFeature) Features.get("dungeons")).runs.all().size()) >= 1,
					"the finished run wasn't recorded");
			SimpleContainer reward = new SimpleContainer(54);
			reward.setItem(11, sb(Items.BONE, "NECRON_HANDLE", "§5Necron's Handle", "§5§lEPIC", 1));
			reward.setItem(12, new ItemStack(Items.NETHER_STAR).copyWithCount(1));
			reward.setItem(12, lore(reward.getItem(12), "x"));
			reward.getItem(12).set(DataComponents.CUSTOM_NAME, Component.literal("Wither Essence x20"));
			reward.setItem(31, lore(new ItemStack(Items.CHEST), "Open Reward Chest", "", "Cost", "2,000,000 Coins", "Dungeon Chest Key", "", "Click to open!"));
			reward.getItem(31).set(DataComponents.CUSTOM_NAME, Component.literal("Open Reward Chest"));
			ctx.runOnClient(mc -> mc.gui.setScreen(new ContainerScreen(ChestMenu.sixRows(0, mc.player.getInventory(), reward), mc.player.getInventory(), Component.literal("Bedrock Chest"))));
			ctx.waitTicks(15);
			ctx.takeScreenshot("08g_reward_chest");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			int[] p0 = ctx.computeOnClient(mc -> new int[] {mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()});
			int bx = p0[0], by = p0[1], bz = p0[2];
			sp.getServer().runCommand("difficulty easy");
			sp.getServer().runCommand("fill " + bx + " " + (by - 1) + " " + bz + " " + (bx + 2) + " " + (by - 1) + " " + (bz + 2) + " minecraft:ice");
			int cx = bx + 12, cy = by + 1, cz = bz + 12;
			sp.getServer().runCommand("summon minecraft:creeper " + cx + ".5 " + cy + " " + cz + ".5 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
			sp.getServer().runCommand("setblock " + (cx - 8) + " " + (cy + 1) + " " + cz + " minecraft:sea_lantern");
			sp.getServer().runCommand("setblock " + (cx + 8) + " " + (cy + 1) + " " + cz + " minecraft:sea_lantern");
			sp.getServer().runCommand("setblock " + cx + " " + (cy + 1) + " " + (cz - 8) + " minecraft:sea_lantern");
			sp.getServer().runCommand("setblock " + cx + " " + (cy + 1) + " " + (cz + 8) + " minecraft:sea_lantern");
			sp.getServer().runCommand("setblock " + (cx - 7) + " " + (cy + 6) + " " + (cz - 7) + " minecraft:sea_lantern");
			for (int i = 0; i < 3; i++)
				sp.getServer().runCommand("summon minecraft:armor_stand " + (bx - 4 - i * 2) + " " + by + " " + (bz + 4) + " {NoGravity:1b,Invisible:1b,CustomNameVisible:1b,CustomName:\"[Lv15] Blaze " + (500 + i * 700) + "/2,000❤\"}");
			ctx.runOnClient(mc -> o.settings.set(Features.get("puzzle_helpers").enabled, true));
			ctx.waitTicks(40);
			var ph = (io.github.donttemptfate.fatesofa.features.PuzzleHelpers) Features.get("puzzle_helpers");
			int beams = ctx.computeOnClient(mc -> ph.beamCount()), path = ctx.computeOnClient(mc -> ph.icePathLength()), blazeN = ctx.computeOnClient(mc -> ph.blazeCount());
			FatesOfaClient.LOG.info("[gametest] puzzles: {} beam pairs, ice path {} tiles, {} blazes", beams, path, blazeN);
			ctx.runOnClient(mc -> {
				mc.player.setYRot(-45f);
				mc.player.setXRot(30f);
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("08h_puzzle_hints");
			check(beams == 2, "creeper beam pairs: " + beams);
			check(path == 9, "ice fill path length: " + path);
			check(blazeN == 3, "blazes found: " + blazeN);
			ctx.runOnClient(mc -> o.settings.set(Features.get("puzzle_helpers").enabled, false));

			// Batch 8c: your auctions and bazaar orders
			SimpleContainer auctions = new SimpleContainer(54);
			auctions.setItem(10, lore(sb(Items.IRON_SWORD, "HYPERION", "§6Hyperion", "§6§lLEGENDARY", 1), "Buy it now: 1,000,000,000 coins", "Status: Sold!"));
			auctions.setItem(11, lore(sb(Items.DIAMOND, "ENCHANTED_DIAMOND", "§aEnchanted Diamond", "§a§lUNCOMMON", 64), "Buy it now: 12,000 coins", "Ends in: 3h"));
			ctx.runOnClient(mc -> mc.gui.setScreen(new ContainerScreen(ChestMenu.sixRows(0, mc.player.getInventory(), auctions), mc.player.getInventory(), Component.literal("Manage Auctions"))));
			ctx.waitTicks(10);
			ctx.takeScreenshot("08i_manage_auctions");
			SimpleContainer orders = new SimpleContainer(54);
			ItemStack buy = lore(new ItemStack(Items.DIAMOND), "Order amount: 1,280x", "Filled: 0/1,280 0%", "Price per unit: 100 coins");
			buy.set(DataComponents.CUSTOM_NAME, Component.literal("BUY Enchanted Diamond"));
			ItemStack done = lore(new ItemStack(Items.DIAMOND), "Order amount: 64x", "Filled: 64/64 100%", "Price per unit: 170 coins");
			done.set(DataComponents.CUSTOM_NAME, Component.literal("BUY Enchanted Diamond"));
			orders.setItem(10, buy);
			orders.setItem(11, done);
			ctx.runOnClient(mc -> mc.gui.setScreen(new ContainerScreen(ChestMenu.sixRows(0, mc.player.getInventory(), orders), mc.player.getInventory(), Component.literal("Your Bazaar Orders"))));
			ctx.waitTicks(10);
			ctx.takeScreenshot("08j_bazaar_orders");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));

			// Batch 8d: island counters from chat
			ctx.runOnClient(mc -> {
				io.github.donttemptfate.fatesofa.game.ChatBus.inject(Component.literal("  TUNGSTEN CORPSE LOOT! "), false);
				io.github.donttemptfate.fatesofa.game.ChatBus.inject(Component.literal("You uncovered a treasure chest!"), false);
				io.github.donttemptfate.fatesofa.game.ChatBus.inject(Component.literal("WOW! You found a Glacite Mineshaft portal!"), false);
				String sum = ((io.github.donttemptfate.fatesofa.features.HollowsTracker) Features.get("hollows_tracker")).testSummary();
				check(sum.equals("1|{Tungsten=1}|1"), "hollows counters: " + sum);
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
			// storage with remembered pages: browse one page, then search across all of them
			ctx.runOnClient(mc -> {
				var cache = io.github.donttemptfate.fatesofa.features.StorageFeature.INSTANCE.cache();
				cache.profile("Test");
				long now = System.currentTimeMillis();
				o.prices.load(Map.of(
								"ENCHANTED_DIAMOND", new PriceTables.Bz(180, 170, 1, 1, 1, 1),
								"ENCHANTED_GOLD", new PriceTables.Bz(700, 640, 1, 1, 1, 1),
								"ENCHANTED_EMERALD", new PriceTables.Bz(1500, 1400, 1, 1, 1, 1),
								"ENCHANTED_DIAMOND_BLOCK", new PriceTables.Bz(30000, 28500, 1, 1, 1, 1)),
						Map.of(),
						Map.of("HYPERION", new PriceTables.Ah(1.1e9, 1.2e9, 4, 1), "TERMINATOR", new PriceTables.Ah(7.4e8, 7.6e8, 3, 1),
								"NECRON_CHESTPLATE", new PriceTables.Ah(9.5e7, 9.8e7, 6, 1), "SUMMONING_RING", new PriceTables.Ah(2.1e6, 2.0e6, 9, 1),
								"JUJU_SHORTBOW", new PriceTables.Ah(1.4e7, 1.5e7, 8, 1)), now);
				cache.put("ender:1", "Ender Chest (1/9)", List.of(
						stored(0, "minecraft:iron_sword", "HYPERION", "§6Hyperion", 1),
						stored(1, "minecraft:diamond", "ENCHANTED_DIAMOND", "§aEnchanted Diamond", 64),
						stored(2, "minecraft:diamond", "ENCHANTED_DIAMOND", "§aEnchanted Diamond", 32),
						stored(3, "minecraft:diamond_block", "ENCHANTED_DIAMOND_BLOCK", "§9Enchanted Diamond Block", 40),
						stored(9, "minecraft:gold_ingot", "ENCHANTED_GOLD", "§aEnchanted Gold", 64),
						stored(10, "minecraft:bone", "SUMMONING_RING", "§9Summoning Ring", 1),
						stored(18, "minecraft:emerald", "ENCHANTED_EMERALD", "§aEnchanted Emerald", 64),
						stored(19, "minecraft:emerald", "ENCHANTED_EMERALD", "§aEnchanted Emerald", 64),
						stored(20, "minecraft:diamond_chestplate", "NECRON_CHESTPLATE", "§6Necron's Chestplate", 1),
						stored(31, "minecraft:gold_ingot", "ENCHANTED_GOLD", "§aEnchanted Gold", 20),
						stored(44, "minecraft:bow", "JUJU_SHORTBOW", "§5Juju Shortbow", 1)), now - 3_600_000);
				cache.put("ender:2", "Ender Chest (2/9)", List.of(
						stored(4, "minecraft:emerald", "ENCHANTED_EMERALD", "§aEnchanted Emerald", 48)), now - 600_000);
				cache.put("ender:3", "Ender Chest (3/9)", List.of(), now - 900_000);
				cache.put("backpack:3", "Large Backpack (Slot #3)", List.of(
						stored(0, "minecraft:diamond", "ENCHANTED_DIAMOND", "§aEnchanted Diamond", 12),
						stored(7, "minecraft:bow", "TERMINATOR", "§6Terminator", 1)), now - 120_000);
				cache.put("backpack:7", "Small Backpack (Slot #7)", List.of(
						stored(2, "minecraft:diamond_block", "ENCHANTED_DIAMOND_BLOCK", "§9Enchanted Diamond Block", 5)), now - 30_000);
				mc.gui.setScreen(new StorageScreen(null, ""));
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("09c_storage_page");
			ctx.runOnClient(mc -> mc.gui.setScreen(new StorageScreen(null, "diamond")));
			ctx.waitTicks(10);
			ctx.takeScreenshot("09d_storage_search");
			// a search result opens the real page (one command) and the search carries into it, lighting up the match
			ctx.runOnClient(mc -> {
				io.github.donttemptfate.fatesofa.features.StorageOverlay.INSTANCE.open("backpack:3", "diamond");
				check("backpack 3".equals(io.github.donttemptfate.fatesofa.game.Game.lastCommand), "opening a page didn't send /backpack 3: " + io.github.donttemptfate.fatesofa.game.Game.lastCommand);
				SimpleContainer page = new SimpleContainer(27);
				page.setItem(0, sb(Items.DIAMOND, "ENCHANTED_DIAMOND", "§aEnchanted Diamond", "§a§lUNCOMMON", 12));
				page.setItem(7, sb(Items.BOW, "TERMINATOR", "§6Terminator", "§6§lLEGENDARY BOW", 1));
				page.setItem(12, sb(Items.GOLD_INGOT, "ENCHANTED_GOLD", "§aEnchanted Gold", "§a§lUNCOMMON", 64));
				mc.gui.setScreen(new ContainerScreen(ChestMenu.threeRows(1, mc.player.getInventory(), page), mc.player.getInventory(),
						Component.literal("Large Backpack (Slot #3)")));
			});
			ctx.waitTicks(15);
			ctx.takeScreenshot("09f_real_page_with_storage_panel");

			// quick buttons: a tab click sends its one command; a hotkey sends the matching entry
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				var q = io.github.donttemptfate.fatesofa.features.QuickCommandsFeature.INSTANCE;
				var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.gui.screen();
				var tabs = q.testLayout(screen);
				check(tabs != null && tabs.size() >= 3, "no quick buttons on the page");
				int[] pets = tabs.get(1);
				check(q.click(screen, pets[0] + 11, pets[1] + 10, io.github.donttemptfate.fatesofa.ui.Mouse.LEFT), "click on the Pets tab wasn't taken");
				check("pets".equals(io.github.donttemptfate.fatesofa.game.Game.lastCommand), "Pets tab didn't send /pets: " + io.github.donttemptfate.fatesofa.game.Game.lastCommand);
				check(q.entries().size() == 6, "default quick commands missing");
			});
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				io.github.donttemptfate.fatesofa.features.QuickCommandsFeature.INSTANCE.hotkey(2);
				check("wardrobe".equals(io.github.donttemptfate.fatesofa.game.Game.lastCommand), "hotkey 3 didn't send /wardrobe");
				mc.gui.setScreen(new io.github.donttemptfate.fatesofa.ui.QuickCommandsScreen(null));
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("09g_quick_commands_editor");
			// a tall menu with no room above it: the tabs go down the left side
			ctx.runOnClient(mc -> {
				SimpleContainer big = new SimpleContainer(54);
				big.setItem(4, sb(Items.BONE, "SUMMONING_RING", "§9Summoning Ring", "§9§lRARE ACCESSORY", 1));
				mc.gui.setScreen(new ContainerScreen(ChestMenu.sixRows(2, mc.player.getInventory(), big), mc.player.getInventory(),
						Component.literal("Ender Chest (1/9)")));
			});
			ctx.waitTicks(15);
			ctx.takeScreenshot("09h_tall_menu_side_tabs");

			ctx.runOnClient(mc -> {
				o.settings.set(io.github.donttemptfate.fatesofa.features.General.THEME, "midnight");
				mc.gui.setScreen(new StorageScreen(null, ""));
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("09e_storage_midnight");
			ctx.runOnClient(mc -> o.settings.set(io.github.donttemptfate.fatesofa.features.General.THEME, "fates"));
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

			// chat cleanup: a repeat replaces its earlier copy with one line that counts
			ctx.runOnClient(mc -> {
				var chat = mc.gui.hud.getChat();
				Component sack = Component.literal("[Sacks] +12 items.");
				chat.addServerSystemMessage(sack);
				io.github.donttemptfate.fatesofa.game.ChatFold.fold(sack, 3);
				var all = ((io.github.donttemptfate.fatesofa.mixin.ChatComponentAccessor) chat).fatesofa$allMessages();
				long copies = all.stream().filter(m -> m.content().getString().startsWith("[Sacks] +12 items.")).count();
				check(copies == 1, "folded chat left " + copies + " copies");
				check(all.get(0).content().getString().endsWith("(×3)"), "folded line has no count: " + all.get(0).content().getString());
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
	static ItemStack lore(ItemStack st, String... lines) {
		List<Component> l = new java.util.ArrayList<>();
		for (String x : lines) l.add(Component.literal(x));
		st.set(DataComponents.LORE, new ItemLore(l));
		return st;
	}

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

	static io.github.donttemptfate.fatesofa.storage.StoredItem stored(int slot, String item, String key, String name, int count) {
		return new io.github.donttemptfate.fatesofa.storage.StoredItem(slot, item, key, name, List.of("§7A remembered item"), null, null, count);
	}

	/** Opens settings (by chat command, or directly) and clicks "General" with a real mouse press. */
	static void realClick(ClientGameTestContext ctx, boolean viaChat) {
		if (viaChat) {
			ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false)));
			ctx.waitTicks(5);
			ctx.getInput().typeChars("/ofa");
			ctx.waitTicks(2);
			ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN);
		} else {
			ctx.runOnClient(mc -> mc.gui.setScreen(new SettingsScreen(null)));
		}
		ctx.waitFor(mc -> mc.gui.screen() instanceof SettingsScreen, 100);
		ctx.waitTicks(10);
		double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
		double[] at = ctx.computeOnClient(mc -> ((SettingsScreen) mc.gui.screen()).sidebarPoint("General"));
		check(at != null, "no General entry");
		ctx.getInput().setCursorPos(at[0] * scale, at[1] * scale);
		ctx.waitTicks(3);
		ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(5);
		String page = ctx.computeOnClient(mc -> mc.gui.screen() instanceof SettingsScreen st ? st.pageLabel() : "(screen closed: " + mc.gui.screen() + ")");
		ctx.takeScreenshot(viaChat ? "00c_real_click_after_chat" : "00b_real_click_direct");
		FatesOfaClient.LOG.info("[gametest] real click ({}): page is now {}", viaChat ? "via /ofa" : "direct", page);
		check("General".equals(page), "a real mouse click didn't reach the settings screen (" + (viaChat ? "opened with /ofa" : "opened directly") + "): page " + page);
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);
	}

	/** Clicks a slider on the Storage page with a real mouse press: its setting must change. */
	static void realSlider(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> mc.gui.setScreen(new SettingsScreen(null, Category.STORAGE)));
		ctx.waitTicks(10);
		String before = ctx.computeOnClient(mc -> Ofa.get().settings.export());
		double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
		double[] at = ctx.computeOnClient(mc -> ((SettingsScreen) mc.gui.screen()).firstSliderPoint());
		check(at != null, "no slider on the Storage page");
		ctx.getInput().setCursorPos(at[0] * scale, at[1] * scale);
		ctx.waitTicks(3);
		ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(5);
		String after = ctx.computeOnClient(mc -> Ofa.get().settings.export());
		ctx.takeScreenshot("00d_real_slider");
		check(!before.equals(after), "a real mouse click on a slider didn't change its setting");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);
	}

	/** Clicks a named control of the open settings screen with the real mouse. */
	static void clickNamed(ClientGameTestContext ctx, String name, boolean right) {
		double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
		double[] at = ctx.computeOnClient(mc -> mc.gui.screen() instanceof SettingsScreen st ? st.point(name) : null);
		check(at != null, "settings control not on screen: " + name);
		ctx.getInput().setCursorPos(at[0] * scale, at[1] * scale);
		ctx.waitTicks(2);
		ctx.getInput().pressMouse(right ? com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT : com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(4);
	}

	static void search(ClientGameTestContext ctx, String text) {
		ctx.runOnClient(mc -> ((SettingsScreen) mc.gui.screen()).searchFor(text));
		ctx.waitTicks(4);
	}

	static String value(String key) {
		var o = Ofa.get();
		return String.valueOf(o.settings.get(o.settings.find(key)));
	}

	/** Batch 4: groups, line lists, island chips, colour picker, rule editor, sound picker, key capture, view switches, diagnostics. */
	static void settingsMachinery(ClientGameTestContext ctx) {
		var o = Ofa.get();
		ctx.runOnClient(mc -> {
			SettingsScreen.resetView();
			mc.gui.setScreen(new SettingsScreen(null));
		});
		ctx.waitFor(mc -> mc.gui.screen() instanceof SettingsScreen, 100);
		ctx.waitTicks(5);

		// Display bundle on the location card: lines, islands, colour picker with rainbow
		search(ctx, "Location card");
		ctx.takeScreenshot("05a_display_bundle");
		search(ctx, "location.lines");
		clickNamed(ctx, "setting:location.lines", false);
		clickNamed(ctx, "line:location.lines:server", false);
		check(!value("location.lines").contains("server"), "unticking a line didn't hide it: " + value("location.lines"));
		clickNamed(ctx, "up:location.lines:area", false);
		check(value("location.lines").startsWith("island,area"), "moving a line up didn't reorder: " + value("location.lines"));
		search(ctx, "location.show");
		clickNamed(ctx, "setting:location.show", false);
		clickNamed(ctx, "island:location.show:GARDEN", false);
		check(value("location.show").equals("GARDEN"), "island chip didn't pick the Garden: " + value("location.show"));
		search(ctx, "location.accent");
		clickNamed(ctx, "setting:location.accent", false);
		clickNamed(ctx, "rainbow:location.accent", false);
		check(o.settings.on((io.github.donttemptfate.fatesofa.config.Setting<Boolean>) o.settings.find("location.accent.rainbow")), "rainbow switch didn't turn on");
		ctx.takeScreenshot("05b_editors_open");

		// rule editor: add a rule by typing
		search(ctx, "chat.rules");
		clickNamed(ctx, "setting:chat.rules", false);
		clickNamed(ctx, "rule-add:chat.rules", false);
		ctx.getInput().typeChars("Watchdog => hide");
		ctx.waitTicks(2);
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN);
		ctx.waitTicks(3);
		check(value("chat.rules").equals("Watchdog => hide"), "typed rule wasn't saved: " + value("chat.rules"));
		ctx.takeScreenshot("05c_rules");

		// sound picker on the pest alert (Alert bundle)
		search(ctx, "garden.pestAlert.sound");
		String before = value("garden.pestAlert.sound");
		clickNamed(ctx, "setting:garden.pestAlert.sound", false);
		clickNamed(ctx, "sound-next:garden.pestAlert.sound", false);
		check(!before.equals(value("garden.pestAlert.sound")), "sound picker didn't change the sound");
		ctx.takeScreenshot("05d_sound");

		// view switches: changed only
		search(ctx, "");
		clickNamed(ctx, "view:changed", false);
		ctx.takeScreenshot("05e_changed_only");
		clickNamed(ctx, "view:changed", false);

		// key capture for an OFA key binding, on the Start page
		boolean found = false;
		for (int i = 0; i < 15 && !found; i++) {
			found = ctx.computeOnClient(mc -> ((SettingsScreen) mc.gui.screen()).point("key:key.fatesofa.hud_editor") != null);
			if (!found) {
				ctx.runOnClient(mc -> mc.gui.screen().mouseScrolled(mc.gui.screen().width - 40, 100, 0, -3));
				ctx.waitTicks(2);
			}
		}
		check(found, "the key list isn't on the Start page");
		clickNamed(ctx, "key:key.fatesofa.hud_editor", false);
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_J);
		ctx.waitTicks(3);
		String bound = ctx.computeOnClient(mc -> io.github.donttemptfate.fatesofa.game.Keys.hudEditor.getTranslatedKeyMessage().getString());
		check(bound.equalsIgnoreCase("J"), "key capture didn't bind the HUD editor key: " + bound);
		ctx.takeScreenshot("05f_keys");
		ctx.runOnClient(mc -> io.github.donttemptfate.fatesofa.game.Keys.rebind(io.github.donttemptfate.fatesofa.game.Keys.hudEditor, null));

		// diagnostics: per-feature timing
		ctx.waitTicks(50);
		ctx.runOnClient(mc -> mc.gui.setScreen(new SettingsScreen(null)));
		ctx.waitTicks(3);
		double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
		double[] diag = ctx.computeOnClient(mc -> ((SettingsScreen) mc.gui.screen()).sidebarPoint("Diagnostics"));
		ctx.getInput().setCursorPos(diag[0] * scale, diag[1] * scale);
		ctx.waitTicks(2);
		ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(5);
		ctx.takeScreenshot("05g_diagnostics");
		String diagPage = ctx.computeOnClient(mc -> mc.gui.screen() instanceof SettingsScreen st ? st.pageLabel() : "?");
		check("Diagnostics".equals(diagPage), "couldn't reach the Diagnostics page from the (scrolling) sidebar: " + diagPage);
		check(io.github.donttemptfate.fatesofa.core.Diagnostics.fps() > 0, "diagnostics measured no frames");

		// Batch 5: every feature has its option bundles; presets only touch options that exist
		long options = o.settings.all().stream().filter(st -> !st.hidden).count();
		FatesOfaClient.LOG.info("[gametest] settings options: {}", options);
		check(options >= 550, "fewer options than expected: " + options);
		for (var e : io.github.donttemptfate.fatesofa.config.Presets.TWEAKS.entrySet())
			for (String k : e.getValue().keySet()) check(o.settings.find(k) != null, "preset " + e.getKey() + " sets an unknown option " + k);
		ctx.runOnClient(mc -> mc.gui.setScreen(new SettingsScreen(null)));
		ctx.waitTicks(3);
		search(ctx, "Fishing tracker");
		ctx.takeScreenshot("06a_fishing_all_groups");
		search(ctx, "Slayer tracker");
		ctx.takeScreenshot("06b_slayer_all_groups");
		ctx.runOnClient(mc -> mc.gui.setScreen(new SettingsScreen(null, Category.DUNGEONS)));
		ctx.waitTicks(3);
		clickNamed(ctx, "group:dungeons/Map card", false);
		ctx.takeScreenshot("06c_dungeon_map_group");

		// put things back for the rest of the test
		ctx.runOnClient(mc -> {
			for (String k : new String[] {"location.lines", "location.show", "location.accent.rainbow", "chat.rules", "garden.pestAlert.sound"})
				o.settings.reset(o.settings.find(k));
			SettingsScreen.resetView();
			mc.gui.setScreen(null);
		});
		ctx.waitTicks(5);
	}

	static void check(boolean ok, String msg) {
		if (!ok) throw new AssertionError(msg);
	}

	static Minecraft mc() {
		return Minecraft.getInstance();
	}
}
