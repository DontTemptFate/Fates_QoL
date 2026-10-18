package io.github.donttemptfate.fatesofa.core;

import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.config.Values;
import io.github.donttemptfate.fatesofa.state.Island;

import java.util.List;
import java.util.Map;

/**
 * The "Alert" option bundle: a title and subtitle (templates), a sound, an optional chat line, a
 * cooldown and where it may fire. The feature calls {@link #fire} with its placeholder values.
 */
public final class Alert {

	/** Where alerts go (set by the client at start-up; tests leave the default, which records). */
	public interface Sink {
		void title(String title, String subtitle, int rgb);

		void sound(Values.Sound s);

		void chat(String line);
	}

	private static final Sink NOOP = new Sink() {
		public void title(String t, String s, int rgb) {
		}

		public void sound(Values.Sound s) {
		}

		public void chat(String line) {
		}
	};
	private static volatile Sink sink = NOOP;

	public static void sink(Sink s) {
		sink = s == null ? NOOP : s;
	}

	private final Feature f;
	public final Setting<Boolean> on;
	public final Setting<Boolean> showTitle;
	public final Setting<String> title;
	public final Setting<String> subtitle;
	public final Setting<Integer> color;
	public final Setting<String> sound;
	public final Setting<Boolean> chat;
	public final Setting<String> chatText;
	public final Setting<Integer> cooldown;
	public final Setting<String> onlyOn;
	private long lastAt = Long.MIN_VALUE / 2;

	public Alert(Feature f, String prefix, String group, String titleDef, String subtitleDef, int colorDef, String soundDef, String... placeholders) {
		this(f, prefix, group, true, titleDef, subtitleDef, colorDef, soundDef, placeholders);
	}

	/** {@code defaultOn}: whether this alert is on before the player touches it. */
	public Alert(Feature f, String prefix, String group, boolean defaultOn, String titleDef, String subtitleDef, int colorDef, String soundDef,
			String... placeholders) {
		this.f = f;
		on = f.setting(Setting.bool(prefix + ".on", group, defaultOn).group(group).help("Switch this alert on or off."));
		showTitle = f.setting(Setting.bool(prefix + ".title.on", "Title on screen", true).group(group));
		title = f.setting(Setting.template(prefix + ".title", "Title text", titleDef, placeholders).group(group));
		subtitle = f.setting(Setting.template(prefix + ".subtitle", "Subtitle text", subtitleDef, placeholders).group(group).advanced());
		color = f.setting(Setting.color(prefix + ".color", "Title colour", colorDef).group(group));
		sound = f.setting(Setting.sound(prefix + ".sound", "Sound", soundDef).group(group));
		chat = f.setting(Setting.bool(prefix + ".chat.on", "Also a line in chat", false).group(group).advanced());
		chatText = f.setting(Setting.template(prefix + ".chat", "Chat text", titleDef + (subtitleDef.isEmpty() ? "" : " – " + subtitleDef), placeholders)
				.group(group).advanced());
		cooldown = f.setting(Setting.integer(prefix + ".cooldown", "Wait between alerts (s)", 0, 0, 600).group(group).advanced());
		onlyOn = f.setting(Setting.islands(prefix + ".onlyOn", "Only on", "").group(group).advanced());
	}

	/** Shows the alert now unless it's on cooldown or not wanted here. Returns whether it fired. */
	public boolean fire(Map<String, ?> vars, Island here, long nowMs) {
		return fire(vars, here, nowMs, -1);
	}

	/** Like {@link #fire(Map, Island, long)} with the title in {@code rgb} instead of the set colour (-1 = set colour). */
	public boolean fire(Map<String, ?> vars, Island here, long nowMs, int rgb) {
		if (f.store() == null || !f.active() || !f.on(on)) return false;
		if (!Values.allows(f.get(onlyOn), here)) return false;
		if (nowMs - lastAt < f.get(cooldown) * 1000L) return false;
		lastAt = nowMs;
		Sink s = sink;
		if (f.on(showTitle)) s.title(Values.fill(f.get(title), vars), Values.fill(f.get(subtitle), vars), rgb >= 0 ? rgb : f.get(color) & 0xFFFFFF);
		Values.Sound snd = Values.sound(f.get(sound));
		if (!snd.silent()) s.sound(snd);
		if (f.on(chat)) s.chat(Values.fill(f.get(chatText), vars));
		return true;
	}

	/** On (and its feature active)? For features that should skip work when nobody wants the alert. */
	public boolean wanted() {
		return f.store() != null && f.active() && f.on(on);
	}

	public List<String> placeholders() {
		return title.placeholders;
	}
}
