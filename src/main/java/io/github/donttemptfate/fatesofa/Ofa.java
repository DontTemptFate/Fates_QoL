package io.github.donttemptfate.fatesofa;

import io.github.donttemptfate.fatesofa.chat.ChatFilter;
import io.github.donttemptfate.fatesofa.config.Settings;
import io.github.donttemptfate.fatesofa.data.PriceService;
import io.github.donttemptfate.fatesofa.data.RepoManager;
import io.github.donttemptfate.fatesofa.protect.Protection;
import io.github.donttemptfate.fatesofa.state.SkyBlockState;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** The shared services every feature uses. One instance, made at start-up. */
public final class Ofa {
	private static Ofa instance;

	public final Path dir;
	public final Settings settings;
	public final SkyBlockState state = new SkyBlockState();
	public final ExecutorService io;
	public final PriceService prices;
	public final RepoManager repo;
	public final Protection protection = new Protection();
	public final ChatFilter chat = new ChatFilter();
	public final io.github.donttemptfate.fatesofa.data.Patterns patterns = new io.github.donttemptfate.fatesofa.data.Patterns();
	public final String version;
	/** The current mayor and minister (Hypixel's public election data), shared by every feature. */
	public final io.github.donttemptfate.fatesofa.activity.Election election;
	/** Item values with upgrades, shared by tooltips, chest value, storage, protection and networth. */
	public final io.github.donttemptfate.fatesofa.items.Valuation valuation;
	/** "Is there a newer OFA?" (notice only). */
	public final io.github.donttemptfate.fatesofa.data.Updates updates = new io.github.donttemptfate.fatesofa.data.Updates();

	public Ofa(Path dir, String version, Consumer<String> log) {
		this.dir = dir;
		this.version = version;
		AtomicInteger n = new AtomicInteger();
		this.io = Executors.newFixedThreadPool(2, r -> {
			Thread t = new Thread(r, "Fates OFA io-" + n.incrementAndGet());
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY + 1);
			return t;
		});
		this.settings = new Settings(dir.resolve("settings.json"));
		this.prices = new PriceService(io, log);
		this.repo = new RepoManager(dir.resolve("repo"), io, log);
		this.election = new io.github.donttemptfate.fatesofa.activity.Election(io);
		this.valuation = new io.github.donttemptfate.fatesofa.items.Valuation(prices, repo);
	}

	public static Ofa get() {
		return instance;
	}

	static void set(Ofa o) {
		instance = o;
	}
}
