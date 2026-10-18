package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatescommon.text.LegacyText;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.chat.ChatFilter;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Hides chat spam by group and folds repeated lines. Only Hypixel's own system messages are touched. */
public final class ChatFeature extends Feature {
	static final Set<String> ON_BY_DEFAULT = Set.of("lobby_joins", "watchdog", "ability_cooldown", "implosion", "blocks_in_way", "guild_exp");

	private final Map<String, io.github.donttemptfate.fatesofa.core.ChatGroup> groups = new LinkedHashMap<>();
	final Setting<Boolean> foldAll = setting(Setting.bool("chat.foldAll", "Fold any repeated message", false).group("Message groups")
			.help("Any line that repeats within a minute is hidden after the first (not only the groups above)."));
	final Setting<String> ownRules = setting(Setting.rules("chat.rules", "Your own rules", "")
			.group("Your own rules")
			.help("One rule per line: text => hide, fold or alert. Text matches anywhere (any case); /like this/ is a regular expression.")
			.keywords("custom filter regex")
			.check(t -> io.github.donttemptfate.fatesofa.chat.CustomRules.parseNow(t).problems()));
	final io.github.donttemptfate.fatesofa.core.Alert ruleAlert = new io.github.donttemptfate.fatesofa.core.Alert(this, "chat.rules.alert", "Your own rules",
			"{message}", "", 0xFFFFFF55, "minecraft:block.note_block.pling|1|1.5", "message");
	private final io.github.donttemptfate.fatesofa.chat.CustomRules custom = new io.github.donttemptfate.fatesofa.chat.CustomRules();
	private int hiddenCount;

	public ChatFeature() {
		super("chat_filter", "Chat cleanup", "Hide chat spam by group (lobby joins, Watchdog, cooldown spam…) and fold repeated lines.",
				Category.CHAT, true, Risk.SAFE);
		for (Map.Entry<String, String> e : ChatFilter.GROUPS.entrySet()) {
			String def = ON_BY_DEFAULT.contains(e.getKey()) ? ChatFilter.defaultMode(e.getKey()) : "show";
			groups.put(e.getKey(), new io.github.donttemptfate.fatesofa.core.ChatGroup(this, "chat.group." + e.getKey(), e.getValue(), "Message groups", def));
		}
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		o.chat.groupModes(g -> {
			var cg = groups.get(g);
			return cg == null ? "show" : cg.action();
		});
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			if (overlay) return true;
			return guard(() -> {
				if (!o.state.onHypixel()) return true;
				String plain = LegacyText.clean(message.getString());
				var rule = custom.match(get(ownRules), plain);
				if (rule != null) {
					switch (rule.action()) {
						case HIDE -> {
							hiddenCount++;
							return false;
						}
						case ALERT -> ruleAlert.fire(java.util.Map.of("message", plain.length() > 40 ? plain.substring(0, 40) + "…" : plain),
								o.state.island(), System.currentTimeMillis());
						case FOLD -> {
							ChatFilter.Decision fd = o.chat.decideFold(plain, System.currentTimeMillis());
							if (fd.result() == ChatFilter.Result.COMPACT) {
								hiddenCount++;
								io.github.donttemptfate.fatesofa.game.ChatFold.fold(message, fd.count());
								return false;
							}
							return true;
						}
					}
				}
				o.chat.compactAll(on(foldAll));
				ChatFilter.Decision d = o.chat.decide(plain, System.currentTimeMillis());
				if (d.group() != null && groups.containsKey(d.group())) {
					var snd = groups.get(d.group()).sound();
					if (!snd.silent() && d.result() != ChatFilter.Result.COMPACT) io.github.donttemptfate.fatesofa.game.Alerts.play(snd);
				}
				if (d.result() == ChatFilter.Result.SHOW) return true;
				hiddenCount++;
				// a repeat: shown once more as the latest line, with how many times it came ("×3")
				if (d.result() == ChatFilter.Result.COMPACT) io.github.donttemptfate.fatesofa.game.ChatFold.fold(message, d.count());
				return false;
			}, true);
		});
	}

	public int hiddenCount() {
		return hiddenCount;
	}
}
