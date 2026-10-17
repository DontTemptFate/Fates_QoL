package io.github.donttemptfate.fatesofa.ui;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Mouse buttons by name. 26.2 (GLFW) numbers them left 0 / right 1 / middle 2; 26.3 (SDL3) uses its own numbers,
 * so code must never compare against bare numbers. Each jar compiles these constants for its own version.
 */
public final class Mouse {
	private Mouse() {}

	public static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
	public static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
	public static final int MIDDLE = InputConstants.MOUSE_BUTTON_MIDDLE;
	public static final int BACK = InputConstants.MOUSE_BUTTON_4;

	public static boolean left(int b) {
		return b == LEFT;
	}

	public static boolean right(int b) {
		return b == RIGHT;
	}

	public static boolean middle(int b) {
		return b == MIDDLE;
	}

	public static boolean back(int b) {
		return b == BACK;
	}
}
