package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatescommon.text.Fmt;
import io.github.donttemptfate.fatescommon.ui.Card;
import io.github.donttemptfate.fatescommon.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.SkillXp;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.config.Values;
import io.github.donttemptfate.fatesofa.core.Alert;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Display;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.core.Tracker;
import io.github.donttemptfate.fatesofa.game.ChatBus;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Skill XP per hour (from the action bar), time to the next level, and level-up alerts for skills, pets and collections. */
public final class SkillTracker extends Feature {
	static final Map<String, String> SKILL_LINES = new LinkedHashMap<>();

	static {
		for (String s : SkillXp.SKILLS) SKILL_LINES.put(s, s);
	}

	static final Pattern SKILL_UP = Pattern.compile("^\\s*SKILL LEVEL UP (?<skill>\\w+) (?:[IVXLC\\d]+ ➜ )?(?<lvl>[IVXLC\\d]+)\\s*$");
	static final Pattern PET_UP = Pattern.compile("^Your (?<pet>.+?) leveled up to level (?<lvl>\\d+)!$");
	static final Pattern COLL_UP = Pattern.compile("^\\s*COLLECTION LEVEL UP (?<name>.+?) (?<tier>[IVXLC]+)\\s*$");
	static final Pattern PET_NOW = Pattern.compile("^(?:Autopet equipped your \\[Lvl \\d+] (?<a>.+?)! VIEW RULE|You summoned your (?<s>.+?)!)$");

	final Display display = new Display(this, "skilltracker", Display.GROUP, 0xFF55FFFF,
			Display.lines("gained", "XP gained", "per_hour", "XP per hour", "progress", "Progress to next level", "to_level", "Time to next level",
					"pet", "Current pet"),
			List.of("gained", "per_hour", "to_level", "pet"));
	final Setting<String> skills = setting(Setting.lines("skilltracker.skills", "Skills tracked", SKILL_LINES, SkillXp.SKILLS).group("Display")
			.help("Untick skills you don't want on the card."));
	final Setting<Integer> hideAfter = setting(Setting.integer("skilltracker.hideAfter", "Hide a skill after no XP for (min)", 10, 1, 120).group("Display"));
	final Tracker tracking = new Tracker(this, "skilltracker.tracker", false);
	final Alert skillUp = new Alert(this, "skilltracker.skillUp", "Skill level-up alert", true, "{skill} {level}", "Skill level up!", 0xFF55FFFF,
			"minecraft:entity.player.levelup", "skill", "level");
	final Alert petUp = new Alert(this, "skilltracker.petUp", "Pet level-up alert", false, "{pet}", "level {level}", 0xFFFFAA00,
			"minecraft:entity.experience_orb.pickup|1|1.4", "pet", "level");
	final Alert collUp = new Alert(this, "skilltracker.collUp", "Collection level-up alert", false, "{collection} {tier}", "Collection level up!",
			0xFFFFFF55, "minecraft:entity.player.levelup|0.6|1.6", "collection", "tier");

	public final SkillXp xp = new SkillXp();
	private volatile String pet;
	private int tick;

	public SkillTracker() {
		super("skill_tracker", "Skill XP tracker", "XP per hour for each skill (from the action bar), time to the next level, your current pet, "
				+ "and level-up alerts for skills, pets and collections.", Category.TRACKERS, true, Risk.SAFE);
	}

	@Override
	public List<Action> actions() {
		return List.of(new Action("Reset counts", xp::reset));
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		ChatBus.onActionBar(m -> guard(() -> {
			if (o.state.onSkyBlock()) xp.actionBar(m.plain(), m.atMs());
		}));
		ChatBus.onChat(m -> guard(() -> {
			String t = m.plain();
			Matcher s = SKILL_UP.matcher(t);
			if (s.matches()) skillUp.fire(Map.of("skill", s.group("skill"), "level", s.group("lvl")), o.state.island(), m.atMs());
			Matcher p = PET_UP.matcher(t);
			if (p.matches()) {
				pet = p.group("pet") + " (" + p.group("lvl") + ")";
				petUp.fire(Map.of("pet", p.group("pet"), "level", p.group("lvl")), o.state.island(), m.atMs());
			}
			Matcher c = COLL_UP.matcher(t);
			if (c.matches()) collUp.fire(Map.of("collection", c.group("name"), "tier", c.group("tier")), o.state.island(), m.atMs());
			Matcher n = PET_NOW.matcher(t);
			if (n.matches()) pet = n.group("a") != null ? n.group("a") : n.group("s");
		}));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			if (++tick % 20 == 0) tracking.maintain(xp.all, xp::reset, o.state.island(), System.currentTimeMillis());
		}));
		Hud.register(new HudElement("skill_tracker", "Skill XP", this, HudPos.at(Anchor.RIGHT, -4, -80), 18, this::card, this::sample,
				() -> o.state.onSkyBlock() && !shown(System.currentTimeMillis()).isEmpty()).display(display));
	}

	List<SkillXp.Skill> shown(long now) {
		List<String> want = Values.lines(get(skills), skills.options);
		List<SkillXp.Skill> out = new ArrayList<>();
		for (SkillXp.Skill s : xp.skills().values()) {
			if (!want.contains(s.name)) continue;
			if (now - s.lastAt > get(hideAfter) * 60_000L) continue;
			out.add(s);
		}
		return out;
	}

	Card card() {
		long now = System.currentTimeMillis();
		Card c = Trackers.card(display, "SKILLS", null);
		Card.KeyValues kv = Trackers.rows();
		for (SkillXp.Skill s : shown(now)) {
			List<String> parts = new ArrayList<>();
			for (String id : display.lines()) {
				switch (id) {
					case "gained" -> parts.add("+" + Trackers.num(display, s.gained));
					case "per_hour" -> {
						double ph = s.perHour();
						if (ph > 0 && tracking.showPerHour()) parts.add(Trackers.num(display, ph) + "/h");
					}
					case "progress" -> {
						if (s.last != null && !Double.isNaN(s.last.percent())) parts.add(Fmt.trimmed(Math.round(s.last.percent() * 10) / 10.0) + "%");
					}
					case "to_level" -> {
						long ms = s.toLevelMs();
						if (ms > 0) parts.add("lvl in " + Fmt.duration(ms));
					}
					default -> {
					}
				}
			}
			Trackers.row(kv, s.name, String.join(" · ", parts), Ui.AQUA);
		}
		if (display.shows("pet") && pet != null) Trackers.row(kv, "Pet", pet, Ui.GOLD);
		if (tracking.showUptime() && xp.all.started()) Trackers.row(kv, "Tracked for", Fmt.duration(xp.all.activeMs()), Ui.GRAY);
		if (kv.isEmpty()) return null;
		c.add(kv);
		return c;
	}

	Card sample() {
		Card c = Trackers.card(display, "SKILLS", null);
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Farming", "+412k · 1.6M/h · lvl in 2h 10m", Ui.AQUA);
		Trackers.row(kv, "Pet", "Elephant (100)", Ui.GOLD);
		c.add(kv);
		return c;
	}
}
