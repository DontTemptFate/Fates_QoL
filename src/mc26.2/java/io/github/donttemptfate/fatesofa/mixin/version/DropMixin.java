package io.github.donttemptfate.fatesofa.mixin.version;

import io.github.donttemptfate.fatesofa.features.ProtectionFeature;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The drop key (26.2: LocalPlayer.drop). Protection can stop the player's own drop, never cause one. */
@Mixin(LocalPlayer.class)
public abstract class DropMixin {
	@Inject(method = "drop", at = @At("HEAD"), cancellable = true)
	private void fatesofa$protectDrop(boolean all, CallbackInfoReturnable<Boolean> cir) {
		if (ProtectionFeature.blockDrop((LocalPlayer) (Object) this)) cir.setReturnValue(false);
	}
}
