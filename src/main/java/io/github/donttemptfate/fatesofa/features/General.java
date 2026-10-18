package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatescommon.ui.Theme;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.data.PriceService;

import java.util.List;

/** Mod-wide settings that don't belong to one feature: look, network, prices. */
public final class General {
	private General() {}

	public static final Setting<Boolean> ONBOARDED = Setting.bool("general.onboarded", "Setup done", false).build();
	public static final Setting<String> THEME = Setting.choice("general.theme", "Theme (shared with Fates Flipper)", "fates", Theme.ids())
			.help("Colours of every OFA card, panel and screen. Shared with Fates Flipper: changing it here changes it there too (config/fatescommon.json).").keywords("look colour color style purple").build();
	public static final Setting<Boolean> CHAKRA = Setting.bool("general.chakra", "Chakra Petch font (shared with Fates Flipper)", true)
			.help("Big numbers and names in the Chakra Petch font; off = Minecraft's font everywhere. Shared with Fates Flipper, like the theme.").keywords("font text").build();
	public static final Setting<Boolean> NETWORK = Setting.bool("general.network", "Online data", true)
			.help("Download item data (GitHub) and prices (Hypixel, fatesflipper.com). Off = no internet use at all.")
			.keywords("internet offline download").build();
	public static final Setting<Boolean> AH_PRICES = Setting.bool("general.ahPrices", "Auction-house values", true)
			.help("Lowest BIN / median from the public fatesflipper.com price file (no login).").keywords("price lbin bin").build();
	public static final Setting<String> BAZAAR_MODE = Setting.choice("general.bazaarMode", "Bazaar value", "instant_sell", List.of("instant_sell", "sell_offer"))
			.help("Value bazaar items at what instant-selling pays, or at the sell-offer price.").keywords("price bz").build();
	public static final Setting<String> AH_MODE = Setting.choice("general.ahMode", "Auction value", "plain_copy", List.of("plain_copy", "lowest_bin", "median"))
			.help("plain_copy: what a plain copy sells for (recommended; upgraded copies don't inflate it). lowest_bin: the cheapest listing now. median: a typical sale, upgrades included.").keywords("price lbin").build();
	public static final Setting<String> REPO_EVERY = Setting.choice("general.repoEvery", "Check for new item data", "daily", List.of("daily", "weekly", "manual"))
			.help("How often OFA looks for newer item and recipe data on GitHub (a download of a few MB when there is some). manual: only /ofa repo update.")
			.keywords("repo neu update download").build();
	public static final Setting<Boolean> REPO_AUTO = Setting.bool("general.repoAuto", "Update item data automatically", true)
			.help("Check GitHub for newer item data once an hour.").keywords("neu repo").build();

	public static final Setting<Boolean> UPDATES = Setting.bool("general.updates", "Tell me when a new version is out", true)
			.help("Checks the OFA GitHub page once per game start and says so in chat. Nothing is downloaded or installed.").keywords("update version release").build();
	public static final Setting<Boolean> BETAS = Setting.bool("general.betas", "Include test (beta) versions", true)
			.help("Off: only full releases are announced.").keywords("update beta").build();
	public static final Setting<Boolean> ADVANCED = Setting.bool("general.advanced", "Show advanced settings", false)
			.help("Lists every option of every feature. Off: the less common ones stay behind \"+N advanced\" on each feature.").build();

	public static List<Setting<?>> lookSettings() {
		return List.of(THEME, CHAKRA);
	}

	public static List<Setting<?>> dataSettings() {
		return List.of(NETWORK, AH_PRICES, BAZAAR_MODE, AH_MODE, REPO_AUTO, REPO_EVERY, UPDATES, BETAS);
	}

	public static List<Setting<?>> globals() {
		return List.of(THEME, CHAKRA, NETWORK, AH_PRICES, BAZAAR_MODE, AH_MODE, REPO_AUTO, REPO_EVERY, UPDATES, BETAS);
	}

	public static void declare(Ofa o) {
		o.settings.declare(ONBOARDED);
		o.settings.declare(ADVANCED);
		for (Setting<?> s : globals()) o.settings.declare(s);
		sharedLookAtStart(o);
		apply(o);
		o.settings.onChange(k -> {
			if (k.startsWith("general.")) apply(o);
		});
	}

	/**
	 * Font and theme live in Fates Common's shared file (config/fatescommon.json), so OFA and Fates Flipper
	 * always agree. First start with the shared file: OFA's own earlier choice is carried over once. After
	 * that, the shared file is the truth and OFA's two options only show and change it.
	 */
	static void sharedLookAtStart(Ofa o) {
		if (!io.github.donttemptfate.fatescommon.Settings.saved()) {
			io.github.donttemptfate.fatescommon.Settings.setTheme(Theme.byId(o.settings.get(THEME)));
			io.github.donttemptfate.fatescommon.Settings.setChakra(o.settings.on(CHAKRA));
		} else {
			pullSharedLook(o);
		}
	}

	private static boolean pulling;

	/** Shows the shared font and theme in OFA's options (Fates Flipper may have changed them). */
	public static void pullSharedLook(Ofa o) {
		pulling = true;
		try {
			String id = io.github.donttemptfate.fatescommon.Settings.theme().id();
			if (!id.equals(o.settings.get(THEME))) o.settings.set(THEME, id);
			boolean chakra = io.github.donttemptfate.fatescommon.Settings.chakra();
			if (chakra != o.settings.on(CHAKRA)) o.settings.set(CHAKRA, chakra);
		} finally {
			pulling = false;
		}
	}

	public static void apply(Ofa o) {
		// the player's choice goes to the shared settings, which apply it to the look at once and save it;
		// never Ui.theme / Ui.useChakra directly, or the two mods would disagree until a restart
		if (!pulling) {
			Theme t = Theme.byId(o.settings.get(THEME));
			if (!t.id().equals(io.github.donttemptfate.fatescommon.Settings.theme().id())) io.github.donttemptfate.fatescommon.Settings.setTheme(t);
			boolean chakra = o.settings.on(CHAKRA);
			if (chakra != io.github.donttemptfate.fatescommon.Settings.chakra()) io.github.donttemptfate.fatescommon.Settings.setChakra(chakra);
		}
		boolean net = o.settings.on(NETWORK);
		o.prices.enabled = net;
		o.prices.ahEnabled = net && o.settings.on(AH_PRICES);
		o.prices.bazaarMode = "sell_offer".equals(o.settings.get(BAZAAR_MODE)) ? PriceService.BazaarMode.SELL_OFFER : PriceService.BazaarMode.INSTANT_SELL;
		String am = o.settings.get(AH_MODE);
		o.prices.ahMode = "median".equals(am) ? PriceService.AhMode.MEDIAN : "lowest_bin".equals(am) ? PriceService.AhMode.LOWEST_BIN : PriceService.AhMode.PLAIN;
		o.election.enabled = net;
		String every = o.settings.get(REPO_EVERY);
		o.repo.autoUpdate = net && o.settings.on(REPO_AUTO) && !"manual".equals(every);
		o.repo.checkEvery = "weekly".equals(every) ? 7 * 86_400_000L : 86_400_000L;
	}
}
