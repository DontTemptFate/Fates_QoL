package com.terraformersmc.modmenu.api;

/**
 * Compile-time stand-in for Mod Menu's API (MIT, TerraformersMC), written from its public documentation.
 * Only used to compile OFA; it is not packaged. When Mod Menu is installed, its own classes are used.
 */
public interface ModMenuApi {
	default ConfigScreenFactory<?> getModConfigScreenFactory() {
		return null;
	}
}
