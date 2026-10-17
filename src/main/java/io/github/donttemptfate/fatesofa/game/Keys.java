package io.github.donttemptfate.fatesofa.game;

import io.github.donttemptfate.fates.common.compat.Compat;
import io.github.donttemptfate.fatesofa.FatesOfaClient;
import io.github.donttemptfate.fatesofa.hud.HudEditorScreen;
import io.github.donttemptfate.fatesofa.ui.SettingsScreen;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Key bindings (Options → Controls → Key Binds → Fates OFA). Unbound by default so nothing clashes. */
public final class Keys {
	private Keys() {}

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(FatesOfaClient.MOD_ID, "main"));

	public static KeyMapping settings;
	public static KeyMapping hudEditor;
	public static KeyMapping lock;
	public static KeyMapping itemList;

	static void register() {
		settings = KeyMappingHelper.registerKeyMapping(Compat.unboundKey("key.fatesofa.settings", CATEGORY));
		hudEditor = KeyMappingHelper.registerKeyMapping(Compat.unboundKey("key.fatesofa.hud_editor", CATEGORY));
		lock = KeyMappingHelper.registerKeyMapping(Compat.unboundKey("key.fatesofa.lock", CATEGORY));
		itemList = KeyMappingHelper.registerKeyMapping(Compat.unboundKey("key.fatesofa.item_list", CATEGORY));
	}

	static void tick(Minecraft mc) {
		if (pressed(settings)) mc.gui.setScreen(new SettingsScreen(mc.gui.screen()));
		if (pressed(hudEditor)) mc.gui.setScreen(new HudEditorScreen(mc.gui.screen()));
	}

	/** One press = one action; held or queued presses are dropped. */
	static boolean pressed(KeyMapping k) {
		if (k == null || !k.consumeClick()) return false;
		while (k.consumeClick()) {
			// drain
		}
		return true;
	}
}
