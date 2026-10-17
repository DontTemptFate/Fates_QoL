package io.github.donttemptfate.fatesofa.mixin;

import io.github.donttemptfate.fatesofa.record.PlayRecorder;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the play recorder see titles and subtitles (it only reads them; nothing is changed). */
@Mixin(Hud.class)
public abstract class HudTitleMixin {
	@Inject(method = "setTitle(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
	private void fatesofa$title(Component c, CallbackInfo ci) {
		if (PlayRecorder.on()) PlayRecorder.title("title", c);
	}

	@Inject(method = "setSubtitle(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
	private void fatesofa$subtitle(Component c, CallbackInfo ci) {
		if (PlayRecorder.on()) PlayRecorder.title("subtitle", c);
	}
}
