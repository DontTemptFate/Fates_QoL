package io.github.donttemptfate.fatesofa.activity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads SkyBlock's drop announcements ("RARE DROP! …", "VERY RARE DROP! (…) (+123 ✯ Magic Find)"). */
public final class Drops {
	private Drops() {}

	public record Drop(String tier, String item, int amount, Integer magicFind) {
	}

	static final Pattern DROP = Pattern.compile(
			"^(?<tier>RARE|VERY RARE|CRAZY RARE|INSANE|PRAY TO RNGESUS|RNGESUS INCARNATE|PET|EXTRAORDINARY|INCREDIBLY RARE) DROP!\\s+"
					+ "\\(?(?<item>.+?)\\)?(?: x(?<amount>[\\d,]+))?(?:\\s+\\(\\+(?<mf>[\\d,]+)%?\\s*✯?\\s*Magic Find\\))?\\s*!?$");

	public static Drop parse(String plain) {
		if (plain == null || !plain.contains("DROP!")) return null;
		Matcher m = DROP.matcher(plain.trim());
		if (!m.matches()) return null;
		String item = m.group("item").trim();
		if (item.startsWith("(") && item.endsWith(")")) item = item.substring(1, item.length() - 1);
		int amount = m.group("amount") == null ? 1 : Integer.parseInt(m.group("amount").replace(",", ""));
		Integer mf = m.group("mf") == null ? null : Integer.valueOf(m.group("mf").replace(",", ""));
		return new Drop(m.group("tier"), item, amount, mf);
	}
}
