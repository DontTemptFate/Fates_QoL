package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.data.Price;
import io.github.donttemptfate.fatesofa.data.RepoItem;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared bits for tracker HUD cards: the card shape and item-name → value lookups. */
final class Trackers {
	private Trackers() {}

	static Card card(String title, String right) {
		Card c = new Card(0.80f, 110, 190);
		c.add(new Card.Line(List.of(Card.seg(title, Ui.Role.LABEL, Ui.accent())),
				right == null ? List.of() : List.of(Card.seg(right, Ui.Role.LABEL, Ui.GRAY))));
		return c;
	}

	static Card.KeyValues rows() {
		return new Card.KeyValues(1);
	}

	static void row(Card.KeyValues kv, String key, String value, int color) {
		kv.row(Card.seg(key, Ui.Role.SMALL, Ui.GRAY), Card.seg(value, Ui.Role.SMALL, color));
	}

	private static Map<String, String> nameToKey = new HashMap<>();
	private static int builtFor = -1;

	/** NEU key for an item's display name ("Revenant Viscera" → REVENANT_VISCERA), or null. */
	static String keyForName(String name) {
		Ofa o = Ofa.get();
		var repo = o.repo.items();
		if (repo.size() != builtFor) {
			Map<String, String> m = new HashMap<>();
			for (RepoItem it : repo.all()) m.putIfAbsent(it.plainName().toLowerCase(Locale.ROOT), it.key());
			nameToKey = m;
			builtFor = repo.size();
		}
		return nameToKey.get(name.toLowerCase(Locale.ROOT).trim());
	}

	/** Value of {@code amount} of a named item, or NaN. */
	static double valueOf(String name, int amount) {
		String key = keyForName(name);
		if (key == null) return Double.NaN;
		Price p = Ofa.get().prices.value(key, System.currentTimeMillis());
		return p == null ? Double.NaN : p.value() * amount;
	}

	static String perHour(double v) {
		return Fmt.shortNum(v) + "/h";
	}
}
