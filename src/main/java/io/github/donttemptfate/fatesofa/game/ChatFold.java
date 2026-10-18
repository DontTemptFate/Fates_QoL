package io.github.donttemptfate.fatesofa.game;

import io.github.donttemptfate.fates.common.text.Strip;
import io.github.donttemptfate.fatesofa.mixin.ChatComponentAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

import java.util.Iterator;
import java.util.List;

/** Folds a repeated chat line: the earlier copy is removed and the message shows once with "(×N)". */
public final class ChatFold {
	private ChatFold() {}

	/** Looks back this many lines for the earlier copy. */
	static final int LOOK_BACK = 20;

	public static void fold(Component message, int count) {
		Minecraft mc = Minecraft.getInstance();
		var chat = mc.gui.hud.getChat();
		String plain = Strip.clean(message.getString());
		ChatComponentAccessor a = (ChatComponentAccessor) chat;
		List<GuiMessage> all = a.fatesofa$allMessages();
		int seen = 0;
		for (Iterator<GuiMessage> it = all.iterator(); it.hasNext() && seen < LOOK_BACK; seen++) {
			GuiMessage m = it.next();
			String old = Strip.clean(m.content().getString()).replaceFirst(" \\(×\\d+\\)$", "");
			if (old.equals(plain)) {
				it.remove();
				a.fatesofa$refreshTrimmedMessages();
				break;
			}
		}
		chat.addServerSystemMessage(message.copy().append(Component.literal(" (×" + count + ")").withStyle(ChatFormatting.GRAY)));
	}
}
