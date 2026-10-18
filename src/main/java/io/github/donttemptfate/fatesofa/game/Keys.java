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
	/** Quick command 1–9: each sends the matching entry of the player's quick command list. */
	public static final KeyMapping[] quick = new KeyMapping[io.github.donttemptfate.fatesofa.quick.QuickCommands.HOTKEYS];

	/** Every OFA key binding, for the key list in OFA's settings. */
	public static java.util.List<KeyMapping> all() {
		java.util.List<KeyMapping> out = new java.util.ArrayList<>();
		for (KeyMapping k : new KeyMapping[] {settings, hudEditor, lock, itemList}) if (k != null) out.add(k);
		for (KeyMapping k : quick) if (k != null) out.add(k);
		return out;
	}

	/** Rebinds an OFA key from OFA's own settings (null = unbound) and saves the game's options. */
	public static void rebind(KeyMapping k, com.mojang.blaze3d.platform.InputConstants.Key key) {
		Minecraft mc = Minecraft.getInstance();
		k.setKey(key == null ? com.mojang.blaze3d.platform.InputConstants.UNKNOWN : key);
		KeyMapping.resetMapping();
		mc.options.save();
	}

	/** The shown name of a key stored by name ("R", "Left Shift"), or "None". */
	public static String keyName(String name) {
		if (name == null || name.isEmpty()) return "None";
		try {
			return com.mojang.blaze3d.platform.InputConstants.getKey(name).getDisplayName().getString();
		} catch (RuntimeException e) {
			return name;
		}
	}

	/** True while a key picked in OFA's settings is held. */
	public static boolean isDown(String name) {
		if (name == null || name.isEmpty()) return false;
		try {
			return Compat.isKeyDown(Minecraft.getInstance(), com.mojang.blaze3d.platform.InputConstants.getKey(name).getValue());
		} catch (RuntimeException e) {
			return false;
		}
	}

	static void register() {
		settings = KeyMappingHelper.registerKeyMapping(Compat.unboundKey("key.fatesofa.settings", CATEGORY));
		hudEditor = KeyMappingHelper.registerKeyMapping(Compat.unboundKey("key.fatesofa.hud_editor", CATEGORY));
		lock = KeyMappingHelper.registerKeyMapping(Compat.unboundKey("key.fatesofa.lock", CATEGORY));
		itemList = KeyMappingHelper.registerKeyMapping(Compat.unboundKey("key.fatesofa.item_list", CATEGORY));
		for (int i = 0; i < quick.length; i++) quick[i] = KeyMappingHelper.registerKeyMapping(Compat.unboundKey("key.fatesofa.quick_" + (i + 1), CATEGORY));
	}

	static void tick(Minecraft mc) {
		if (pressed(settings)) mc.gui.setScreen(new SettingsScreen(mc.gui.screen()));
		if (pressed(hudEditor)) mc.gui.setScreen(new HudEditorScreen(mc.gui.screen()));
		for (int i = 0; i < quick.length; i++) {
			if (pressed(quick[i]) && io.github.donttemptfate.fatesofa.features.QuickCommandsFeature.INSTANCE != null)
				io.github.donttemptfate.fatesofa.features.QuickCommandsFeature.INSTANCE.hotkey(i);
		}
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
