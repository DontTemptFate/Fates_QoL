package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.IslandTrackers;
import io.github.donttemptfate.fatesofa.core.Alert;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Display;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.game.ChatBus;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;
import io.github.donttemptfate.fatesofa.state.Island;

import java.util.List;
import java.util.Map;

/** Rift collectibles: Enigma Souls (from the tab list, counted live) and Montezuma soul pieces found this visit. */
public final class RiftChecklist extends Feature {
	final Display display = new Display(this, "riftlist", Display.GROUP, 0xFFFF55FF, Display.lines("enigma", "Enigma Souls", "montezuma", "Montezuma pieces this visit"),
			List.of("enigma", "montezuma"), false);
	final Alert soul = new Alert(this, "riftlist.soulAlert", "Enigma Soul alert", true, "ENIGMA SOUL", "{found}/{total}", 0xFFFF55FF,
			"minecraft:block.amethyst_block.chime", "found", "total");
	private int extraSouls, montezuma;
	private int[] lastTab;

	public RiftChecklist() {
		super("rift_checklist", "Rift collectibles", "Enigma Souls found (and an alert for each new one) and Montezuma soul pieces this visit.",
				Category.RIFT, true, Risk.SAFE);
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		o.state.onIslandChange(i -> {
			if (i == Island.RIFT) montezuma = 0;
		});
		ChatBus.onChat(m -> guard(() -> {
			String[] c = IslandTrackers.chat(m.plain());
			if (c == null) return;
			if (c[0].equals("enigma")) {
				extraSouls++;
				int[] s = souls();
				soul.fire(Map.of("found", s == null ? "?" : Integer.toString(s[0]), "total", s == null ? "?" : Integer.toString(s[1])), o.state.island(), m.atMs());
			} else if (c[0].equals("montezuma")) {
				montezuma++;
			}
		}));
		Hud.register(new HudElement("rift_checklist", "Rift collectibles", this, HudPos.at(Anchor.TOP, 0, 70), 16, this::card, this::sample,
				() -> o.state.in(Island.RIFT)).display(display));
	}

	/** Souls from the tab list plus any found since it last updated. */
	int[] souls() {
		int[] t = IslandTrackers.enigmaSouls(Ofa.get().state.tab());
		if (t != null) {
			if (lastTab == null || t[0] != lastTab[0]) extraSouls = 0;
			lastTab = t;
			return new int[] {t[0] + extraSouls, t[1]};
		}
		return lastTab == null ? null : new int[] {lastTab[0] + extraSouls, lastTab[1]};
	}

	Card card() {
		Card c = Trackers.card(display, "RIFT", null);
		Card.KeyValues kv = Trackers.rows();
		display.each(Map.of(
				"enigma", () -> {
					int[] s = souls();
					if (s != null) Trackers.row(kv, "Enigma Souls", s[0] + "/" + s[1], s[0] >= s[1] ? Ui.GREEN : Ui.PINK);
				},
				"montezuma", () -> {
					if (montezuma > 0) Trackers.row(kv, "Montezuma pieces", Integer.toString(montezuma), Ui.AQUA);
				}));
		if (kv.isEmpty()) return null;
		c.add(kv);
		return c;
	}

	Card sample() {
		Card c = Trackers.card(display, "RIFT", null);
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Enigma Souls", "34/52", Ui.PINK);
		c.add(kv);
		return c;
	}
}
