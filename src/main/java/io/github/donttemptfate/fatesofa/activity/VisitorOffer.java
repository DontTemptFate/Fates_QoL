package io.github.donttemptfate.fatesofa.activity;

import io.github.donttemptfate.fatescommon.text.Fmt;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A garden visitor's offer, read from the "Accept Offer" button's lore: items wanted, copper and item rewards. */
public record VisitorOffer(List<Line> wanted, double copper, List<Line> rewards) {
	public record Line(String name, double amount) {
	}

	static final Pattern AMOUNT = Pattern.compile("^(?<name>.+?)(?: x(?<n>[\\d,.]+[kM]?))?$");
	static final Pattern COPPER = Pattern.compile("^\\+(?<n>[\\d,.]+) Copper$");

	public static VisitorOffer parse(List<String> lore) {
		List<Line> wanted = new ArrayList<>(), rewards = new ArrayList<>();
		double copper = 0;
		int section = 0; // 1 = items required, 2 = rewards
		for (String raw : lore) {
			String l = raw.trim();
			if (l.isEmpty()) continue;
			if (l.startsWith("Items Required")) {
				section = 1;
				continue;
			}
			if (l.startsWith("Rewards")) {
				section = 2;
				continue;
			}
			if (l.startsWith("Click to") || l.startsWith("Missing items")) section = 0;
			if (section == 1) {
				Matcher m = AMOUNT.matcher(l);
				if (m.matches()) wanted.add(new Line(m.group("name").trim(), m.group("n") == null ? 1 : Fmt.parse(m.group("n"))));
			} else if (section == 2) {
				Matcher c = COPPER.matcher(l);
				if (c.matches()) {
					copper += Fmt.parse(c.group("n"));
					continue;
				}
				if (l.startsWith("+")) continue; // farming xp, garden xp, bits
				Matcher m = AMOUNT.matcher(l);
				if (m.matches()) rewards.add(new Line(m.group("name").trim(), m.group("n") == null ? 1 : Fmt.parse(m.group("n"))));
			}
		}
		return wanted.isEmpty() ? null : new VisitorOffer(List.copyOf(wanted), copper, List.copyOf(rewards));
	}
}
