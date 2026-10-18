package io.github.donttemptfate.fatesofa.core;

import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.config.Values;

import java.util.List;
import java.util.Map;

/** The "Chat group" option bundle: what happens to one kind of chat message (show / hide / fold), plus a sound. */
public final class ChatGroup {
	public static final List<String> ACTIONS = List.of("show", "hide", "fold");

	private final Feature f;
	public final Setting<String> action;
	public final Setting<String> sound;

	public ChatGroup(Feature f, String prefix, String label, String group, String def) {
		this.f = f;
		action = f.setting(Setting.choice(prefix, label, def, ACTIONS).group(group).keywords("hide spam filter fold")
				.labels(Map.of("show", "Show", "hide", "Hide", "fold", "Fold repeats")));
		sound = f.setting(Setting.sound(prefix + ".sound", label + ": sound", "").group(group).advanced());
	}

	public String action() {
		return f.store() == null ? action.def : f.get(action);
	}

	public Values.Sound sound() {
		return Values.sound(f.store() == null ? sound.def : f.get(sound));
	}
}
