package com.terraformersmc.modmenu.api;

import net.minecraft.client.gui.screens.Screen;

/** Compile-time stand-in for Mod Menu's API (see ModMenuApi); not packaged. */
@FunctionalInterface
public interface ConfigScreenFactory<S extends Screen> {
	S create(Screen parent);
}
