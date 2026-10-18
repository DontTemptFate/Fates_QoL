package io.github.donttemptfate.fatesofa.game;

import io.github.donttemptfate.fatescommon.text.LegacyText;
import io.github.donttemptfate.fatesofa.FatesOfaClient;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Every incoming server message, once, before any filter hides it: chat lines and action-bar text, as
 * plain text (no formatting). Features subscribe here instead of each hooking chat themselves.
 */
public final class ChatBus {
	private ChatBus() {}

	/** A message: plain text and the original component. */
	public record Msg(String plain, Component component, long atMs) {
	}

	private static final List<Consumer<Msg>> CHAT = new CopyOnWriteArrayList<>();
	private static final List<Consumer<Msg>> ACTION_BAR = new CopyOnWriteArrayList<>();
	private static volatile String lastActionBar = "";

	public static void onChat(Consumer<Msg> l) {
		CHAT.add(l);
	}

	public static void onActionBar(Consumer<Msg> l) {
		ACTION_BAR.add(l);
	}

	public static String lastActionBar() {
		return lastActionBar;
	}

	static void register() {
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			try {
				String plain = LegacyText.clean(message.getString());
				// patterns only ever need the start of a line; very long lines are cut so no pattern runs away
				if (plain.length() > 600) plain = plain.substring(0, 600);
				Msg m = new Msg(plain, message, System.currentTimeMillis());
				if (overlay) {
					lastActionBar = m.plain();
					for (Consumer<Msg> l : ACTION_BAR) safe(l, m);
				} else {
					for (Consumer<Msg> l : CHAT) safe(l, m);
				}
			} catch (Throwable e) {
				FatesOfaClient.LOG.warn("chat listener error", e);
			}
			return true;
		});
	}

	static void safe(Consumer<Msg> l, Msg m) {
		try {
			l.accept(m);
		} catch (Throwable e) {
			FatesOfaClient.LOG.warn("chat listener error", e);
		}
	}

	/** For tests: feed a message as if the server sent it. */
	public static void inject(Component message, boolean overlay) {
		Msg m = new Msg(LegacyText.clean(message.getString()), message, System.currentTimeMillis());
		for (Consumer<Msg> l : overlay ? ACTION_BAR : CHAT) l.accept(m);
	}
}
