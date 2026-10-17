package io.github.donttemptfate.fatesofa;

import io.github.donttemptfate.fatesofa.core.Features;
import io.github.donttemptfate.fatesofa.data.Http;
import io.github.donttemptfate.fatesofa.features.ChatFeature;
import io.github.donttemptfate.fatesofa.features.ContainerValue;
import io.github.donttemptfate.fatesofa.features.ItemListFeature;
import io.github.donttemptfate.fatesofa.features.PetTooltip;
import io.github.donttemptfate.fatesofa.features.RarityBackground;
import io.github.donttemptfate.fatesofa.features.StorageFeature;
import io.github.donttemptfate.fatesofa.features.ValueTooltip;
import io.github.donttemptfate.fatesofa.features.General;
import io.github.donttemptfate.fatesofa.features.LocationHud;
import io.github.donttemptfate.fatesofa.features.ProtectionFeature;
import io.github.donttemptfate.fatesofa.game.Game;
import io.github.donttemptfate.fatesofa.game.Hooks;
import io.github.donttemptfate.fatesofa.hud.Hud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fates OFA — One For All. Hypixel SkyBlock quality of life in one mod. Display only: it never clicks,
 * moves or plays for the player. Works without any account.
 */
public final class FatesOfaClient implements ClientModInitializer {
	public static final String MOD_ID = "fatesofa";
	public static final Logger LOG = LoggerFactory.getLogger("Fates OFA");

	@Override
	public void onInitializeClient() {
		String version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		Http.userAgent = "FatesOFA/" + version + " (https://github.com/DontTemptFate/Fates_QoL)";
		Ofa o = new Ofa(FabricLoader.getInstance().getConfigDir().resolve("fatesofa"), version, m -> LOG.info(m));
		Ofa.set(o);

		Features.onError((f, e, off) -> LOG.warn("[{}] {}{}", f.id, off ? "switched off after repeated errors: " : "", e.toString(), e),
				(f, last) -> Game.warn(f.name + " hit errors and is switched off for now (" + last + "). /ofa → Diagnostics."));
		registerFeatures();
		General.declare(o);
		Features.declareAll(o.settings);
		o.settings.load();
		General.apply(o);

		Hud.init(o.settings);
		Hooks.register(o);
		Features.initAll();
		o.repo.start();
		LOG.info("Fates OFA {} loaded: {} features", version, Features.all().size());
	}

	static void registerFeatures() {
		Features.add(new ProtectionFeature());
		Features.add(new ItemListFeature());
		Features.add(new ValueTooltip());
		Features.add(new PetTooltip());
		Features.add(new RarityBackground());
		Features.add(new ContainerValue());
		Features.add(new StorageFeature());
		Features.add(new ChatFeature());
		Features.add(new LocationHud());
	}
}
