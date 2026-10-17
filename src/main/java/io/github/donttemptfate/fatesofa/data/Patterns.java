package io.github.donttemptfate.fatesofa.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Chat, scoreboard and menu text patterns, kept as data so a changed Hypixel message is fixed without a
 * mod update. Source: SkyHanni-REPO's {@code regexesModern.json} (MIT, maintained as Hypixel changes),
 * with OFA's own built-in pattern as the fallback for every key it uses.
 *
 * <p>OFA matches text without formatting codes, so colour codes in a pattern are removed when it's loaded.
 */
public final class Patterns {
	private final Map<String, String> fallback = new HashMap<>();
	private volatile Map<String, Pattern> compiled = new ConcurrentHashMap<>();
	private volatile Map<String, String> repo = Map.of();

	/** Registers OFA's own pattern for a key (used when the repo doesn't have it or it doesn't compile). */
	public Patterns define(String key, String regex) {
		fallback.put(key, regex);
		compiled = new ConcurrentHashMap<>();
		return this;
	}

	/** Loads the repo file's patterns (replacing earlier ones). */
	public void load(JsonObject regexesModern) {
		Map<String, String> m = new HashMap<>();
		JsonObject r = regexesModern != null && regexesModern.has("regexes") ? regexesModern.getAsJsonObject("regexes") : regexesModern;
		if (r != null) {
			for (Map.Entry<String, JsonElement> e : r.entrySet()) {
				if (e.getValue().isJsonPrimitive()) m.put(e.getKey(), e.getValue().getAsString());
			}
		}
		repo = m;
		compiled = new ConcurrentHashMap<>();
	}

	public int repoSize() {
		return repo.size();
	}

	/** The pattern for a key (repo first, then OFA's own), without colour codes; null when neither exists. */
	public Pattern get(String key) {
		Map<String, Pattern> cache = compiled;
		Pattern p = cache.get(key);
		if (p != null) return p;
		String src = repo.get(key);
		p = src == null ? null : compileColourless(src);
		if (p == null) {
			String fb = fallback.get(key);
			p = fb == null ? null : compileColourless(fb);
		}
		if (p != null) cache.put(key, p);
		return p;
	}

	/**
	 * Like {@link #get(String)}, but the repo pattern is only used when it has every named group OFA reads;
	 * otherwise OFA's own pattern is used.
	 */
	public Pattern get(String key, String... groups) {
		Pattern p = get(key);
		if (p == null || groups.length == 0) return p;
		if (p.namedGroups().keySet().containsAll(java.util.List.of(groups))) return p;
		String fb = fallback.get(key);
		return fb == null ? null : compileColourless(fb);
	}

	/** Removes Minecraft colour codes from a pattern so it matches plain text. */
	public static String colourless(String regex) {
		String s = regex;
		s = s.replaceAll("\\(\\?:(?:§(?:\\.|\\[[^\\]]*]|[0-9a-fk-orA-FK-OR]))+\\)[*?+]?", "");
		s = s.replaceAll("§\\[[^\\]]*]", "");
		s = s.replaceAll("§.", "");
		return s;
	}

	static Pattern compileColourless(String regex) {
		try {
			return Pattern.compile(colourless(regex));
		} catch (PatternSyntaxException e) {
			return null;
		}
	}
}
