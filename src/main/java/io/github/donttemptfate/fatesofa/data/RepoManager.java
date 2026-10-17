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
	private final java.util.List<Runnable> onLoaded = new java.util.concurrent.CopyOnWriteArrayList<>();

	public volatile boolean autoUpdate = true;

	public RepoManager(Path root, Executor io, Consumer<String> log) {
		this.root = root;
		this.io = io;
		this.log = log;
	}

	/** Called (on a background thread) every time new data is loaded. */
	public void onLoaded(Runnable r) {
		onLoaded.add(r);
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

	private final java.util.Map<String, com.google.gson.JsonObject> constants = new java.util.concurrent.ConcurrentHashMap<>();

	/** A NEU repo constants file ({@code constants/<name>.json}), or null. Cached until the next load. */
	public com.google.gson.JsonObject constant(String name) {
		if (!name.matches("[a-z_]{1,40}")) return null;
		com.google.gson.JsonObject c = constants.get(name);
		if (c != null) return c;
		try {
			Path f = dir(NEU).resolve("constants").resolve(name + ".json");
			if (!Files.isRegularFile(f) || Files.size(f) > 16L * 1024 * 1024) return null;
			c = com.google.gson.JsonParser.parseString(Files.readString(f)).getAsJsonObject();
			constants.put(name, c);
			return c;
		} catch (Exception e) {
			return null;
		}
	}

	/** A SkyHanni-REPO constants file ({@code constants/<path>.json}), or null. Not cached (read once by callers). */
	public com.google.gson.JsonObject skyhanni(String path) {
		if (!path.matches("[A-Za-z_/]{1,60}")) return null;
		try {
			Path f = dir(SKYHANNI).resolve("constants").resolve(path + ".json");
			if (!Files.isRegularFile(f) || Files.size(f) > 32L * 1024 * 1024) return null;
			return com.google.gson.JsonParser.parseString(Files.readString(f)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
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
				if (state == State.LOADING) {
					state = State.NOT_LOADED;
					message = "item data not downloaded yet (online data is off)";
				}
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
				constants.clear();
				state = State.READY;
				message = items.size() + " items (" + shortSha(items.commit()) + ")";
				log.accept("item repo loaded: " + message + " in " + (System.currentTimeMillis() - t) + " ms");
				for (Runnable r : onLoaded) {
					try {
						r.run();
					} catch (RuntimeException e) {
						log.accept("data listener failed: " + e);
					}
				}
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
				changed = true;
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
