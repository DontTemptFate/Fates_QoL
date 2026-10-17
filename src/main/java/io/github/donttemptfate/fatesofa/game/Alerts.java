package io.github.donttemptfate.fatesofa.game;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;

/** On-screen alerts: a title in the middle of the screen and an optional sound. Display only. */
public final class Alerts {
	private Alerts() {}

	public static void title(String text, int rgb, String subtitle) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			mc.gui.hud.setTimes(2, 30, 8);
			mc.gui.hud.setTitle(Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(true)));
			mc.gui.hud.setSubtitle(subtitle == null ? Component.empty() : Component.literal(subtitle));
		});
	}

	public static void ding() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 2.0f, 1.0f)));
	}
}
