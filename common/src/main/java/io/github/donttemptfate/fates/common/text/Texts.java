package io.github.donttemptfate.fates.common.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.List;
import java.util.Optional;

/** Converting between Minecraft text components and "§" strings (what Hypixel and the NEU repo use). */
public final class Texts {
	private Texts() {}

	private static final char[] CODES = "0123456789abcdef".toCharArray();
	private static final int[] RGB = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

	/** A component as a § string: a colour code whenever the style changes, then k/l/m/n/o. */
	public static String legacy(Component component) {
		if (component == null) return "";
		StringBuilder sb = new StringBuilder();
		String[] last = {null};
		component.visit((style, text) -> {
			if (text.isEmpty()) return Optional.empty();
			String codes = codes(style);
			if (!codes.equals(last[0])) {
				sb.append(codes);
				last[0] = codes;
			}
			sb.append(text);
			return Optional.empty();
		}, Style.EMPTY);
		return sb.toString();
	}

	private static String codes(Style style) {
		StringBuilder c = new StringBuilder();
		c.append('§').append(legacyColor(style.getColor()));
		if (style.isObfuscated()) c.append("§k");
		if (style.isBold()) c.append("§l");
		if (style.isStrikethrough()) c.append("§m");
		if (style.isUnderlined()) c.append("§n");
		if (style.isItalic()) c.append("§o");
		return c.toString();
	}

	static char legacyColor(TextColor color) {
		if (color == null) return 'f';
		int rgb = color.getValue() & 0xFFFFFF;
		int best = 15;
		long bestDist = Long.MAX_VALUE;
		for (int i = 0; i < RGB.length; i++) {
			int c = RGB[i];
			long dr = ((c >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
			long dg = ((c >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
			long db = (c & 0xFF) - (rgb & 0xFF);
			long d = dr * dr + dg * dg + db * db;
			if (d < bestDist) {
				bestDist = d;
				best = i;
			}
		}
		return CODES[best];
	}

	/**
	 * § text as a component. Only colours and formatting carry over; click and hover actions are never
	 * created from text.
	 */
	public static MutableComponent fromLegacy(String text) {
		MutableComponent out = Component.empty();
		List<LegacyText.Run> runs = LegacyText.parse(text);
		for (LegacyText.Run r : runs) {
			Style style = Style.EMPTY.withItalic(false);
			if (r.color() != 0) {
				ChatFormatting f = ChatFormatting.getByCode(r.color());
				if (f != null) style = style.withColor(TextColor.fromLegacyFormat(f));
			}
			if (r.bold()) style = style.withBold(true);
			if (r.italic()) style = style.withItalic(true);
			if (r.underlined()) style = style.withUnderlined(true);
			if (r.strikethrough()) style = style.withStrikethrough(true);
			if (r.obfuscated()) style = style.withObfuscated(true);
			out.append(Component.literal(r.text()).withStyle(style));
		}
		return out;
	}

	/** Plain text of a component (no formatting), cleaned of Hypixel's decorative characters. */
	public static String plain(Component c) {
		return c == null ? "" : Strip.clean(c.getString());
	}
}
