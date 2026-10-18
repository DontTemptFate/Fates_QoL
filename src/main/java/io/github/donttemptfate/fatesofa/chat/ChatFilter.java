package io.github.donttemptfate.fatesofa.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Decides what happens to an incoming chat message: shown, hidden, or folded into the previous copy of
 * the same message ("×3"). Only switched-on groups apply. Pure logic; the game hook does the rest.
 */
public final class ChatFilter {
	public enum Result { SHOW, HIDE, COMPACT }

	public record Decision(Result result, int count, String group) {
		static final Decision SHOW = new Decision(Result.SHOW, 1, null);
	}

	/** Default groups. Each pattern is matched against the message without formatting codes. */
	public static final List<ChatRule> DEFAULTS = List.of(
			ChatRule.of("lobby_joins", "^(?:>>> )?\\[MVP\\+\\+?] \\S+ (?:joined|slid into) the lobby!", ChatRule.Action.HIDE),
			ChatRule.of("lobby_joins", "^ *\\[[A-Z+]+] \\S+ (?:joined|spooked into) the lobby!", ChatRule.Action.HIDE),
			ChatRule.of("watchdog", "^\\[WATCHDOG ANNOUNCEMENT]", ChatRule.Action.HIDE),
			ChatRule.of("watchdog", "^Watchdog has banned [\\d,]+ players in the last 7 days\\.", ChatRule.Action.HIDE),
			ChatRule.of("watchdog", "^Staff have banned an additional [\\d,]+ in the last 7 days\\.", ChatRule.Action.HIDE),
			ChatRule.of("watchdog", "^Blacklisted modifications are a bannable offense!", ChatRule.Action.HIDE),
			ChatRule.of("profile_info", "^You are playing on profile: ", ChatRule.Action.HIDE),
			ChatRule.of("profile_info", "^Profile ID: [0-9a-f-]{36}$", ChatRule.Action.HIDE),
			ChatRule.of("ability_cooldown", "^This ability is (?:on cooldown|currently on cooldown) for \\d+s\\.", ChatRule.Action.COMPACT),
			ChatRule.of("ability_cooldown", "^You do not have enough mana to do this!", ChatRule.Action.COMPACT),
			ChatRule.of("kill_combo", "^\\+\\d+ Kill Combo", ChatRule.Action.HIDE),
			ChatRule.of("kill_combo", "^Your Kill Combo has expired!", ChatRule.Action.HIDE),
			ChatRule.of("implosion", "^Your Implosion hit \\d+ enem(?:y|ies) for [\\d,.]+ damage\\.", ChatRule.Action.HIDE),
			ChatRule.of("implosion", "^There are blocks in the way!", ChatRule.Action.HIDE),
			ChatRule.of("bazaar_orders", "^\\[Bazaar] (?:Submitting|Executing|Claiming) ", ChatRule.Action.HIDE),
			ChatRule.of("auction_spam", "^You (?:have|already have) .* auction", ChatRule.Action.COMPACT),
			ChatRule.of("sacks", "^\\[Sacks] \\+[\\d,]+ items?\\.", ChatRule.Action.COMPACT),
			ChatRule.of("blocks_in_way", "^There are blocks in the way!", ChatRule.Action.COMPACT),
			ChatRule.of("guild_exp", "^You earned \\d+ GEXP", ChatRule.Action.HIDE),
			ChatRule.of("friend_status", "^Friend > \\S+ (?:joined|left)\\.", ChatRule.Action.HIDE),
			ChatRule.of("guild_status", "^Guild > \\S+ (?:joined|left)\\.", ChatRule.Action.HIDE));

	/** Group ids with a label for the settings screen. */
	public static final Map<String, String> GROUPS = new LinkedHashMap<>();

	static {
		GROUPS.put("lobby_joins", "Lobby join messages");
		GROUPS.put("watchdog", "Watchdog announcements");
		GROUPS.put("profile_info", "\"You are playing on profile\" lines");
		GROUPS.put("ability_cooldown", "Ability cooldown / mana spam (folded)");
		GROUPS.put("kill_combo", "Kill combo messages");
		GROUPS.put("implosion", "Implosion damage messages");
		GROUPS.put("bazaar_orders", "Bazaar order progress");
		GROUPS.put("auction_spam", "Auction house notices (folded)");
		GROUPS.put("sacks", "Sack messages (folded)");
		GROUPS.put("blocks_in_way", "\"Blocks in the way\" (folded)");
		GROUPS.put("guild_exp", "Guild EXP messages");
		GROUPS.put("friend_status", "Friend joined / left");
		GROUPS.put("guild_status", "Guild member joined / left");
	}

	private final List<ChatRule> rules = new ArrayList<>(DEFAULTS);
	private volatile Predicate<String> groupOn = g -> false;
	private volatile boolean compactAll;
	private String lastText;
	private long lastAt;
	private int lastCount;

	public static final long COMPACT_WINDOW_MS = 60_000;

	/** Which groups are switched on. */
	public void groups(Predicate<String> on) {
		this.groupOn = on;
	}

	/**
	 * Per-group choice ("show", "hide", "fold"; null = the group's own action when switched on). Set by
	 * the chat settings; takes over from {@link #groups} for groups it answers.
	 */
	private volatile java.util.function.Function<String, String> groupMode = g -> null;

	public void groupModes(java.util.function.Function<String, String> mode) {
		this.groupMode = mode;
	}

	/** The group's own action ("hide" or "fold") from the first rule of that group. */
	public static String defaultMode(String group) {
		for (ChatRule r : DEFAULTS) if (r.group().equals(group)) return r.action() == ChatRule.Action.HIDE ? "hide" : "fold";
		return "hide";
	}

	/** Also fold any message repeated within a minute, not only the listed groups. */
	public void compactAll(boolean on) {
		this.compactAll = on;
	}

	public synchronized void replaceRules(List<ChatRule> data) {
		rules.clear();
		rules.addAll(data);
	}

	public synchronized Set<String> groupIds() {
		return GROUPS.keySet();
	}

	public synchronized Decision decide(String plain, long now) {
		if (plain == null || plain.isBlank()) return Decision.SHOW;
		for (ChatRule r : rules) {
			String mode = groupMode.apply(r.group());
			if (mode == null && !groupOn.test(r.group())) continue;
			if (!r.matches(plain)) continue;
			if ("show".equals(mode)) return new Decision(Result.SHOW, 1, r.group());
			if ("hide".equals(mode) || (mode == null && r.action() == ChatRule.Action.HIDE)) return new Decision(Result.HIDE, 1, r.group());
			return compact(plain, now, r.group());
		}
		if (compactAll) {
			Decision d = compact(plain, now, null);
			if (d.result() == Result.COMPACT) return d;
			return Decision.SHOW;
		}
		lastText = null;
		return Decision.SHOW;
	}

	/** A player rule said "fold": repeats of this line within a minute become one line. */
	public synchronized Decision decideFold(String plain, long now) {
		if (plain == null || plain.isBlank()) return Decision.SHOW;
		return compact(plain, now, "own_rule");
	}

	Decision compact(String plain, long now, String group) {
		if (plain.equals(lastText) && now - lastAt <= COMPACT_WINDOW_MS) {
			lastCount++;
			lastAt = now;
			return new Decision(Result.COMPACT, lastCount, group);
		}
		lastText = plain;
		lastAt = now;
		lastCount = 1;
		return new Decision(Result.SHOW, 1, group);
	}
}
