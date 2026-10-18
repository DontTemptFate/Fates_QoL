package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.text.Strip;
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

	private final Map<String, Setting<Boolean>> groups = new LinkedHashMap<>();
	final Setting<Boolean> foldAll = setting(Setting.bool("chat.foldAll", "Fold any repeated message", false)
			.help("Any line that repeats within a minute is hidden after the first (not only the groups above)."));
	private int hiddenCount;

	public ChatFeature() {
		super("chat_filter", "Chat cleanup", "Hide chat spam by group (lobby joins, Watchdog, cooldown spam…) and fold repeated lines.",
				Category.CHAT, true, Risk.SAFE);
		for (Map.Entry<String, String> e : ChatFilter.GROUPS.entrySet()) {
			groups.put(e.getKey(), setting(Setting.bool("chat.hide." + e.getKey(), e.getValue(), ON_BY_DEFAULT.contains(e.getKey()))
					.keywords("hide spam filter")));
		}
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		o.chat.groups(g -> {
			Setting<Boolean> s = groups.get(g);
			return s != null && on(s);
		});
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			if (overlay) return true;
			return guard(() -> {
				if (!o.state.onHypixel()) return true;
				o.chat.compactAll(on(foldAll));
				ChatFilter.Decision d = o.chat.decide(Strip.clean(message.getString()), System.currentTimeMillis());
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
