package io.github.donttemptfate.fatesofa.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** In-game smoke test (CI): the mod loads in a real client and a world opens. */
@SuppressWarnings("UnstableApiUsage")
public class FatesOfaGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			ctx.waitTicks(40);
			ctx.takeScreenshot("00_world");
		}
	}
}
