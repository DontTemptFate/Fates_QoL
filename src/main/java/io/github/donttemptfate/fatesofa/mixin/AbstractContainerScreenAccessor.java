package io.github.donttemptfate.fatesofa.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to where a container window sits and which slot is under the mouse. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int fatesofa$leftPos();

	@Accessor("topPos")
	int fatesofa$topPos();

	@Accessor("imageWidth")
	int fatesofa$imageWidth();

	@Accessor("imageHeight")
	int fatesofa$imageHeight();

	@Accessor("hoveredSlot")
	Slot fatesofa$hoveredSlot();
}
