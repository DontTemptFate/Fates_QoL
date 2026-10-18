package io.github.donttemptfate.fatesofa.state;

import io.github.donttemptfate.fatescommon.text.LegacyText;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The SkyBlock tab list read as "Key: value" pairs ("Area: Hub", "Profile: Kiwi ♲", "SB Level: [146]",
 * "Bank: 1.2B / 50M"). First occurrence wins. Section titles and widget lines without a colon are
 * kept in {@link #lines()} for features that read their own widget.
 */
public record TabInfo(Map<String, String> values, List<String> lines, List<String> withBlanks) {
	static final Pattern KV = Pattern.compile("^([A-Za-z][A-Za-z '\\-]{1,30}):\\s*(.*)$");

	public static TabInfo parse(List<String> raw) {
		Map<String, String> map = new LinkedHashMap<>();
		List<String> lines = new java.util.ArrayList<>();
		List<String> blanks = new java.util.ArrayList<>();
		for (String r : raw) {
			String l = LegacyText.clean(r);
			blanks.add(l);
			if (l.isEmpty()) continue;
			lines.add(l);
			Matcher m = KV.matcher(l);
			if (m.matches()) map.putIfAbsent(m.group(1).trim(), m.group(2).trim());
		}
		return new TabInfo(Collections.unmodifiableMap(map), List.copyOf(lines), List.copyOf(blanks));
	}

	/**
	 * A tab-list widget's lines: those after the header line (e.g. "Commissions:") up to the next blank
	 * line or the next widget header. Empty when the widget isn't shown.
	 */
	public List<String> widget(String header) {
		List<String> out = new java.util.ArrayList<>();
		boolean in = false;
		for (String l : withBlanks) {
			if (!in) {
				if (l.equalsIgnoreCase(header) || l.startsWith(header + " ") || l.startsWith(header + ":")) {
					in = true;
					String rest = l.length() > header.length() ? l.substring(header.length()).replaceFirst("^:?\\s*", "") : "";
					if (!rest.isEmpty() && !rest.startsWith("(")) out.add(rest);
				}
				continue;
			}
			if (l.isEmpty() || (l.endsWith(":") && !l.contains(": "))) break;
			out.add(l);
		}
		return out;
	}

	public String get(String key) {
		return values.get(key);
	}

	public Island island() {
		String a = values.get("Area");
		if (a == null) a = values.get("Dungeon");
		return Island.fromName(a);
	}

	public String profileName() {
		String p = values.get("Profile");
		if (p == null) return null;
		String name = p.replaceAll("[^A-Za-z ]", "").trim();
		return name.isEmpty() ? null : name;
	}

	public ProfileType profileType() {
		return ProfileType.fromProfileLine(values.get("Profile"));
	}

	/** "SB Level: [146]" → 146, null when absent. */
	public Integer skyblockLevel() {
		String v = values.get("SB Level");
		if (v == null) return null;
		Matcher m = Pattern.compile("\\[(\\d{1,4})]").matcher(v);
		return m.find() ? Integer.parseInt(m.group(1)) : null;
	}
}
