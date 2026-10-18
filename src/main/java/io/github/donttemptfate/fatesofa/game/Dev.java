package io.github.donttemptfate.fatesofa.game;

import net.fabricmc.loader.api.FabricLoader;

/** The in-game test switch. Only works in a development run, never in a released jar a player installs. */
public final class Dev {
	private Dev() {}

	private static final boolean DEV = FabricLoader.getInstance().isDevelopmentEnvironment();

	/** True only while the automated in-game test runs (development environment + the test property). */
	public static boolean test() {
		return DEV && Boolean.getBoolean("fatesofa.test");
	}
}
