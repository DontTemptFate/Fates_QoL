package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;
import io.github.donttemptfate.fatesofa.state.Island;
import io.github.donttemptfate.fatesofa.state.SkyBlockState;

/** A small card with the island, area, server and profile OFA has detected. */
public final class LocationHud extends Feature {
	public LocationHud() {
		super("location_hud", "Location card", "A small HUD card with the island, area, server and profile type OFA detected.",
				Category.HUD, false, Risk.SAFE);
	}

	@Override
	public void init() {
		Hud.register(new HudElement("location", "Location", this, HudPos.at(Anchor.TOP_LEFT, 4, 4), 10,
				this::card, () -> sample(), () -> Ofa.get().state.onSkyBlock()));
	}

	Card card() {
		SkyBlockState s = Ofa.get().state;
		Island i = s.island();
		return build(i == null ? "?" : i.display, s.area(), s.server(), s.profileType().name());
	}

	Card sample() {
		return build("Hub", "Village", "mini123AB", "NORMAL");
	}

	static Card build(String island, String area, String server, String profile) {
		Card c = new Card(0.80f, 0, 160);
		c.add(new Card.Line(java.util.List.of(Card.seg(island, Ui.Role.VALUE, Ui.WHITE)),
				java.util.List.of(Card.seg(profile, Ui.Role.LABEL, Ui.GRAY))));
		if (area != null) c.add(Card.Line.of(Card.seg(area, Ui.Role.SMALL, Ui.AQUA)));
		if (server != null) c.add(Card.Line.of(Card.seg(server, Ui.Role.LABEL, Ui.DARK)));
		return c;
	}
}
