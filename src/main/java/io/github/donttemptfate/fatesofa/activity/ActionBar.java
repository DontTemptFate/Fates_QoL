package io.github.donttemptfate.fatesofa.activity;

import io.github.donttemptfate.fates.common.text.Fmt;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Health, defense, mana and overflow mana from SkyBlock's action bar ("1,234/1,500❤   500❈ Defense   300/300✎ Mana"). */
public record ActionBar(double health, double maxHealth, double defense, double mana, double maxMana, double overflow) {
	static final Pattern HEALTH = Pattern.compile("(?<cur>[\\d,]+)/(?<max>[\\d,]+)❤");
	static final Pattern DEFENSE = Pattern.compile("(?<d>[\\d,]+)❈ Defense");
	static final Pattern MANA = Pattern.compile("(?<cur>[\\d,]+)/(?<max>[\\d,]+)✎(?: Mana)?");
	static final Pattern OVERFLOW = Pattern.compile("(?<o>[\\d,]+)ʬ");

	public static ActionBar parse(String plain) {
		if (plain == null) return null;
		Matcher h = HEALTH.matcher(plain), d = DEFENSE.matcher(plain), m = MANA.matcher(plain), o = OVERFLOW.matcher(plain);
		boolean hh = h.find(), mm = m.find();
		if (!hh && !mm) return null;
		return new ActionBar(hh ? Fmt.parse(h.group("cur")) : Double.NaN, hh ? Fmt.parse(h.group("max")) : Double.NaN,
				d.find() ? Fmt.parse(d.group("d")) : Double.NaN, mm ? Fmt.parse(m.group("cur")) : Double.NaN, mm ? Fmt.parse(m.group("max")) : Double.NaN,
				o.find() ? Fmt.parse(o.group("o")) : 0);
	}
}
