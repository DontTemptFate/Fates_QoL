package io.github.donttemptfate.fatesofa.mixin;

import io.github.donttemptfate.fatesofa.features.RarityBackground;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drawing behind a slot's item (rarity backgrounds). Drawing only. */
@Mixin(AbstractContainerScreen.class)
public abstract class SlotDrawMixin {
	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void fatesofa$behindSlot(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		RarityBackground.draw(g, slot);
	}
}
