package io.github.donttemptfate.fatesofa.activity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The SkyBlock calendar: 1 SkyBlock day = 20 real minutes, 31 days a month, 12 months a year
 * (124 real hours). Year 1 began on 11 June 2019, 17:55 UTC. Used for event timers.
 */
public final class SkyBlockTime {
	private SkyBlockTime() {}

	public static final long EPOCH_MS = 1_560_275_700_000L;
	public static final long DAY_MS = 20 * 60_000L;
	public static final long MONTH_MS = 31 * DAY_MS;
	public static final long YEAR_MS = 12 * MONTH_MS;
	public static final long HOUR_MS = DAY_MS / 24;

	public static final String[] MONTHS = {"Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer",
			"Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter"};

	/** A SkyBlock date: year (from 1), month 0–11, day 1–31, hour 0–23, minute 0–59. */
	public record Date(int year, int month, int day, int hour, int minute) {
		public String label() {
			return MONTHS[month] + " " + day + suffix(day) + ", Year " + year;
		}
	}

	public static Date at(long realMs) {
		long t = realMs - EPOCH_MS;
		int year = (int) Math.floorDiv(t, YEAR_MS) + 1;
		long inYear = Math.floorMod(t, YEAR_MS);
		int month = (int) (inYear / MONTH_MS);
		long inMonth = inYear % MONTH_MS;
		int day = (int) (inMonth / DAY_MS) + 1;
		long inDay = inMonth % DAY_MS;
		int hour = (int) (inDay / HOUR_MS);
		int minute = (int) ((inDay % HOUR_MS) * 60 / HOUR_MS);
		return new Date(year, month, day, hour, minute);
	}

	/** Real time when a SkyBlock date starts (day 1-based, month 0-based). */
	public static long start(int year, int month, int day) {
		return EPOCH_MS + (year - 1L) * YEAR_MS + month * MONTH_MS + (day - 1L) * DAY_MS;
	}

	static String suffix(int d) {
		if (d >= 11 && d <= 13) return "th";
		return switch (d % 10) {
			case 1 -> "st";
			case 2 -> "nd";
			case 3 -> "rd";
			default -> "th";
		};
	}

	/** One upcoming (or running) event. */
	public record Event(String name, long startMs, long endMs) {
		public boolean running(long now) {
			return now >= startMs && now < endMs;
		}
	}

	/** Recurring events known from the calendar, the next occurrence of each (running ones included). */
	public static List<Event> upcoming(long now) {
		List<Event> out = new ArrayList<>();
		// real-clock events
		out.add(hourly("Jacob's Farming Contest", now, 15, 20));
		out.add(hourly("Dark Auction", now, 55, 5));
		// SkyBlock-calendar events
		out.add(yearly("Spooky Festival", now, 7, 29, 3)); // Autumn 29–31
		out.add(yearly("Season of Jerry", now, 11, 24, 3)); // Late Winter 24–26
		out.add(yearly("New Year Celebration", now, 11, 29, 3)); // Late Winter 29–31
		out.add(earliest(yearly("Traveling Zoo", now, 3, 1, 3), yearly("Traveling Zoo", now, 9, 1, 3))); // Early Summer / Early Winter 1–3
		out.add(seasonal("Hoppity's Hunt", now, 0, 3)); // the three spring months
		out.add(cult(now));
		out.sort(Comparator.comparingLong(e -> e.running(now) ? Long.MIN_VALUE + e.endMs() : e.startMs()));
		return out;
	}

	static Event hourly(String name, long now, int minute, int durationMin) {
		long hour = now - Math.floorMod(now, 3_600_000L);
		long start = hour + minute * 60_000L;
		long end = start + durationMin * 60_000L;
		if (now >= end) {
			start += 3_600_000L;
			end += 3_600_000L;
		}
		return new Event(name, start, end);
	}

	static Event yearly(String name, long now, int month, int day, int days) {
		int year = at(now).year();
		for (int y = year; y <= year + 1; y++) {
			long s = start(y, month, day);
			long e = s + days * DAY_MS;
			if (now < e) return new Event(name, s, e);
		}
		return new Event(name, start(year + 1, month, day), start(year + 1, month, day) + days * DAY_MS);
	}

	static Event seasonal(String name, long now, int firstMonth, int months) {
		int year = at(now).year();
		for (int y = year; y <= year + 1; y++) {
			long s = start(y, firstMonth, 1);
			long e = s + months * MONTH_MS;
			if (now < e) return new Event(name, s, e);
		}
		return new Event(name, start(year + 1, firstMonth, 1), start(year + 1, firstMonth, 1) + months * MONTH_MS);
	}

	/** Cult of the Fallen Star: days 7, 14, 21 and 28 of every month, 00:00–06:00 SkyBlock time. */
	static Event cult(long now) {
		Date d = at(now);
		for (int k = 0; k < 3; k++) {
			int m = (d.month() + k) % 12;
			int y = d.year() + (d.month() + k >= 12 ? 1 : 0);
			for (int day : new int[] {7, 14, 21, 28}) {
				long s = start(y, m, day);
				long e = s + 6 * HOUR_MS;
				if (now < e) return new Event("Cult of the Fallen Star", s, e);
			}
		}
		return new Event("Cult of the Fallen Star", now + 7 * DAY_MS, now + 7 * DAY_MS + 6 * HOUR_MS);
	}

	static Event earliest(Event a, Event b) {
		long now = System.currentTimeMillis();
		if (a.running(now)) return a;
		if (b.running(now)) return b;
		return a.startMs() <= b.startMs() ? a : b;
	}
}
