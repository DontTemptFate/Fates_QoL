package io.github.donttemptfate.fatesofa.game;

import io.github.donttemptfate.fatesofa.config.Values;
import io.github.donttemptfate.fatesofa.core.Alert;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** On-screen alerts: a title in the middle of the screen and an optional sound. Display only. */
public final class Alerts {
	private Alerts() {}

	public static void title(String text, int rgb, String subtitle) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			mc.gui.hud.setTimes(2, 30, 8);
			mc.gui.hud.setTitle(Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(true)));
			mc.gui.hud.setSubtitle(subtitle == null || subtitle.isEmpty() ? Component.empty() : Component.literal(subtitle));
		});
	}

	public static void ding() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 2.0f, 1.0f)));
	}

	/** Plays a sound picked in settings (any game sound id; an unknown id is silent). */
	public static void play(Values.Sound s) {
		if (s == null || s.silent()) return;
		Identifier id = Identifier.tryParse(s.id());
		if (id == null) return;
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id), s.pitch(), s.volume())));
	}

	/** Option-bundle alerts go to the screen, the speakers and chat. */
	static void registerSink() {
		Alert.sink(new Alert.Sink() {
			public void title(String title, String subtitle, int rgb) {
				Alerts.title(title, rgb, subtitle);
			}

			public void sound(Values.Sound s) {
				play(s);
			}

			public void chat(String line) {
				Minecraft mc = Minecraft.getInstance();
				mc.execute(() -> Game.send(Component.literal(line)));
			}
		});
	}
}
