package io.github.donttemptfate.fatesofa.test;

import io.github.donttemptfate.fatesofa.FatesOfaClient;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.core.Features;
import io.github.donttemptfate.fatesofa.hud.HudEditorScreen;
import io.github.donttemptfate.fatesofa.ui.SettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * In-game smoke test (CI): the mod loads in a real client, every feature initialises without
 * switching itself off, and the settings screen and HUD editor open and draw.
 */
@SuppressWarnings("UnstableApiUsage")
public class FatesOfaGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		check(Ofa.get() != null, "OFA didn't initialise");
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			ctx.waitTicks(40);
			ctx.takeScreenshot("00_world");

			ctx.runOnClient(mc -> mc.gui.setScreen(new SettingsScreen(null)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("01_settings_start");
			for (io.github.donttemptfate.fatesofa.core.Category c : io.github.donttemptfate.fatesofa.core.Category.values()) {
				if (Features.all().stream().noneMatch(f -> f.category == c)) continue;
				ctx.runOnClient(mc -> mc.gui.setScreen(new SettingsScreen(null, c)));
				ctx.waitTicks(5);
				ctx.takeScreenshot("02_settings_" + c.name().toLowerCase());
			}
			ctx.runOnClient(mc -> mc.gui.setScreen(new HudEditorScreen(null)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("03_hud_editor");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(5);

			for (Feature f : Features.all()) {
				check(!f.failed(), "feature switched itself off: " + f.id + " — " + f.errors().lastError());
			}
			FatesOfaClient.LOG.info("[gametest] {} features OK", Features.all().size());
		}
	}

	static void check(boolean ok, String msg) {
		if (!ok) throw new AssertionError(msg);
	}
}
