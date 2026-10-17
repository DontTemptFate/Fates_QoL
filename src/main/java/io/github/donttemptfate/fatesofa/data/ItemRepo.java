package io.github.donttemptfate.fatesofa.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Every item of the NEU repo, loaded once (off the render thread) into an immutable index, plus a
 * reverse index "which recipes use this item". Built whole and swapped in, never half-filled.
 */
public final class ItemRepo {
	private final Map<String, RepoItem> items;
	private final Map<String, List<Recipe>> usedIn;
	private final String commit;

	private ItemRepo(Map<String, RepoItem> items, Map<String, List<Recipe>> usedIn, String commit) {
		this.items = items;
		this.usedIn = usedIn;
		this.commit = commit;
	}

	public static final ItemRepo EMPTY = new ItemRepo(Map.of(), Map.of(), null);

	/** Loads {@code dir/items/*.json}. Broken files are skipped and counted. */
	public static ItemRepo load(Path dir) throws IOException {
		Path itemsDir = dir.resolve("items");
		Map<String, RepoItem> items = new HashMap<>();
		Map<String, List<Recipe>> usedIn = new HashMap<>();
		try (Stream<Path> files = Files.list(itemsDir)) {
			for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
				try {
					JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
					RepoItem it = parse(o);
					if (it == null) continue;
					items.put(it.key(), it);
					for (Recipe r : it.recipes()) {
						for (Ingredient in : r.inputs()) usedIn.computeIfAbsent(in.key(), k -> new ArrayList<>()).add(r);
					}
				} catch (IOException | RuntimeException e) {
					// skip one broken file
				}
			}
		}
		Map<String, List<Recipe>> frozen = new HashMap<>();
		usedIn.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
		return new ItemRepo(Collections.unmodifiableMap(items), Collections.unmodifiableMap(frozen), GitRepo.commitOf(dir));
	}

	static RepoItem parse(JsonObject o) {
		String key = str(o, "internalname");
		if (key == null || key.isEmpty()) return null;
		List<String> lore = new ArrayList<>();
		JsonElement l = o.get("lore");
		if (l != null && l.isJsonArray()) for (JsonElement e : (JsonArray) l) if (e.isJsonPrimitive()) lore.add(e.getAsString());
		String wiki = null;
		JsonElement info = o.get("info");
		if (info != null && info.isJsonArray()) {
			for (JsonElement e : (JsonArray) info) {
				String s = e.isJsonPrimitive() ? e.getAsString() : "";
				if (s.startsWith("https://")) {
					wiki = s;
					break;
				}
			}
		}
		int damage = 0;
		try {
			damage = o.has("damage") ? o.get("damage").getAsInt() : 0;
		} catch (RuntimeException ignored) {
			// keep 0
		}
		return new RepoItem(key, def(str(o, "displayname"), key), def(str(o, "itemid"), "minecraft:barrier"), damage,
				List.copyOf(lore), Recipe.fromItem(key, o), def(str(o, "crafttext"), ""), wiki, str(o, "nbttag"));
	}

	static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	static String def(String s, String d) {
		return s == null ? d : s;
	}

	public RepoItem get(String key) {
		return key == null ? null : items.get(key);
	}

	public Collection<RepoItem> all() {
		return items.values();
	}

	public int size() {
		return items.size();
	}

	public List<Recipe> usedIn(String key) {
		return usedIn.getOrDefault(key, List.of());
	}

	public String commit() {
		return commit;
	}
}
