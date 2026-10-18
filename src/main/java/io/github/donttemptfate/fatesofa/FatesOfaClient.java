package io.github.donttemptfate.fatesofa;

import io.github.donttemptfate.fatesofa.core.Features;
import io.github.donttemptfate.fatesofa.data.Http;
import io.github.donttemptfate.fatesofa.features.*;
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

		o.repo.onLoaded(() -> o.patterns.load(o.repo.skyhanni("regexesModern")));
		Hud.init(o.settings);
		Hooks.register(o);
		io.github.donttemptfate.fatesofa.record.PlayRecorder.register();
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
		Features.add(new StorageOverlay());
		Features.add(new EventTimers());
		Features.add(new GardenFeature());
		Features.add(new VisitorHelper());
		Features.add(new MiningFeature());
		Features.add(new FishingFeature());
		Features.add(new SlayerFeature());
		Features.add(new StatBars());
		Features.add(new RiftFeature());
		Features.add(new DungeonFeature());
		Features.add(new DungeonHelpers());
		Features.add(new SecretRoutes());
		Features.add(new KuudraFeature());
		Features.add(new DianaFeature());
		Features.add(new HoppityFeature());
		Features.add(new BingoFeature());
		Features.add(new PlannerFeature());
		Features.add(new WaypointFeature());
		Features.add(new QuickCommandsFeature());
		Features.add(new ChatFeature());
		Features.add(new LocationHud());
		Features.add(new SkillTracker());
		Features.add(new ItemTracker());
		Features.add(new CollectionsFeature());
		Features.add(new MinionHelper());
		Features.add(new ChocolateFactoryHelper());
		Features.add(new RewardChests());
		Features.add(new PuzzleHelpers());
	}
}
