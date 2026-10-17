package io.github.donttemptfate.fatesofa.rooms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Learns secrets and routes while you play. The game counts a room's secrets on the action bar ("2/7
 * Secrets"); when that count goes up, the find is put where it happened: the chest, lever or head you just
 * used, the item you just picked up or the secret bat that just died. The path you walk from entering a
 * room to its last secret is kept as the room's route when it is your fastest.
 *
 * <p>Plain logic (no game calls) so it can be tested; the feature feeds it positions and events.
 */
public final class RoomLearner {
	/** Where a world position sits: its cell, the cell's room id and facing. */
	public record Placement(RoomGrid.Cell cell, String id, int rotation) {
	}

	public interface Rooms {
		/** Null while the room there isn't recognised (not loaded yet, doorway, outside the grid). */
		Placement at(double x, double z);
	}

	/** One learned find. */
	public record Learned(String id, RoomStore.Kind kind, boolean isNew, double x, double y, double z) {
	}

	record Candidate(RoomStore.Kind kind, double x, double y, double z, long at) {
	}

	static final long CANDIDATE_MS = 3000, CELL_SETTLE_MS = 1500, ROUTE_MAX_MS = 10 * 60_000;
	static final double ROUTE_STEP = 1.5;

	private final RoomStore store;
	private final Rooms rooms;
	private final String me;
	private final Deque<Candidate> candidates = new ArrayDeque<>();
	private final List<double[]> foundHere = new ArrayList<>();
	private int lastFound = -1, lastTotal = -1;
	private RoomGrid.Cell cell;
	private long cellChangedAt;
	private Placement routeStart;
	private long routeStartAt;
	private final List<double[]> path = new ArrayList<>();

	public RoomLearner(RoomStore store, Rooms rooms, String me) {
		this.store = store;
		this.rooms = rooms;
		this.me = me;
	}

	/** A new run: forget what was found and walked. */
	public void reset() {
		candidates.clear();
		foundHere.clear();
		lastFound = lastTotal = -1;
		cell = null;
		routeStart = null;
		path.clear();
	}

	/** Something that may be a secret: a used block (pass its centre) or a vanished item / bat. */
	public void candidate(RoomStore.Kind kind, double x, double y, double z, long now) {
		candidates.addFirst(new Candidate(kind, x, y, z, now));
		while (candidates.size() > 8) candidates.removeLast();
		// a chest a teammate already emptied doesn't raise your count, but it's done
		if (kind == RoomStore.Kind.CHEST || kind == RoomStore.Kind.ESSENCE) markFound(x, y, z);
	}

	/** The player's position, every tick or so. */
	public void player(double x, double y, double z, long now) {
		RoomGrid.Cell c = RoomGrid.at(x, z);
		if (c != null && !c.equals(cell)) {
			cell = c;
			cellChangedAt = now;
		}
		if (routeStart == null) return;
		if (now - routeStartAt > ROUTE_MAX_MS) {
			routeStart = null;
			return;
		}
		double[] p = RoomGrid.toRoom(routeStart.cell(), routeStart.rotation(), x, z);
		double[] pt = {p[0], y, p[1]};
		if (path.isEmpty() || dist2(path.get(path.size() - 1), pt) >= ROUTE_STEP * ROUTE_STEP) {
			if (path.size() < RoomStore.MAX_POINTS) path.add(pt);
		}
	}

	/** The action bar's room count changed (or was read again). Returns what was learned. */
	public List<Learned> counter(int found, int total, double px, double py, double pz, long now) {
		if (found < 0 || total <= 0 || found > total) return List.of();
		if (total != lastTotal || found < lastFound || lastFound < 0) { // another room
			lastFound = found;
			lastTotal = total;
			startRoute(found, px, pz, now);
			return List.of();
		}
		int gained = found - lastFound;
		lastFound = found;
		if (gained <= 0) return List.of();
		List<Learned> out = new ArrayList<>();
		Iterator<Candidate> it = candidates.iterator();
		while (it.hasNext() && out.size() < gained) {
			Candidate c = it.next();
			if (now - c.at() > CANDIDATE_MS) {
				it.remove();
				continue;
			}
			it.remove();
			Learned l = learn(c.kind(), c.x(), c.y(), c.z());
			if (l != null) out.add(l);
		}
		// nothing to point at (a secret the game counts some other way): your own position, once, if you've
		// been in this cell long enough that it can't be the previous room's count
		if (out.isEmpty() && now - cellChangedAt > CELL_SETTLE_MS) {
			Learned l = learn(RoomStore.Kind.OTHER, px, py, pz);
			if (l != null) out.add(l);
		}
		if (found == total) finishRoute(now);
		return out;
	}

	Learned learn(RoomStore.Kind kind, double x, double y, double z) {
		Placement p = rooms.at(x, z);
		if (p == null) return null;
		double[] r = RoomGrid.toRoom(p.cell(), p.rotation(), x, z);
		boolean isNew = store.addSecret(p.id(), r[0], y, r[1], kind, me);
		markFound(x, y, z);
		return new Learned(p.id(), kind, isNew, x, y, z);
	}

	void startRoute(int found, double px, double pz, long now) {
		path.clear();
		routeStart = null;
		if (found != 0) return; // only a full clear from the door is a route
		Placement p = rooms.at(px, pz);
		if (p == null) return;
		routeStart = p;
		routeStartAt = now;
	}

	void finishRoute(long now) {
		if (routeStart != null && path.size() >= 2 && now - routeStartAt > 3000) {
			store.offerRoute(routeStart.id(), new RoomStore.Route(now - routeStartAt, List.copyOf(path), me));
		}
		routeStart = null;
		path.clear();
	}

	void markFound(double x, double y, double z) {
		if (foundHere.size() < 500) foundHere.add(new double[] {x, y, z});
	}

	/** Already collected this run (by you, or a chest someone else opened). */
	public boolean found(double x, double y, double z) {
		double[] q = {x, y, z};
		for (double[] f : foundHere) if (dist2(f, q) <= RoomStore.SAME * RoomStore.SAME) return true;
		return false;
	}

	public boolean recordingRoute() {
		return routeStart != null;
	}

	static double dist2(double[] a, double[] b) {
		double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
		return dx * dx + dy * dy + dz * dz;
	}
}
