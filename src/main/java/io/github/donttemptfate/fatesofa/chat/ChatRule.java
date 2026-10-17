package io.github.donttemptfate.fatesofa.chat;

import java.util.regex.Pattern;

/**
 * One chat filter rule: a group the player can switch, a pattern on the message's plain text, and
 * what to do with a match. Rules ship as data so a changed Hypixel message is fixed without an update.
 */
public record ChatRule(String group, Pattern pattern, Action action) {
	public enum Action { HIDE, COMPACT }

	public static ChatRule of(String group, String regex, Action action) {
		return new ChatRule(group, Pattern.compile(regex), action);
	}

	public boolean matches(String plain) {
		return pattern.matcher(plain).find();
	}
}
