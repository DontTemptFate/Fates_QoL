package io.github.donttemptfate.fatesofa.storage;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remembers what's in each ender chest page and backpack the player opens (per profile), so it can be
 * looked through and searched anywhere. Only what the player has seen; nothing is fetched.
 */
public final class StorageCache {
	public record Page(String id, String title, long updatedAt, List<StoredItem> items) {
	}

	public record Hit(Page page, StoredItem item) {
	}

	static final Pattern ENDER = Pattern.compile("^Ender Chest \\((\\d+)/(\\d+)\\)");
	static final Pattern BACKPACK = Pattern.compile("^(.*Backpack.*?)\\s*\\(Slot #(\\d+)\\)");

	private final Path dir;
	private final Map<String, Page> pages = new LinkedHashMap<>();
	private String profile;
	private boolean dirty;
	private String pendingEmpty;

	public StorageCache(Path dir) {
		this.dir = dir;
	}

	/** Which stored page a window is, from its title; null when it isn't one. */
	public static String pageId(String title) {
		if (title == null) return null;
		Matcher m = ENDER.matcher(title.trim());
		if (m.find()) return "ender:" + m.group(1);
		m = BACKPACK.matcher(title.trim());
		if (m.find()) return "backpack:" + m.group(2);
		return null;
	}

	public static String label(String id) {
		if (id.startsWith("ender:")) return "Ender Chest " + id.substring(6);
		if (id.startsWith("backpack:")) return "Backpack " + id.substring(9);
		return id;
	}

	/** Switches to a profile's cache (loading it), saving the previous one. */
	public synchronized void profile(String name) {
		String p = name == null ? null : name.replaceAll("[^A-Za-z0-9_-]", "");
		if (p == null || p.isEmpty() || p.equals(profile)) return;
		save();
		profile = p;
		pages.clear();
		load();
	}

	public synchronized String profile() {
		return profile;
	}

	public synchronized void put(String id, String title, List<StoredItem> items, long now) {
		if (profile == null) return;
		Page old = pages.get(id);
		if (old != null && old.items().equals(items)) return;
		// a window that just opened may not have its items yet: an empty read only replaces a full page
		// when it is seen twice in a row
		if (items.isEmpty() && old != null && !old.items().isEmpty()) {
			if (!id.equals(pendingEmpty)) {
				pendingEmpty = id;
				return;
			}
		}
		pendingEmpty = null;
		pages.put(id, new Page(id, title, now, List.copyOf(items)));
		dirty = true;
	}

	public synchronized List<Page> pages() {
		List<Page> out = new ArrayList<>(pages.values());
		out.sort(Comparator.comparing((Page p) -> p.id().startsWith("ender:") ? 0 : 1).thenComparingInt(p -> num(p.id())));
		return out;
	}

	static int num(String id) {
		try {
			return Integer.parseInt(id.substring(id.indexOf(':') + 1));
		} catch (RuntimeException e) {
			return 0;
		}
	}

	public synchronized List<Hit> search(String query) {
		List<Hit> out = new ArrayList<>();
		String[] words = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
		for (Page p : pages()) {
			for (StoredItem it : p.items()) {
				String t = it.searchText();
				boolean all = true;
				for (String w : words) if (!t.contains(w)) all = false;
				if (all) out.add(new Hit(p, it));
			}
		}
		return out;
	}

	Path file() {
		return dir.resolve("storage-" + profile + ".json");
	}

	synchronized void load() {
		Path f = file();
		if (!Files.exists(f)) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("pages").entrySet()) {
				JsonObject o = e.getValue().getAsJsonObject();
				List<StoredItem> items = new ArrayList<>();
				for (JsonElement x : o.getAsJsonArray("items")) items.add(StoredItem.fromJson(x.getAsJsonObject()));
				pages.put(e.getKey(), new Page(e.getKey(), o.get("title").getAsString(), o.get("at").getAsLong(), List.copyOf(items)));
			}
		} catch (IOException | RuntimeException e) {
			pages.clear();
		}
	}

	public synchronized void save() {
		if (!dirty || profile == null) return;
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonObject ps = new JsonObject();
		for (Page p : pages.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("title", p.title());
			o.addProperty("at", p.updatedAt());
			JsonArray items = new JsonArray();
			for (StoredItem it : p.items()) items.add(it.toJson());
			o.add("items", items);
			ps.add(p.id(), o);
		}
		root.add("pages", ps);
		try {
			Files.createDirectories(dir);
			Path tmp = file().resolveSibling(file().getFileName() + ".tmp");
			Files.writeString(tmp, new Gson().toJson(root), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (java.nio.file.AtomicMoveNotSupportedException e) {
				Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING);
			}
			dirty = false;
		} catch (IOException ignored) {
			// try again next time
		}
	}
}
