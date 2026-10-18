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
	final io.github.donttemptfate.fatesofa.core.Display display;

	public LocationHud() {
		super("location_hud", "Location card", "A small HUD card with the island, area, server and profile type OFA detected.",
				Category.HUD, false, Risk.SAFE);
		var all = new java.util.LinkedHashMap<String, String>();
		all.put("island", "Island");
		all.put("profile", "Profile type");
		all.put("area", "Area");
		all.put("server", "Server");
		display = new io.github.donttemptfate.fatesofa.core.Display(this, "location", io.github.donttemptfate.fatesofa.core.Display.GROUP, Ui.AQUA, all,
				java.util.List.of("island", "profile", "area", "server"), false);
	}

	@Override
	public void init() {
		Hud.register(new HudElement("location", "Location", this, HudPos.at(Anchor.BOTTOM_LEFT, 4, -4), 10,
				this::card, () -> sample(), () -> Ofa.get().state.onSkyBlock()).display(display));
	}

	Card card() {
		SkyBlockState s = Ofa.get().state;
		Island i = s.island();
		return build(i == null ? "?" : i.display, s.area(), s.server(), s.profileType().name());
	}

	Card sample() {
		return build("Hub", "Village", "mini123AB", "NORMAL");
	}

	Card build(String island, String area, String server, String profile) {
		Card c = new Card(display.alpha(), 0, 160);
		int accent = display.accent(System.currentTimeMillis());
		boolean profileShown = false;
		for (String line : display.lines()) {
			switch (line) {
				case "island" -> {
					boolean withProfile = display.lines().indexOf("profile") == display.lines().indexOf("island") + 1;
					c.add(new Card.Line(java.util.List.of(Card.seg(island, Ui.Role.VALUE, Ui.WHITE)),
							withProfile ? java.util.List.of(Card.seg(profile, Ui.Role.LABEL, Ui.GRAY)) : java.util.List.of()));
					profileShown |= withProfile;
				}
				case "profile" -> {
					if (!profileShown) c.add(Card.Line.of(Card.seg(profile, Ui.Role.LABEL, Ui.GRAY)));
				}
				case "area" -> {
					if (area != null) c.add(Card.Line.of(Card.seg(area, Ui.Role.SMALL, accent)));
				}
				case "server" -> {
					if (server != null) c.add(Card.Line.of(Card.seg(server, Ui.Role.LABEL, Ui.DARK)));
				}
				default -> {
				}
			}
		}
		return c;
	}
}
