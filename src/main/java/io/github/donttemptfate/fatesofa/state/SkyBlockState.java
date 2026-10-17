package io.github.donttemptfate.fatesofa.state;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The one shared picture of where the player is and what they have, built from the Hypixel Mod API
 * (when present), the sidebar, the tab list and chat. Features read this; they never parse the
 * scoreboard or tab list themselves. Values that belong to one world are dropped on a world change.
 */
public final class SkyBlockState {
	private volatile boolean onHypixel;
	private volatile Observed<Boolean> onSkyBlock;
	private volatile Observed<Island> island;
	private volatile Observed<String> area;
	private volatile Observed<String> server;
	private volatile Observed<String> profileName;
	private volatile Observed<ProfileType> profileType;
	private volatile Observed<Double> purse;
	private volatile Observed<Double> bits;
	private volatile Observed<Integer> skyblockLevel;
	private volatile ScoreboardInfo scoreboard;
	private volatile TabInfo tab;
	private volatile long worldChangedAtMs;

	private final List<Consumer<Island>> islandListeners = new CopyOnWriteArrayList<>();

	public void onIslandChange(Consumer<Island> l) {
		islandListeners.add(l);
	}

	// ---- inputs -------------------------------------------------------------------------------------

	public void setOnHypixel(boolean on) {
		onHypixel = on;
		if (!on) {
			onSkyBlock = null;
			setIsland(null);
		}
	}

	/** A new world was loaded (server switch, island warp): world-scoped values are no longer true. */
	public void worldChanged(long nowMs) {
		worldChangedAtMs = nowMs;
		area = null;
		server = null;
		scoreboard = null;
		tab = null;
		// the island is kept until the new world reports one, but marked as from before the change
	}

	public void fromModApi(String serverName, String mode, long nowMs) {
		if (serverName != null) server = new Observed<>(serverName, Observed.Source.MOD_API, nowMs);
		Island i = Island.fromMode(mode);
		if (i != null) {
			onSkyBlock = new Observed<>(true, Observed.Source.MOD_API, nowMs);
			setIsland(new Observed<>(i, Observed.Source.MOD_API, nowMs));
		}
	}

	/** A location packet saying the player isn't in SkyBlock (lobby, other game). */
	public void notSkyBlock(long nowMs) {
		onSkyBlock = new Observed<>(false, Observed.Source.MOD_API, nowMs);
		setIsland(null);
	}

	public void fromScoreboard(ScoreboardInfo info, long nowMs) {
		scoreboard = info;
		if (info == null) return;
		Observed<Boolean> sb = onSkyBlock;
		// the Mod API is trusted over the sidebar when both speak
		if (sb == null || sb.source() != Observed.Source.MOD_API || !sb.fresh(nowMs, 10_000)) {
			onSkyBlock = new Observed<>(info.skyblock(), Observed.Source.SCOREBOARD, nowMs);
		}
		if (info.area() != null) area = new Observed<>(info.area(), Observed.Source.SCOREBOARD, nowMs);
		if (info.server() != null && (server == null || server.source() != Observed.Source.MOD_API))
			server = new Observed<>(info.server(), Observed.Source.SCOREBOARD, nowMs);
		if (info.purse() != null) purse = new Observed<>(info.purse(), Observed.Source.SCOREBOARD, nowMs);
		if (info.bits() != null) bits = new Observed<>(info.bits(), Observed.Source.SCOREBOARD, nowMs);
		if (info.dungeonFloor() != null && (island == null || island.source() != Observed.Source.MOD_API)) {
			setIsland(new Observed<>(info.dungeonFloor().startsWith("T") ? Island.KUUDRA : Island.DUNGEON, Observed.Source.SCOREBOARD, nowMs));
		}
	}

	public void fromTab(TabInfo info, long nowMs) {
		tab = info;
		if (info == null) return;
		Island i = info.island();
		Observed<Island> cur = island;
		boolean modApiFresh = cur != null && cur.source() == Observed.Source.MOD_API && cur.atMs() >= worldChangedAtMs;
		if (i != null && !modApiFresh) setIsland(new Observed<>(i, Observed.Source.TAB_LIST, nowMs));
		String pn = info.profileName();
		if (pn != null) profileName = new Observed<>(pn, Observed.Source.TAB_LIST, nowMs);
		if (info.get("Profile") != null) profileType = new Observed<>(info.profileType(), Observed.Source.TAB_LIST, nowMs);
		Integer lvl = info.skyblockLevel();
		if (lvl != null) skyblockLevel = new Observed<>(lvl, Observed.Source.TAB_LIST, nowMs);
	}

	private void setIsland(Observed<Island> next) {
		Island before = island == null ? null : island.value();
		island = next;
		Island after = next == null ? null : next.value();
		if (!Objects.equals(before, after)) for (Consumer<Island> l : islandListeners) l.accept(after);
	}

	// ---- reads --------------------------------------------------------------------------------------

	public boolean onHypixel() {
		return onHypixel;
	}

	public boolean onSkyBlock() {
		Observed<Boolean> o = onSkyBlock;
		return onHypixel && o != null && Boolean.TRUE.equals(o.value());
	}

	/** The current island, or null when unknown / not in SkyBlock. */
	public Island island() {
		Observed<Island> o = island;
		return onSkyBlock() && o != null ? o.value() : null;
	}

	public boolean in(Island i) {
		return island() == i;
	}

	public Observed<Island> islandObserved() {
		return island;
	}

	public String area() {
		Observed<String> o = area;
		return o == null ? null : o.value();
	}

	public String server() {
		Observed<String> o = server;
		return o == null ? null : o.value();
	}

	public String profileName() {
		Observed<String> o = profileName;
		return o == null ? null : o.value();
	}

	public ProfileType profileType() {
		Observed<ProfileType> o = profileType;
		return o == null ? ProfileType.UNKNOWN : o.value();
	}

	public Double purse() {
		Observed<Double> o = purse;
		return o == null ? null : o.value();
	}

	public Double bits() {
		Observed<Double> o = bits;
		return o == null ? null : o.value();
	}

	public Integer skyblockLevel() {
		Observed<Integer> o = skyblockLevel;
		return o == null ? null : o.value();
	}

	/** The last sidebar read, or null. */
	public ScoreboardInfo scoreboard() {
		return scoreboard;
	}

	/** The last tab list read, or null. */
	public TabInfo tab() {
		return tab;
	}
}
