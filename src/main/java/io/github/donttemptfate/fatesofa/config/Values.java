package io.github.donttemptfate.fatesofa.config;

import io.github.donttemptfate.fatesofa.state.Island;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads and writes the text-stored setting values (sounds, islands, lines, templates, rules, colours). */
public final class Values {
	private Values() {}

	// ---- sounds -------------------------------------------------------------------------------------

	/** A sound to play: the game's sound id, volume 0–2, pitch 0.5–2. */
	public record Sound(String id, float volume, float pitch) {
		public static final Sound NONE = new Sound("", 1f, 1f);

		public boolean silent() {
			return id.isEmpty() || volume <= 0;
		}

		public String encode() {
			return id.isEmpty() ? "" : id + "|" + trim(volume) + "|" + trim(pitch);
		}
	}

	/** Sounds offered in the picker: id → friendly name ("" = none). */
	public static final Map<String, String> SOUNDS = new LinkedHashMap<>();

	static {
		SOUNDS.put("", "None");
		SOUNDS.put("minecraft:block.note_block.pling", "Pling");
		SOUNDS.put("minecraft:entity.experience_orb.pickup", "Orb pickup");
		SOUNDS.put("minecraft:entity.player.levelup", "Level up");
		SOUNDS.put("minecraft:block.note_block.bell", "Bell");
		SOUNDS.put("minecraft:block.note_block.chime", "Chime");
		SOUNDS.put("minecraft:block.note_block.harp", "Harp");
		SOUNDS.put("minecraft:block.note_block.bass", "Bass");
		SOUNDS.put("minecraft:block.note_block.xylophone", "Xylophone");
		SOUNDS.put("minecraft:block.note_block.bit", "Bit");
		SOUNDS.put("minecraft:block.anvil.land", "Anvil");
		SOUNDS.put("minecraft:entity.arrow.hit_player", "Arrow hit");
		SOUNDS.put("minecraft:entity.villager.yes", "Villager yes");
		SOUNDS.put("minecraft:entity.villager.no", "Villager no");
		SOUNDS.put("minecraft:entity.cat.ambient", "Cat");
		SOUNDS.put("minecraft:entity.wolf.ambient", "Wolf");
		SOUNDS.put("minecraft:block.amethyst_block.chime", "Amethyst");
		SOUNDS.put("minecraft:ui.button.click", "Click");
		SOUNDS.put("minecraft:entity.generic.explode", "Explosion");
		SOUNDS.put("minecraft:entity.wither.spawn", "Wither");
		SOUNDS.put("minecraft:entity.ender_dragon.growl", "Dragon");
		SOUNDS.put("minecraft:entity.firework_rocket.blast", "Firework");
	}

	static final Pattern SOUND_ID = Pattern.compile("[a-z0-9_.\\-]+:[a-z0-9_./\\-]+");

	public static Sound sound(String v) {
		if (v == null || v.isBlank()) return Sound.NONE;
		String[] p = v.split("\\|");
		String id = p[0].trim().toLowerCase(Locale.ROOT);
		if (!SOUND_ID.matcher(id).matches()) return Sound.NONE;
		float vol = p.length > 1 ? clamp(num(p[1], 1f), 0f, 2f) : 1f;
		float pitch = p.length > 2 ? clamp(num(p[2], 1f), 0.5f, 2f) : 1f;
		return new Sound(id, vol, pitch);
	}

	/** The next sound in the picker list ({@code dir} ±1), keeping volume and pitch. */
	public static String nextSound(String v, int dir) {
		Sound s = sound(v);
		List<String> ids = new ArrayList<>(SOUNDS.keySet());
		int i = ids.indexOf(s.id());
		String next = ids.get(Math.floorMod((i < 0 ? 0 : i) + dir, ids.size()));
		return new Sound(next, s.volume(), s.pitch()).encode();
	}

	public static String soundName(String id) {
		String n = SOUNDS.get(id);
		if (n != null) return n;
		int c = id.lastIndexOf(':');
		return id.substring(c + 1);
	}

	// ---- islands ------------------------------------------------------------------------------------

	/** The islands listed (unknown names dropped). Empty = everywhere. */
	public static Set<Island> islands(String v) {
		Set<Island> out = EnumSet.noneOf(Island.class);
		if (v == null || v.isBlank()) return out;
		for (String p : v.split(",")) {
			try {
				out.add(Island.valueOf(p.trim().toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException ignored) {
				// an island removed in an update: skip it
			}
		}
		return out;
	}

	/** True when the setting allows {@code here} (an empty list allows everywhere; unknown location only then). */
	public static boolean allows(String v, Island here) {
		Set<Island> s = islands(v);
		return s.isEmpty() || (here != null && s.contains(here));
	}

	public static String toggleIsland(String v, Island i) {
		Set<Island> s = islands(v);
		if (!s.remove(i)) s.add(i);
		List<String> names = new ArrayList<>();
		for (Island x : s) names.add(x.name());
		return String.join(",", names);
	}

	// ---- lines --------------------------------------------------------------------------------------

	/** The shown line ids, in order (ids the setting doesn't know are dropped). */
	public static List<String> lines(String v, List<String> known) {
		List<String> out = new ArrayList<>();
		if (v == null || v.isBlank()) return out;
		for (String p : v.split(",")) {
			String id = p.trim();
			if (known.contains(id) && !out.contains(id)) out.add(id);
		}
		return out;
	}

	/** Shows or hides a line (a newly shown line goes last). */
	public static String toggleLine(String v, List<String> known, String id) {
		List<String> l = lines(v, known);
		if (!l.remove(id)) l.add(id);
		return String.join(",", l);
	}

	/** Moves a shown line up ({@code dir} -1) or down (+1). */
	public static String moveLine(String v, List<String> known, String id, int dir) {
		List<String> l = lines(v, known);
		int i = l.indexOf(id);
		int j = i + dir;
		if (i < 0 || j < 0 || j >= l.size()) return String.join(",", l);
		l.set(i, l.get(j));
		l.set(j, id);
		return String.join(",", l);
	}

	// ---- templates ----------------------------------------------------------------------------------

	static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)}");

	/** Fills {name} placeholders; unknown ones stay as written. "&" colour codes become "§". */
	public static String fill(String template, Map<String, ?> vars) {
		if (template == null) return "";
		Matcher m = PLACEHOLDER.matcher(template);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			Object v = vars.get(m.group(1));
			m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? m.group() : String.valueOf(v)));
		}
		m.appendTail(sb);
		return sb.toString().replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
	}

	/** Placeholders used in {@code template} that aren't in {@code allowed}. */
	public static List<String> unknownPlaceholders(String template, List<String> allowed) {
		List<String> out = new ArrayList<>();
		if (template == null) return out;
		Matcher m = PLACEHOLDER.matcher(template);
		while (m.find()) if (!allowed.contains(m.group(1)) && !out.contains(m.group(1))) out.add(m.group(1));
		return out;
	}

	// ---- rules --------------------------------------------------------------------------------------

	/** The rules, one per line; blank lines and lines starting with # are skipped. */
	public static List<String> rules(String v) {
		List<String> out = new ArrayList<>();
		if (v == null) return out;
		for (String line : v.split("\n")) {
			String t = line.trim();
			if (!t.isEmpty() && !t.startsWith("#")) out.add(t);
		}
		return out;
	}

	public static String setRule(String v, int index, String rule) {
		List<String> l = rules(v);
		String r = rule == null ? "" : rule.replace('\n', ' ').trim();
		if (index >= 0 && index < l.size()) {
			if (r.isEmpty()) l.remove(index);
			else l.set(index, r);
		} else if (!r.isEmpty()) {
			l.add(r);
		}
		return String.join("\n", l);
	}

	// ---- colours ------------------------------------------------------------------------------------

	/** The colour to draw now: the stored ARGB, or a cycling rainbow keeping its alpha. */
	public static int color(int argb, boolean rainbow, long nowMs) {
		if (!rainbow) return argb;
		float hue = (nowMs % 4000L) / 4000f;
		return (argb & 0xFF000000) | (hsv(hue, 0.85f, 1f) & 0xFFFFFF);
	}

	public static int hsv(float h, float s, float v) {
		float r, g, b;
		int i = (int) Math.floor(h * 6);
		float f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
		switch (Math.floorMod(i, 6)) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return 0xFF000000 | Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
	}

	/** "#AARRGGBB" (or "#RRGGBB" when fully opaque). */
	public static String hex(int argb) {
		return (argb >>> 24) == 0xFF ? String.format("#%06X", argb & 0xFFFFFF) : String.format("#%08X", argb);
	}

	/** Parses "#RGB", "#RRGGBB" or "#AARRGGBB" (with or without #); null when it isn't a colour. */
	public static Integer parseHex(String s) {
		if (s == null) return null;
		String t = s.trim();
		if (t.startsWith("#")) t = t.substring(1);
		if (!t.matches("[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) return null;
		if (t.length() == 3) t = "" + t.charAt(0) + t.charAt(0) + t.charAt(1) + t.charAt(1) + t.charAt(2) + t.charAt(2);
		long v = Long.parseLong(t, 16);
		return (int) (t.length() == 6 ? 0xFF000000L | v : v);
	}

	/** Replaces one channel (shift 24 = alpha, 16 = red, 8 = green, 0 = blue). */
	public static int withChannel(int argb, int shift, int value) {
		int v = Math.max(0, Math.min(255, value));
		return (argb & ~(0xFF << shift)) | (v << shift);
	}

	// ---- helpers ------------------------------------------------------------------------------------

	static float num(String s, float def) {
		try {
			float f = Float.parseFloat(s.trim());
			return Float.isNaN(f) ? def : f;
		} catch (NumberFormatException e) {
			return def;
		}
	}

	static float clamp(float v, float lo, float hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	static String trim(float f) {
		float r = Math.round(f * 100) / 100f;
		return r == (int) r ? String.valueOf((int) r) : String.valueOf(r);
	}
}
