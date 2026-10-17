package io.github.donttemptfate.fatesofa.quick;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The player's quick commands: each is one command they can send with one click (a tab above menus), one
 * key press (Quick command 1–9 in Controls) or a short alias they type. One action from the player = one
 * command, never more, never on its own.
 */
public final class QuickCommands {
	/**
	 * @param name    shown on the tab's tooltip and in the editor
	 * @param command what is sent, without the leading slash ("pets", "warp crimson")
	 * @param icon    a Minecraft item id ("bone") or a SkyBlock item id in capitals ("HYPERION")
	 * @param tab     shown as a tab above menus
	 * @param alias   a short command that sends it ("ch" → /ch); empty for none
	 */
	public record Entry(String name, String command, String icon, boolean tab, String alias) {
		public Entry {
			name = name == null ? "" : name.strip();
			command = clean(command);
			icon = icon == null || icon.isBlank() ? "paper" : icon.strip();
			if (icon.startsWith("minecraft:")) icon = icon.substring(10);
			alias = alias == null ? "" : alias.strip().toLowerCase(Locale.ROOT);
		}

		public String label() {
			return name.isEmpty() ? "/" + command : name;
		}
	}

	public static final int MAX = 24, HOTKEYS = 9, MAX_COMMAND = 200;
	static final Pattern ALIAS = Pattern.compile("^[a-z][a-z0-9_]{0,15}$");
	/** Names that would hide OFA's own or common Minecraft commands. */
	static final List<String> RESERVED = List.of("ofa", "help", "me", "msg", "tell", "w", "r", "reply", "say", "list", "trigger");

	/** True when an icon names a SkyBlock item (capitals, like the repo's ids) rather than a Minecraft one. */
	public static boolean skyblockIcon(String icon) {
		return icon != null && !icon.equals(icon.toLowerCase(Locale.ROOT));
	}

	public static List<Entry> defaults() {
		return List.of(
				new Entry("Storage", "storage", "ender_chest", true, ""),
				new Entry("Pets", "pets", "bone", true, ""),
				new Entry("Wardrobe", "wardrobe", "leather_chestplate", true, ""),
				new Entry("Accessory Bag", "accessories", "rabbit_foot", true, ""),
				new Entry("Skills", "skills", "diamond_sword", true, ""),
				new Entry("Warp menu", "warp", "ender_eye", true, ""));
	}

	/** "/Warp  crimson " → "warp crimson"; control characters and a leading slash removed, length capped. */
	public static String clean(String command) {
		if (command == null) return "";
		String c = command.replaceAll("§.?", "").replaceAll("\\p{Cntrl}", " ").strip();
		while (c.startsWith("/")) c = c.substring(1).strip();
		c = c.replaceAll("\\s+", " ");
		return c.length() > MAX_COMMAND ? c.substring(0, MAX_COMMAND) : c;
	}

	/** Null when the alias is fine (or empty), otherwise why it isn't. */
	public static String aliasProblem(String alias, List<Entry> all, int self) {
		if (alias == null || alias.isEmpty()) return null;
		if (!ALIAS.matcher(alias).matches()) return "letters, digits and _ only, starting with a letter (max 16)";
		if (RESERVED.contains(alias)) return "/" + alias + " is already a command";
		for (int i = 0; i < all.size(); i++) if (i != self && alias.equals(all.get(i).alias())) return "another quick command uses /" + alias;
		return null;
	}

	public static JsonArray toJson(List<Entry> list) {
		JsonArray a = new JsonArray();
		for (Entry e : list) {
			JsonObject o = new JsonObject();
			o.addProperty("name", e.name());
			o.addProperty("command", e.command());
			o.addProperty("icon", e.icon());
			o.addProperty("tab", e.tab());
			if (!e.alias().isEmpty()) o.addProperty("alias", e.alias());
			a.add(o);
		}
		return a;
	}

	/** The saved list, or the defaults when nothing was saved yet. Bad entries are skipped. */
	public static List<Entry> fromJson(JsonElement el) {
		if (el == null || !el.isJsonArray()) return defaults();
		List<Entry> out = new ArrayList<>();
		for (JsonElement e : el.getAsJsonArray()) {
			if (out.size() >= MAX || !e.isJsonObject()) continue;
			JsonObject o = e.getAsJsonObject();
			Entry en = new Entry(str(o, "name"), str(o, "command"), str(o, "icon"), !o.has("tab") || bool(o, "tab"), str(o, "alias"));
			if (en.command().isEmpty()) continue;
			if (aliasProblem(en.alias(), out, -1) != null) en = new Entry(en.name(), en.command(), en.icon(), en.tab(), "");
			out.add(en);
		}
		return out;
	}

	static String str(JsonObject o, String k) {
		try {
			return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
		} catch (RuntimeException e) {
			return "";
		}
	}

	static boolean bool(JsonObject o, String k) {
		try {
			return o.get(k).getAsBoolean();
		} catch (RuntimeException e) {
			return true;
		}
	}
}
