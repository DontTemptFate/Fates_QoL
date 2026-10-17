package io.github.donttemptfate.fatesofa.mixin.version;

import io.github.donttemptfate.fatesofa.features.ProtectionFeature;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The drop key (26.3: MultiPlayerGameMode.dropItem). Protection can stop the player's own drop, never cause one. */
@Mixin(MultiPlayerGameMode.class)
public abstract class DropMixin {
	@Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
	private void fatesofa$protectDrop(LocalPlayer player, boolean all, CallbackInfo ci) {
		if (ProtectionFeature.blockDrop(player)) ci.cancel();
	}
}
