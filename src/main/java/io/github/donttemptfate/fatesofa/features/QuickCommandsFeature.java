package io.github.donttemptfate.fatesofa.features;

import com.mojang.brigadier.CommandDispatcher;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.game.Game;
import io.github.donttemptfate.fatesofa.items.Stacks;
import io.github.donttemptfate.fatesofa.mixin.AbstractContainerScreenAccessor;
import io.github.donttemptfate.fatesofa.quick.QuickCommands;
import io.github.donttemptfate.fatesofa.ui.QuickCommandsScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Quick commands: tabs above menus, hotkeys (Quick command 1–9 in Controls) and short aliases. Each one sends a
 * single command because the player clicked, pressed or typed it — nothing runs on its own.
 */
public final class QuickCommandsFeature extends Feature {
	public static QuickCommandsFeature INSTANCE;

	final Setting<Boolean> tabs = setting(Setting.bool("quick.tabs", "Quick buttons above menus", true)
			.help("Tabs above your inventory and SkyBlock menus. Click one to send its command; right-click to edit."));
	final Setting<Boolean> hotkeys = setting(Setting.bool("quick.hotkeys", "Hotkeys", true)
			.help("Quick command 1–9 in Controls → Fates OFA send the first nine commands of your list."));
	final Setting<Boolean> aliases = setting(Setting.bool("quick.aliases", "Short aliases", true)
			.help("Type a quick command's alias (e.g. /ch) to send it. New aliases work after you rejoin the server."));

	static final int TAB_W = 22, TAB_H = 21;
	private List<QuickCommands.Entry> entries = QuickCommands.defaults();
	private final Map<String, ItemStack> icons = new HashMap<>();

	public QuickCommandsFeature() {
		super("quick_commands", "Quick buttons & commands",
				"Your most-used commands one click, key or short alias away: tabs above menus, hotkeys and aliases. One action from you sends one command.",
				Category.GENERAL, true, Risk.SAFE);
		INSTANCE = this;
	}

	@Override
	public Set<String> keywords() {
		return Set.of("warp", "hotkey", "keybind", "alias", "shortcut", "pets", "wardrobe", "storage", "button", "tab");
	}

	@Override
	public List<Action> actions() {
		return List.of(new Action("Edit quick commands…", () -> Game.openNextTick(QuickCommandsScreen::new)));
	}

	@Override
	public void init() {
		entries = QuickCommands.fromJson(Ofa.get().settings.raw("quick.entries"));
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> guard(() -> draw(acs, g, mx, my)));
			ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> guard(() -> !click(acs, e.x(), e.y(), e.button()), true));
		});
		ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> registerAliases(d));
	}

	public List<QuickCommands.Entry> entries() {
		return List.copyOf(entries);
	}

	public void save(List<QuickCommands.Entry> list) {
		entries = List.copyOf(list);
		icons.clear();
		Ofa.get().settings.putRaw("quick.entries", QuickCommands.toJson(entries));
	}

	boolean usable() {
		return Ofa.get().state.onHypixel() || Boolean.getBoolean("fatesofa.test");
	}

	/** Sends entry {@code i} (a hotkey or tab press). */
	public void run(QuickCommands.Entry e) {
		guard(() -> {
			if (!usable()) {
				Game.warn("Quick commands only work on Hypixel.");
				return;
			}
			Game.runCommand(e.command());
		});
	}

	public void hotkey(int i) {
		if (!on(hotkeys) || !active()) return;
		List<QuickCommands.Entry> list = entries;
		if (i < list.size()) run(list.get(i));
		else Game.info("Quick command " + (i + 1) + " has no command yet: /ofa quick to add one.");
	}

	void registerAliases(CommandDispatcher<FabricClientCommandSource> d) {
		if (!active() || !on(aliases)) return;
		for (QuickCommands.Entry e : entries) {
			if (e.alias().isEmpty() || d.getRoot().getChild(e.alias()) != null) continue;
			d.register(ClientCommands.literal(e.alias()).executes(c -> {
				run(e);
				return 1;
			}));
		}
	}

	ItemStack icon(QuickCommands.Entry e) {
		return icons.computeIfAbsent(e.icon(), QuickCommandsFeature::iconStack);
	}

	/** A SkyBlock item (from the item data) or a Minecraft item by id; paper when neither exists. */
	public static ItemStack iconStack(String id) {
		if (QuickCommands.skyblockIcon(id)) {
			var repo = Ofa.get().repo.items().get(id);
			if (repo != null) return Stacks.of(repo);
			return Stacks.build("minecraft:paper", null, "", List.of(), null, 1);
		}
		return Stacks.build(id.contains(":") ? id : "minecraft:" + id, null, "", List.of(), null, 1);
	}

	/**
	 * Where the tabs go: {x, y, entry index (−1 = "+"), side}. A row on top of the window when there's room above it
	 * (side 0); otherwise a column down its left edge (side 1), e.g. tall SkyBlock menus at a large GUI scale.
	 */
	List<int[]> layout(AbstractContainerScreen<?> s) {
		AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) s;
		int top = a.fatesofa$topPos(), left = a.fatesofa$leftPos();
		boolean above = top >= TAB_H + 2;
		if (!above && left < TAB_W + 2) return null;
		List<int[]> out = new ArrayList<>();
		int pos = above ? left + 3 : top + 3;
		int max = above ? left + a.fatesofa$imageWidth() - 3 : top + a.fatesofa$imageHeight() - 3;
		int step = above ? TAB_W + 2 : TAB_H + 2, size = above ? TAB_W : TAB_H;
		int i = 0;
		for (QuickCommands.Entry e : entries) {
			if (e.tab()) {
				if (pos + size > max - size - 2) break; // keep room for "+"
				out.add(above ? new int[] {pos, top - TAB_H, i, 0} : new int[] {left - TAB_W, pos, i, 1});
				pos += step;
			}
			i++;
		}
		out.add(above ? new int[] {pos, top - TAB_H, -1, 0} : new int[] {left - TAB_W, pos, -1, 1});
		return out;
	}

	/** For the in-game test. */
	public List<int[]> testLayout(AbstractContainerScreen<?> s) {
		return layout(s);
	}

	/** Width the tab column takes left of this window (0 when the tabs sit on top), so other panels move over. */
	public int leftReserve(AbstractContainerScreen<?> s) {
		if (!active() || !on(tabs) || !usable()) return 0;
		List<int[]> l = layout(s);
		return l != null && !l.isEmpty() && l.get(0)[3] == 1 ? TAB_W + 2 : 0;
	}

	void draw(AbstractContainerScreen<?> s, GuiGraphicsExtractor g, int mx, int my) {
		if (!on(tabs) || !usable()) return;
		List<int[]> tabs = layout(s);
		if (tabs == null) return;
		var t = Ui.theme();
		int[] hovered = null;
		for (int[] tb : tabs) {
			boolean over = mx >= tb[0] && mx < tb[0] + TAB_W && my >= tb[1] && my < tb[1] + TAB_H;
			boolean side = tb[3] == 1;
			int lift = over ? 2 : 0;
			int x = tb[0] - (side ? lift : 0), y = tb[1] - (side ? 0 : lift), w = TAB_W + (side ? lift : 0), h = TAB_H + (side ? 0 : lift);
			g.fill(x, y, x + w, y + h, over ? 0xF02A0D46 : 0xF0160622);
			// frame on three sides (the window is the fourth)
			g.fill(x, y, x + w, y + 1, t.outer());
			g.fill(x, y, x + 1, y + h, t.outer());
			if (side) g.fill(x, y + h - 1, x + w, y + h, t.outer());
			else g.fill(x + w - 1, y, x + w, y + h, t.outer());
			g.fill(x + 1, y + 1, side ? x + w : x + w - 1, y + 2, t.inner());
			if (tb[2] < 0) {
				var plus = Ui.t("+", Ui.Role.TITLE, (Ui.bar() & 0xFFFFFF) | 0xFF000000);
				Ui.draw(g, plus, Ui.Role.TITLE, x + (w - Ui.width(plus, Ui.Role.TITLE)) / 2, y + 14);
			} else {
				g.item(icon(entries.get(tb[2])), x + (side ? 3 : 3), y + 3);
			}
			if (over) {
				if (side) g.fill(x + 2, y + 3, x + 3, y + h - 3, Ui.accent());
				else g.fill(x + 3, y + h - 3, x + w - 3, y + h - 2, Ui.accent());
				hovered = tb;
			}
		}
		if (hovered != null) {
			List<Component> lines = new ArrayList<>();
			if (hovered[2] < 0) {
				lines.add(Component.literal("Quick buttons").withStyle(ChatFormatting.WHITE));
				lines.add(Component.literal("Add or edit your commands").withStyle(ChatFormatting.GRAY));
			} else {
				QuickCommands.Entry e = entries.get(hovered[2]);
				lines.add(Component.literal(e.label()).withStyle(ChatFormatting.WHITE));
				lines.add(Component.literal("runs /" + e.command()).withStyle(ChatFormatting.LIGHT_PURPLE));
				int idx = hovered[2];
				String key = idx < io.github.donttemptfate.fatesofa.game.Keys.quick.length && io.github.donttemptfate.fatesofa.game.Keys.quick[idx] != null
						&& !io.github.donttemptfate.fatesofa.game.Keys.quick[idx].isUnbound()
						? io.github.donttemptfate.fatesofa.game.Keys.quick[idx].getTranslatedKeyMessage().getString() : null;
				if (key != null || !e.alias().isEmpty())
					lines.add(Component.literal((key != null ? "Key " + key : "") + (key != null && !e.alias().isEmpty() ? " · " : "")
							+ (e.alias().isEmpty() ? "" : "/" + e.alias())).withStyle(ChatFormatting.DARK_AQUA));
				lines.add(Component.literal("Right-click to edit").withStyle(ChatFormatting.DARK_GRAY));
			}
			g.setComponentTooltipForNextFrame(Minecraft.getInstance().font, lines, mx, my);
		}
	}

	/** True when the click hit a tab (the window doesn't get it). Public for the in-game test. */
	public boolean click(AbstractContainerScreen<?> s, double mx, double my, int button) {
		if (!on(tabs) || !usable()) return false;
		List<int[]> tabs = layout(s);
		if (tabs == null) return false;
		for (int[] tb : tabs) {
			if (mx < tb[0] - 2 || mx >= tb[0] + TAB_W || my < tb[1] - 2 || my >= tb[1] + TAB_H) continue;
			if (tb[2] < 0 || io.github.donttemptfate.fatesofa.ui.Mouse.right(button)) {
				int focus = tb[2];
				Minecraft.getInstance().gui.setScreen(new QuickCommandsScreen(s, focus));
			} else if (io.github.donttemptfate.fatesofa.ui.Mouse.left(button)) run(entries.get(tb[2]));
			return true;
		}
		return false;
	}
}
