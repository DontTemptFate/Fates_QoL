package io.github.donttemptfate.fatesofa.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Lets chat cleanup replace its own previous copy of a repeated message with one line that counts ("×3"). */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("allMessages")
	List<GuiMessage> fatesofa$allMessages();

	@Invoker("refreshTrimmedMessages")
	void fatesofa$refreshTrimmedMessages();
}
