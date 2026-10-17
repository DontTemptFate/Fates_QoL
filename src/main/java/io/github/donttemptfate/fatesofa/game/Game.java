package io.github.donttemptfate.fatesofa.game;

import io.github.donttemptfate.fates.common.text.Strip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;
import java.util.function.Function;

/** Small helpers for the game side: chat notes to the player, screen opening, Hypixel check. */
public final class Game {
	private Game() {}

	private static Function<Screen, Screen> pendingScreen;

	public static Minecraft mc() {
		return Minecraft.getInstance();
	}

	/** Opens a screen on the next tick (a command's chat window would close it at once). */
	public static void openNextTick(Function<Screen, Screen> factory) {
		pendingScreen = factory;
	}

	static void tickPendingScreen(Minecraft mc) {
		Function<Screen, Screen> f = pendingScreen;
		if (f != null) {
			pendingScreen = null;
			mc.gui.setScreen(f.apply(mc.gui.screen()));
		}
	}

	static MutableComponent prefix() {
		return Component.literal("[OFA] ").withStyle(ChatFormatting.LIGHT_PURPLE);
	}

	/** A message in the player's own chat (never sent to the server). */
	public static void send(Component message) {
		Minecraft mc = mc();
		if (mc.player != null) mc.player.sendSystemMessage(message);
	}

	public static void info(String text) {
		send(prefix().append(Component.literal(Strip.codes(text)).withStyle(ChatFormatting.GRAY)));
	}

	public static void ok(String text) {
		send(prefix().append(Component.literal(Strip.codes(text)).withStyle(ChatFormatting.GREEN)));
	}

	public static void warn(String text) {
		send(prefix().append(Component.literal(Strip.codes(text)).withStyle(ChatFormatting.YELLOW)));
	}

	public static void error(String text) {
		send(prefix().append(Component.literal(Strip.codes(text)).withStyle(ChatFormatting.RED)));
	}

	/** Minimum time between two commands OFA sends for the player, so a double click or key repeat stays one command. */
	static final long COMMAND_GAP_MS = 400;
	private static long lastCommandAt;
	/** The last command sent (for tests). */
	public static volatile String lastCommand;

	/**
	 * Sends one command the player asked for with a click or key press (quick buttons, hotkeys, aliases, opening a
	 * storage page). Never called on a timer or in a sequence: one player action, at most one command.
	 * Returns false when nothing was sent (no connection, empty, or too soon after the last one).
	 */
	public static boolean runCommand(String command) {
		Minecraft mc = mc();
		String c = io.github.donttemptfate.fatesofa.quick.QuickCommands.clean(command);
		if (c.isEmpty() || mc.player == null || mc.getConnection() == null) return false;
		long now = System.currentTimeMillis();
		if (now - lastCommandAt < COMMAND_GAP_MS) return false;
		lastCommandAt = now;
		lastCommand = c;
		mc.getConnection().sendCommand(c);
		return true;
	}

	public static boolean isHypixel(Minecraft mc) {
		var server = mc.getCurrentServer();
		if (server == null || server.ip == null) return false;
		String ip = server.ip.toLowerCase(Locale.ROOT).trim();
		int colon = ip.lastIndexOf(':');
		if (colon > 0 && ip.indexOf(':') == colon) ip = ip.substring(0, colon);
		if (ip.endsWith(".")) ip = ip.substring(0, ip.length() - 1);
		return ip.equals("hypixel.net") || ip.endsWith(".hypixel.net");
	}
}
