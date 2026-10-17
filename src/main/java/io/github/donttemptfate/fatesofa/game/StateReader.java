package io.github.donttemptfate.fatesofa.game;

import io.github.donttemptfate.fates.common.text.Texts;
import io.github.donttemptfate.fatesofa.state.ScoreboardInfo;
import io.github.donttemptfate.fatesofa.state.SkyBlockState;
import io.github.donttemptfate.fatesofa.state.TabInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads the sidebar and the tab list into {@link SkyBlockState} twice a second. The only place in OFA
 * that touches the scoreboard or tab list; features read the parsed result.
 */
public final class StateReader {
	private StateReader() {}

	static final int EVERY_TICKS = 10;
	private static int tick;

	static void tick(Minecraft mc, SkyBlockState state) {
		if (++tick % EVERY_TICKS != 0) return;
		if (mc.level == null || mc.player == null) return;
		long now = System.currentTimeMillis();
		state.fromScoreboard(sidebar(mc), now);
		state.fromTab(TabInfo.parse(tabLines(mc)), now);
	}

	/** The sidebar, top line first, or null when there is none. */
	public static ScoreboardInfo sidebar(Minecraft mc) {
		Scoreboard sb = mc.level.getScoreboard();
		Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (obj == null) return null;
		List<PlayerScoreEntry> entries = new ArrayList<>(sb.listPlayerScores(obj));
		entries.removeIf(PlayerScoreEntry::isHidden);
		entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed().thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER));
		List<String> lines = new ArrayList<>();
		for (PlayerScoreEntry e : entries) {
			if (lines.size() >= 15) break;
			PlayerTeam team = sb.getPlayersTeam(e.owner());
			Component name = e.display() != null ? e.display() : PlayerTeam.formatNameForTeam(team, e.ownerName());
			lines.add(Texts.legacy(name));
		}
		return ScoreboardInfo.parse(Texts.legacy(obj.getDisplayName()), lines);
	}

	/** Tab list entries in display order, as § strings. */
	public static List<String> tabLines(Minecraft mc) {
		ClientPacketListener conn = mc.getConnection();
		if (conn == null) return List.of();
		List<PlayerInfo> infos = new ArrayList<>(conn.getListedOnlinePlayers());
		infos.sort(Comparator.<PlayerInfo>comparingInt(p -> -p.getTabListOrder())
				.thenComparing(p -> p.getTeam() == null ? "" : p.getTeam().getName()));
		List<String> lines = new ArrayList<>(infos.size());
		for (PlayerInfo p : infos) {
			Component c = p.getTabListDisplayName();
			if (c != null) lines.add(Texts.legacy(c));
		}
		return lines;
	}
}
