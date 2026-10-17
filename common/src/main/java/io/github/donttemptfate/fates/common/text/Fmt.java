package io.github.donttemptfate.fates.common.text;

import java.util.Locale;

/** Number, coin and time formatting used everywhere (1.2M, 950k, 3h 12m). */
public final class Fmt {
	private Fmt() {}

	/** Short form: 999, 1.2k, 34k, 1.25M, 120M, 1.4B. Negative numbers keep their sign. */
	public static String shortNum(double v) {
		if (Double.isNaN(v) || Double.isInfinite(v)) return "?";
		String sign = v < 0 ? "-" : "";
		double a = Math.abs(v);
		if (a < 1_000) return sign + trim(a, a < 10 && a != Math.floor(a) ? 1 : 0);
		String[] units = {"k", "M", "B", "T"};
		double d = a;
		int u = -1;
		while (d >= 1000 && u < units.length - 1) {
			d /= 1000;
			u++;
		}
		// 999.95k would round to "1000k": move up a unit instead
		if (Math.round(d * 10) >= 10_000 && u < units.length - 1) {
			d /= 1000;
			u++;
		}
		int decimals = d < 10 ? 2 : d < 100 ? 1 : 0;
		return sign + trim(d, decimals) + units[u];
	}

	/** Like {@link #shortNum} with a leading + for positive values (profits). */
	public static String signedShort(double v) {
		return (v > 0 ? "+" : "") + shortNum(v);
	}

	/** Full number with thousands separators: 1,234,567. */
	public static String full(long v) {
		return String.format(Locale.ROOT, "%,d", v);
	}

	public static String percent(double fraction) {
		double p = fraction * 100;
		return trim(p, Math.abs(p) < 10 ? 1 : 0) + "%";
	}

	/** 45s, 3m 12s, 2h 05m, 3d 4h. */
	public static String duration(long ms) {
		if (ms < 0) ms = 0;
		long s = ms / 1000;
		if (s < 60) return s + "s";
		long m = s / 60;
		if (m < 60) return m + "m " + pad(s % 60) + "s";
		long h = m / 60;
		if (h < 48) return h + "h " + pad(m % 60) + "m";
		return (h / 24) + "d " + (h % 24) + "h";
	}

	/** Timer style: 1:05, 12:30, 1:02:03. */
	public static String clock(long ms) {
		long s = Math.max(0, ms) / 1000;
		long h = s / 3600, m = (s / 60) % 60, sec = s % 60;
		return h > 0 ? h + ":" + pad(m) + ":" + pad(sec) : m + ":" + pad(sec);
	}

	/** Seconds with one decimal: 4.3s. */
	public static String seconds1(long ms) {
		return String.format(Locale.ROOT, "%.1fs", ms / 1000.0);
	}

	private static String pad(long v) {
		return v < 10 ? "0" + v : Long.toString(v);
	}

	/** Up to two decimals, trailing zeros dropped: 1.5, 2, 0.25. */
	public static String trimmed(double v) {
		return trim(v, 2);
	}

	static String trim(double v, int decimals) {
		String s = String.format(Locale.ROOT, "%." + decimals + "f", v);
		if (s.indexOf('.') >= 0) {
			s = s.replaceAll("0+$", "");
			if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	/**
	 * Reads a number the way Hypixel writes it: "1,234,567", "1.2M", "950k", "12.5", "3B".
	 * Returns NaN when there is no number.
	 */
	public static double parse(String s) {
		if (s == null) return Double.NaN;
		String t = s.trim().replace(",", "").toLowerCase(Locale.ROOT);
		if (t.isEmpty()) return Double.NaN;
		double mult = 1;
		char last = t.charAt(t.length() - 1);
		switch (last) {
			case 'k' -> mult = 1e3;
			case 'm' -> mult = 1e6;
			case 'b' -> mult = 1e9;
			case 't' -> mult = 1e12;
			default -> mult = 1;
		}
		if (mult != 1) t = t.substring(0, t.length() - 1);
		try {
			return Double.parseDouble(t) * mult;
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	/** Roman numerals I–L (Hypixel writes skill, enchant and tier levels this way). 0 when not one. */
	public static int roman(String s) {
		if (s == null || s.isEmpty()) return 0;
		int total = 0, prev = 0;
		for (int i = s.length() - 1; i >= 0; i--) {
			int v = switch (s.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				case 'C' -> 100;
				default -> -1;
			};
			if (v < 0) return 0;
			total += v < prev ? -v : v;
			prev = Math.max(prev, v);
		}
		return total;
	}

	/** Reads "12" or "XII" as a level. 0 when neither. */
	public static int level(String s) {
		if (s == null) return 0;
		String t = s.trim();
		if (t.matches("\\d{1,4}")) return Integer.parseInt(t);
		return roman(t);
	}
}
