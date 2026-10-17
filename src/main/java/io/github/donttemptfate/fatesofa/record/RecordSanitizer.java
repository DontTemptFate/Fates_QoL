package io.github.donttemptfate.fatesofa.record;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cleans what the play recorder writes: player names become {me} / {player}, and anything players typed
 * (public, party, guild, co-op chat, private messages) is left out entirely. Game and NPC text is kept.
 */
public final class RecordSanitizer {
	/** Lines other players (or you) typed. Checked on text without colour codes. */
	static final Pattern CHANNEL = Pattern.compile("^(?:Party|Guild|Officer|Co-op|Coop) > .*|^(?:From|To) (?:\\[[^]]+] )?[A-Za-z0-9_]{1,16}: .*");
	/** SkyBlock chat: "[287] ♲ [MVP+] Name: hi". */
	static final Pattern SKYBLOCK_CHAT = Pattern.compile("^\\[\\d{1,3}] .*?[A-Za-z0-9_]{1,16}(?: [^\\s:]{1,3})?: .*");
	/** Lobby chat: "[MVP+] Name: hi" or "Name: hi" (an all-capitals word like "BONUS:" is the game). */
	static final Pattern LOBBY_CHAT = Pattern.compile("^(?:\\[[A-Z+]{2,12}] )?(?<name>[A-Za-z0-9_]{3,16}): .*");
	/** Speakers that are the game, not players. */
	static final Pattern GAME_SPEAKER = Pattern.compile("^\\[(?:NPC|BOSS|SKULL|STATUE|CROWD|ATTENDANT|SBE)] .*");

	static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
	/** A name right after a rank tag ("[MVP§c+§b] Name") or a SkyBlock level ("[287] ♲ Name"), colour codes allowed. */
	static final Pattern TAGGED_NAME = Pattern.compile(
			"((?:\\[(?:§.)*(?:VIP|MVP|YOUTUBE|ADMIN|MOD|HELPER|GM|OWNER|PIG)(?:§.)*(?:\\+(?:§.)*){0,3}] ?|\\[(?:§.)*\\d{1,3}(?:§.)*] (?:§.)*(?:[^\\sA-Za-z0-9_§\\[]{1,2} )?)(?:§.)*)"
					+ "(?!\\{)([A-Za-z0-9_]{3,16})(?![A-Za-z0-9_])");

	private final String me;
	private final Pattern mePattern;
	private final List<Name> others;

	record Name(String lower, Pattern p) {
	}

	public RecordSanitizer(String me, Collection<String> otherNames) {
		this.me = me == null ? "" : me;
		Set<String> set = new LinkedHashSet<>();
		for (String n : otherNames) if (n != null && NAME.matcher(n).matches() && !n.equalsIgnoreCase(this.me)) set.add(n);
		// longest first, so "Fate" doesn't eat the start of "Fates"
		this.others = set.stream().sorted(Comparator.comparingInt(String::length).reversed()).limit(300)
				.map(n -> new Name(n.toLowerCase(Locale.ROOT), wordPattern(n))).toList();
		this.mePattern = this.me.isEmpty() ? null : wordPattern(this.me);
	}

	/** True when the plain line is something a player typed (and must not be written). */
	public boolean typedByPlayer(String plain) {
		if (plain == null) return false;
		String p = plain.trim();
		if (GAME_SPEAKER.matcher(p).matches()) return false;
		if (CHANNEL.matcher(p).matches() || SKYBLOCK_CHAT.matcher(p).matches()) return true;
		Matcher lobby = LOBBY_CHAT.matcher(p);
		if (lobby.matches() && !lobby.group("name").equals(lobby.group("name").toUpperCase(Locale.ROOT))) return true;
		int colon = p.indexOf(": ");
		if (colon > 0) { // "<anything with a known name>: text"
			String head = p.substring(0, colon);
			if (mePattern != null && mePattern.matcher(head).find()) return true;
			String lower = head.toLowerCase(Locale.ROOT);
			for (Name n : others) if (lower.contains(n.lower()) && n.p().matcher(head).find()) return true;
		}
		return false;
	}

	/** The text with names replaced. Works on § text too (names aren't split by colour codes in practice). */
	public String clean(String text) {
		if (text == null || text.isEmpty()) return text;
		String out = text;
		if (mePattern != null) out = mePattern.matcher(out).replaceAll("{me}");
		out = TAGGED_NAME.matcher(out).replaceAll("$1{player}");
		String lower = out.toLowerCase(Locale.ROOT);
		for (Name n : others) if (lower.contains(n.lower())) out = n.p().matcher(out).replaceAll("{player}");
		return out;
	}

	static Pattern wordPattern(String name) {
		// § codes before a name ("§bFate") still count as a boundary
		return Pattern.compile("(?:(?<=§[0-9a-fk-orA-FK-OR])|(?<![A-Za-z0-9_]))" + Pattern.quote(name) + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
	}
}
