package io.github.donttemptfate.fatesofa.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.donttemptfate.fatesofa.ui.SettingsScreen;

/** Mod Menu's "Configure" button opens OFA's settings. Only loaded when Mod Menu is installed. */
public final class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SettingsScreen::new;
	}
}
