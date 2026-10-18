package io.github.donttemptfate.fatesofa.chat;

import io.github.donttemptfate.fatesofa.config.Values;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The player's own chat rules, one per line: {@code text => action}. The text matches anywhere in the
 * message, ignoring case; wrap it in slashes for a regular expression ({@code /^\[NPC\]/ => hide}).
 * Actions: hide, fold (repeats become one line), alert (title + sound). A line without "=>" hides.
 */
public final class CustomRules {
	public enum Action { HIDE, FOLD, ALERT }

	public record Rule(String source, Pattern pattern, String contains, Action action) {
		public boolean matches(String plain) {
			if (pattern != null) return pattern.matcher(plain).find();
			return plain.toLowerCase(Locale.ROOT).contains(contains);
		}
	}

	/** A parsed list plus the lines that couldn't be read (shown in settings). */
	public record Parsed(List<Rule> rules, List<String> problems) {
	}

	private String lastText;
	private Parsed last = new Parsed(List.of(), List.of());

	/** Parses the setting's text (cached until it changes). */
	public synchronized Parsed parse(String text) {
		if (text != null && text.equals(lastText)) return last;
		lastText = text;
		last = parseNow(text);
		return last;
	}

	public static Parsed parseNow(String text) {
		List<Rule> out = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		for (String line : Values.rules(text)) {
			String match = line, act = "hide";
			int arrow = line.lastIndexOf("=>");
			if (arrow >= 0) {
				match = line.substring(0, arrow).trim();
				act = line.substring(arrow + 2).trim().toLowerCase(Locale.ROOT);
			}
			Action a = switch (act) {
				case "hide", "" -> Action.HIDE;
				case "fold", "compact" -> Action.FOLD;
				case "alert", "title" -> Action.ALERT;
				default -> null;
			};
			if (a == null) {
				problems.add(line + "  (unknown action \"" + act + "\"; use hide, fold or alert)");
				continue;
			}
			if (match.isEmpty()) {
				problems.add(line + "  (nothing to match)");
				continue;
			}
			if (match.length() >= 2 && match.startsWith("/") && match.endsWith("/")) {
				try {
					out.add(new Rule(line, Pattern.compile(match.substring(1, match.length() - 1)), null, a));
				} catch (PatternSyntaxException e) {
					problems.add(line + "  (bad pattern)");
				}
			} else {
				out.add(new Rule(line, null, match.toLowerCase(Locale.ROOT), a));
			}
		}
		return new Parsed(List.copyOf(out), List.copyOf(problems));
	}

	/** The first rule matching the message, or null. */
	public Rule match(String text, String plain) {
		for (Rule r : parse(text).rules()) if (r.matches(plain)) return r;
		return null;
	}
}
