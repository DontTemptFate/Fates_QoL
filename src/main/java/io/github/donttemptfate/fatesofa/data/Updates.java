package io.github.donttemptfate.fatesofa.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * "Is there a newer OFA?": reads {@code latest.json} from the repository's data branch (written by the release
 * workflow) once per game start. It only tells the player; nothing is downloaded or installed.
 */
public final class Updates {
	public static final String URL = "https://raw.githubusercontent.com/DontTemptFate/Fates_QoL/data/latest.json";

	/** The newest release: version, its page, and whether it is a pre-release (beta). */
	public record Latest(String version, String url, boolean prerelease, String notes) {
	}

	private volatile Latest latest;
	private volatile boolean checked;

	public Latest latest() {
		return latest;
	}

	public boolean checked() {
		return checked;
	}

	/** Fetches latest.json in the background, then calls {@code done} (on that thread). */
	public void check(Executor io, Consumer<Latest> done) {
		io.execute(() -> {
			try {
				Http.Response r = Http.get(URL, 64 * 1024, null);
				if (r.ok()) latest = parse(r.text());
			} catch (Exception e) {
				// offline or not published yet: no notice
			}
			checked = true;
			if (done != null) done.accept(latest);
		});
	}

	public static Latest parse(String json) {
		JsonObject o = JsonParser.parseString(json).getAsJsonObject();
		String v = o.get("version").getAsString().trim();
		if (!v.matches("[0-9]+\\.[0-9]+\\.[0-9]+(-[0-9A-Za-z.]+)?")) throw new IllegalArgumentException("bad version " + v);
		String url = o.has("url") ? o.get("url").getAsString() : "https://github.com/DontTemptFate/Fates_QoL/releases";
		if (!url.startsWith("https://github.com/DontTemptFate/Fates_QoL/")) url = "https://github.com/DontTemptFate/Fates_QoL/releases";
		return new Latest(v, url, o.has("prerelease") && o.get("prerelease").getAsBoolean(), o.has("notes") ? o.get("notes").getAsString() : "");
	}

	/** True when {@code candidate} is newer than {@code current} ("0.1.0-beta.2" > "0.1.0-beta.1" > "0.1.0-alpha.3"; "0.1.0" > "0.1.0-beta.9"). */
	public static boolean newer(String candidate, String current) {
		return compare(core(candidate), core(current)) > 0;
	}

	/** The version without "+mc26.3" build metadata. */
	static String core(String v) {
		int plus = v.indexOf('+');
		return (plus < 0 ? v : v.substring(0, plus)).trim();
	}

	static int compare(String a, String b) {
		String[] am = a.split("-", 2), bm = b.split("-", 2);
		String[] an = am[0].split("\\."), bn = bm[0].split("\\.");
		for (int i = 0; i < 3; i++) {
			int x = i < an.length ? num(an[i]) : 0, y = i < bn.length ? num(bn[i]) : 0;
			if (x != y) return Integer.compare(x, y);
		}
		boolean ap = am.length > 1, bp = bm.length > 1;
		if (!ap || !bp) return ap == bp ? 0 : ap ? -1 : 1; // a release beats its pre-releases
		String[] ax = am[1].split("\\."), bx = bm[1].split("\\.");
		for (int i = 0; i < Math.max(ax.length, bx.length); i++) {
			if (i >= ax.length) return -1;
			if (i >= bx.length) return 1;
			boolean an2 = ax[i].matches("\\d+"), bn2 = bx[i].matches("\\d+");
			int c = an2 && bn2 ? Integer.compare(num(ax[i]), num(bx[i])) : an2 ? -1 : bn2 ? 1 : ax[i].compareTo(bx[i]);
			if (c != 0) return c;
		}
		return 0;
	}

	static int num(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
