package io.github.donttemptfate.fatesofa.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Every player setting in one versioned JSON file ({@code config/fatesofa/settings.json}). Values the
 * player never changed are not written, so a new default in an update reaches them. Bad values (wrong
 * type, out of range, unknown option) fall back to the default instead of failing.
 */
public final class Settings {
	/** Current file version; older files are migrated on load. */
	public static final int VERSION = 1;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path file;
	private final Map<String, Setting<?>> declared = new LinkedHashMap<>();
	private final List<Consumer<String>> listeners = new ArrayList<>();
	private JsonObject values = new JsonObject();
	private volatile boolean dirty;

	public Settings(Path file) {
		this.file = file;
	}

	/** Declares a setting (features do this once at start-up). Returns it for convenience. */
	public synchronized <T> Setting<T> declare(Setting<T> s) {
		Setting<?> old = declared.putIfAbsent(s.key, s);
		if (old != null && old != s) throw new IllegalStateException("setting declared twice: " + s.key);
		if (s.rainbow != null) declared.putIfAbsent(s.rainbow.key, s.rainbow);
		return s;
	}

	public synchronized Collection<Setting<?>> all() {
		return Collections.unmodifiableCollection(new ArrayList<>(declared.values()));
	}

	public synchronized Setting<?> find(String key) {
		return declared.get(key);
	}

	/** Called with the key whenever a value changes. */
	public void onChange(Consumer<String> listener) {
		listeners.add(listener);
	}

	/** Parsed values by key, so reading a setting every frame never touches JSON or the lock. Cleared on any change. */
	private final java.util.concurrent.ConcurrentHashMap<String, Object> cache = new java.util.concurrent.ConcurrentHashMap<>();
	private static final Object DEFAULT = new Object();

	@SuppressWarnings("unchecked")
	public <T> T get(Setting<T> s) {
		Object c = cache.get(s.key);
		if (c != null) return c == DEFAULT ? s.def : (T) c;
		synchronized (this) {
			JsonElement e = values.get(s.key);
			Object v = e == null ? null : read(s, e);
			cache.put(s.key, v == null ? DEFAULT : v);
			return v == null ? s.def : (T) v;
		}
	}

	public boolean on(Setting<Boolean> s) {
		return Boolean.TRUE.equals(get(s));
	}

	public <T> void set(Setting<T> s, T value) {
		if (value == null) {
			reset(s);
			return;
		}
		synchronized (this) {
			JsonElement e = write(s, value);
			if (e == null) return;
			if (value.equals(s.def)) values.remove(s.key);
			else values.add(s.key, e);
			cache.remove(s.key);
			dirty = true;
		}
		fire(s.key);
	}

	void fire(String key) {
		for (Consumer<String> l : listeners) {
			try {
				l.accept(key);
			} catch (RuntimeException e) {
				// a listener's problem never breaks changing a setting
			}
		}
	}

	/** Back to the default value. */
	public void reset(Setting<?> s) {
		synchronized (this) {
			if (values.remove(s.key) == null) return;
			cache.remove(s.key);
			dirty = true;
		}
		fire(s.key);
	}

	public synchronized boolean isDefault(Setting<?> s) {
		return !values.has(s.key);
	}

	/** A raw value by key, for keys that aren't declared settings (HUD positions, lock lists). */
	public synchronized JsonElement raw(String key) {
		JsonElement e = values.get(key);
		return e == null ? null : e.deepCopy();
	}

	public synchronized void putRaw(String key, JsonElement value) {
		if (value == null) values.remove(key);
		else values.add(key, value.deepCopy());
		cache.remove(key);
		dirty = true;
	}

	static Object read(Setting<?> s, JsonElement e) {
		try {
			if (!e.isJsonPrimitive()) return null;
			JsonPrimitive p = e.getAsJsonPrimitive();
			return switch (s.type) {
				case BOOL -> p.isBoolean() ? p.getAsBoolean() : null;
				case INT, COLOR -> {
					if (!p.isNumber()) yield null;
					int v = p.getAsInt();
					if (s.type == Setting.Type.INT && (v < s.min || v > s.max)) yield null;
					yield v;
				}
				case DOUBLE -> {
					if (!p.isNumber()) yield null;
					double v = p.getAsDouble();
					yield v < s.min || v > s.max || Double.isNaN(v) ? null : v;
				}
				case ENUM -> p.isString() && s.options.contains(p.getAsString()) ? p.getAsString() : null;
				case STRING, KEY, SOUND, ISLANDS, LINES, TEMPLATE, RULES -> p.isString() && p.getAsString().length() <= s.maxLength ? p.getAsString() : null;
			};
		} catch (RuntimeException ex) {
			return null;
		}
	}

	static JsonElement write(Setting<?> s, Object value) {
		if (value == null) return null;
		JsonElement e = switch (value) {
			case Boolean b -> new JsonPrimitive(b);
			case Number n -> new JsonPrimitive(n);
			case String str -> new JsonPrimitive(str);
			default -> null;
		};
		return e != null && read(s, e) != null ? e : null;
	}

	// ---- files ------------------------------------------------------------------------------------

	/** Loads the file; a missing or broken file means all defaults (a broken one is kept aside). */
	public synchronized void load() {
		values = new JsonObject();
		cache.clear();
		if (file == null || !Files.exists(file)) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			int version = root.has("version") ? root.get("version").getAsInt() : 0;
			JsonObject v = root.has("values") && root.get("values").isJsonObject() ? root.getAsJsonObject("values") : new JsonObject();
			values = migrate(v, version);
			cache.clear();
			dirty = version != VERSION;
		} catch (IOException | RuntimeException e) {
			try {
				Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// keep going with defaults
			}
		}
	}

	static JsonObject migrate(JsonObject values, int fromVersion) {
		// Version 1 is the first; future renames go here, oldest first.
		return values;
	}

	public boolean dirty() {
		return dirty;
	}

	/** Writes the file if anything changed (atomic replace, so a crash never leaves half a file). */
	public synchronized void save() throws IOException {
		if (!dirty || file == null) return;
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		root.add("values", values);
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
		dirty = false;
	}

	// ---- import / export -----------------------------------------------------------------------------

	/** Raw (non-setting) keys an import may bring, with the shape each must have. */
	static boolean rawImportable(String key, JsonElement v) {
		if (key.length() > 80 || !key.matches("[a-z0-9_.\\-]+")) return false;
		if (key.startsWith("hud.")) return v.isJsonObject() && v.getAsJsonObject().size() <= 8;
		if (key.startsWith("hudhide.")) return v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean();
		return switch (key) {
			case "quick.entries" -> v.isJsonArray() && v.getAsJsonArray().size() <= 64;
			case "protect.locks" -> v.isJsonObject();
			case "waypoints.saved" -> v.isJsonArray() || v.isJsonObject();
			default -> false;
		};
	}

	/** The player's changed settings as one copyable line ("OFA1:" + base64 JSON). */
	public synchronized String export() {
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		root.add("values", values.deepCopy());
		return "OFA1:" + Base64.getEncoder().encodeToString(root.toString().getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Applies an exported line. Only declared settings with valid values are taken; everything else in
	 * it is ignored. Returns how many settings were applied, or -1 when the text isn't an export.
	 */
	public int importLine(String line) {
		if (line == null || !line.trim().startsWith("OFA1:")) return -1;
		JsonObject v;
		int version;
		try {
			byte[] raw = Base64.getDecoder().decode(line.trim().substring(5));
			if (raw.length > 512 * 1024) return -1;
			JsonObject root = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
			version = root.get("version").getAsInt();
			if (!root.has("values") || !root.get("values").isJsonObject()) return -1;
			v = migrate(root.getAsJsonObject("values"), version);
			if (v == null) return -1;
		} catch (RuntimeException e) {
			return -1;
		}
		int applied = 0;
		List<String> changed = new ArrayList<>();
		synchronized (this) {
			for (Map.Entry<String, JsonElement> e : v.entrySet()) {
				Setting<?> s = declared.get(e.getKey());
				if (s == null) {
					// layouts, quick commands, locks and waypoints travel too (shape checked; each feature re-validates)
					if (rawImportable(e.getKey(), e.getValue())) {
						values.add(e.getKey(), e.getValue().deepCopy());
						changed.add(e.getKey());
						applied++;
					}
					continue;
				}
				if (read(s, e.getValue()) == null) continue;
				values.add(s.key, e.getValue().deepCopy());
				changed.add(s.key);
				applied++;
			}
			cache.clear();
			if (applied > 0) dirty = true;
		}
		for (String k : changed) fire(k);
		return applied;
	}
}
