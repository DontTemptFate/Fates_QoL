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

			// storage overview (empty and with a remembered page)
			ctx.runOnClient(mc -> mc.gui.setScreen(new StorageScreen(null, "")));
			ctx.waitTicks(10);
			ctx.takeScreenshot("09_storage_empty");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(5);

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
