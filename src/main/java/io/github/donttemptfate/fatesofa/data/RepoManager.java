package io.github.donttemptfate.fatesofa.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Keeps local copies of the NotEnoughUpdates-REPO (items, recipes, constants) and SkyHanni-REPO
 * (constants), both MIT. On start the last copy is loaded at once (so everything works offline), then
 * the newest commit is checked and downloaded in the background when it changed. Checked at most once
 * an hour. Nothing in the repos is ever executed.
 */
public final class RepoManager {
	public enum State { NOT_LOADED, LOADING, READY, OFFLINE, FAILED }

	public static final GitRepo NEU = new GitRepo("NotEnoughUpdates", "NotEnoughUpdates-REPO", "master",
			p -> p.startsWith("items/") || p.startsWith("constants/") || p.equals("LICENSE"));
	public static final GitRepo SKYHANNI = new GitRepo("hannibal002", "SkyHanni-REPO", "main",
			p -> p.startsWith("constants/") || p.equals("LICENSE"));

	static final long CHECK_EVERY = 3_600_000L;

	private final Path root;
	private final Executor io;
	private final Consumer<String> log;
	private final AtomicBoolean busy = new AtomicBoolean();
	private volatile ItemRepo items = ItemRepo.EMPTY;
	private volatile State state = State.NOT_LOADED;
	private volatile String message = "";
	private volatile long lastCheck;
	private volatile Runnable onLoaded = () -> {
	};

	public volatile boolean autoUpdate = true;

	public RepoManager(Path root, Executor io, Consumer<String> log) {
		this.root = root;
		this.io = io;
		this.log = log;
	}

	public void onLoaded(Runnable r) {
		this.onLoaded = r;
	}

	public ItemRepo items() {
		return items;
	}

	public State state() {
		return state;
	}

	public String message() {
		return message;
	}

	public Path dir(GitRepo r) {
		return root.resolve(r.name);
	}

	/** Loads what is on disk, then checks for updates (all in the background). */
	public void start() {
		if (!busy.compareAndSet(false, true)) return;
		state = State.LOADING;
		io.execute(() -> {
			try {
				loadLocal();
				if (autoUpdate) update(false);
			} finally {
				busy.set(false);
			}
		});
	}

	/** Checks for a newer commit now (the /ofa repo update command). */
	public void checkNow() {
		if (!busy.compareAndSet(false, true)) return;
		io.execute(() -> {
			try {
				update(true);
			} finally {
				busy.set(false);
			}
		});
	}

	/** Hourly check; cheap to call often. */
	public void tick(long now) {
		if (autoUpdate && now - lastCheck > CHECK_EVERY && state != State.LOADING && !busy.get()) {
			lastCheck = now;
			checkNow();
		}
	}

	void loadLocal() {
		Path neu = dir(NEU);
		if (Files.isDirectory(neu.resolve("items"))) {
			try {
				long t = System.currentTimeMillis();
				items = ItemRepo.load(neu);
				state = State.READY;
				message = items.size() + " items (" + shortSha(items.commit()) + ")";
				log.accept("item repo loaded: " + message + " in " + (System.currentTimeMillis() - t) + " ms");
				onLoaded.run();
			} catch (Exception e) {
				log.accept("item repo load failed: " + e);
				state = State.FAILED;
				message = "couldn't read the saved item data";
			}
		}
	}

	void update(boolean manual) {
		lastCheck = System.currentTimeMillis();
		boolean changed = false;
		for (GitRepo r : new GitRepo[] {NEU, SKYHANNI}) {
			try {
				String latest = r.latestCommit();
				String have = GitRepo.commitOf(dir(r));
				if (latest.equals(have)) continue;
				log.accept(r.label() + ": downloading " + shortSha(latest));
				r.download(latest, dir(r));
				if (r == NEU) changed = true;
			} catch (Exception e) {
				log.accept(r.label() + ": update failed: " + e);
				if (items.size() == 0) {
					state = State.OFFLINE;
					message = "item data not downloaded yet (no connection to GitHub)";
				}
			}
		}
		if (changed || items.size() == 0) loadLocal();
		else if (manual) message = items.size() + " items (" + shortSha(items.commit()) + "), up to date";
	}

	static String shortSha(String sha) {
		return sha == null ? "?" : sha.substring(0, Math.min(7, sha.length()));
	}
}
