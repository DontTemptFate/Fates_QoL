package io.github.donttemptfate.fatesofa.data;

import io.github.donttemptfate.fatescommon.text.LegacyText;

import java.util.List;
import java.util.Locale;

/** One item of the NEU repo: what it looks like and how to get it. */
public record RepoItem(
		String key,
		String displayName,
		String itemId,
		int damage,
		List<String> lore,
		List<Recipe> recipes,
		String crafttext,
		String wiki,
		String nbt) {

	/** Name without colour codes, for search and sorting. */
	public String plainName() {
		return LegacyText.clean(displayName);
	}

	/** Lower-case text searched by the item list: name, key and lore. */
	public String searchText() {
		StringBuilder b = new StringBuilder(plainName()).append(' ').append(key.replace('_', ' '));
		for (String l : lore) b.append(' ').append(LegacyText.clean(l));
		return b.toString().toLowerCase(Locale.ROOT);
	}

	public boolean isPet() {
		return key.contains(";") && Character.isDigit(key.charAt(key.length() - 1)) && !key.contains("_RUNE;") && itemId.contains("skull");
	}
}
