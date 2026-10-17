package io.github.donttemptfate.fatesofa;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fates OFA — One For All. Hypixel SkyBlock quality of life, display only. */
public final class FatesOfaClient implements ClientModInitializer {
	public static final String MOD_ID = "fatesofa";
	public static final Logger LOG = LoggerFactory.getLogger("Fates OFA");

	@Override
	public void onInitializeClient() {
		LOG.info("Fates OFA loaded");
	}
}
