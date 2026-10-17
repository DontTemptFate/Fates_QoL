package io.github.donttemptfate.fatesofa.state;

import io.github.donttemptfate.fates.common.text.Strip;

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
public record TabInfo(Map<String, String> values, List<String> lines) {
	static final Pattern KV = Pattern.compile("^([A-Za-z][A-Za-z '\\-]{1,30}):\\s*(.*)$");

	public static TabInfo parse(List<String> raw) {
		Map<String, String> map = new LinkedHashMap<>();
		List<String> lines = new java.util.ArrayList<>();
		for (String r : raw) {
			String l = Strip.clean(r);
			if (l.isEmpty()) continue;
			lines.add(l);
			Matcher m = KV.matcher(l);
			if (m.matches()) map.putIfAbsent(m.group(1).trim(), m.group(2).trim());
		}
		return new TabInfo(Collections.unmodifiableMap(map), List.copyOf(lines));
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
