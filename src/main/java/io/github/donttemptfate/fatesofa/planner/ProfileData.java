package io.github.donttemptfate.fatesofa.planner;

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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** What OFA has read from your own menus, per profile: accessories in the bag, skill levels. */
public final class ProfileData {
	private final Path dir;
	private String profile;
	private final Set<String> accessories = new LinkedHashSet<>();
	private final Map<String, Integer> skills = new LinkedHashMap<>();
	private long accessoriesAt, skillsAt;
	private boolean dirty;

	public ProfileData(Path dir) {
		this.dir = dir;
	}

	public synchronized void profile(String name) {
		String p = name == null ? null : name.replaceAll("[^A-Za-z0-9_-]", "");
		if (p == null || p.isEmpty() || p.equals(profile)) return;
		save();
		profile = p;
		accessories.clear();
		skills.clear();
		load();
	}

	public synchronized String profile() {
		return profile;
	}

	/** One accessory bag page: adds its accessories (pages are read one at a time). */
	public synchronized void addAccessories(Set<String> keys, boolean firstPage, long now) {
		if (firstPage) accessories.clear();
		if (accessories.addAll(keys) || firstPage) dirty = true;
		accessoriesAt = now;
	}

	public synchronized Set<String> accessories() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(accessories));
	}

	public synchronized long accessoriesAt() {
		return accessoriesAt;
	}

	public synchronized void skill(String name, int level, long now) {
		Integer old = skills.put(name, level);
		if (old == null || old != level) dirty = true;
		skillsAt = now;
	}

	public synchronized Map<String, Integer> skills() {
		return new LinkedHashMap<>(skills);
	}

	Path file() {
		return dir.resolve("profile-" + profile + ".json");
	}

	synchronized void load() {
		try {
			if (!Files.exists(file())) return;
			JsonObject o = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject();
			if (o.has("accessories")) for (JsonElement e : o.getAsJsonArray("accessories")) accessories.add(e.getAsString());
			if (o.has("skills")) for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("skills").entrySet()) skills.put(e.getKey(), e.getValue().getAsInt());
			accessoriesAt = o.has("accessoriesAt") ? o.get("accessoriesAt").getAsLong() : 0;
		} catch (IOException | RuntimeException e) {
			accessories.clear();
			skills.clear();
		}
	}

	public synchronized void save() {
		if (!dirty || profile == null) return;
		JsonObject o = new JsonObject();
		JsonArray a = new JsonArray();
		accessories.forEach(a::add);
		o.add("accessories", a);
		o.addProperty("accessoriesAt", accessoriesAt);
		JsonObject s = new JsonObject();
		skills.forEach(s::addProperty);
		o.add("skills", s);
		try {
			Files.createDirectories(dir);
			Path tmp = file().resolveSibling(file().getFileName() + ".tmp");
			Files.writeString(tmp, new Gson().toJson(o), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (java.nio.file.AtomicMoveNotSupportedException e) {
				Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING);
			}
			dirty = false;
		} catch (IOException ignored) {
			// next time
		}
	}
}
