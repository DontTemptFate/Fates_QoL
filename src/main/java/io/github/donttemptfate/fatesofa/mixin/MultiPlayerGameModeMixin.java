package io.github.donttemptfate.fatesofa.mixin;

import io.github.donttemptfate.fatesofa.features.ProtectionFeature;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item protection: the only place OFA can stop one of the player's own clicks or drops (never make one).
 * Every container click passes through here; the drop key is hooked per Minecraft version (DropMixin).
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
	private void fatesofa$protectClick(int containerId, int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
		if (ProtectionFeature.blockClick(slotId, button, input, player)) ci.cancel();
	}
}
