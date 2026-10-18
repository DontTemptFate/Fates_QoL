package io.github.donttemptfate.fatesofa;

import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.core.Features;

import java.util.ArrayList;
import java.util.List;

/** The "Fates OFA" section of crash reports: enough to tell whether OFA was involved, nothing personal. */
public final class CrashInfo {
	private CrashInfo() {}

	public static String text() {
		Ofa o = Ofa.get();
		if (o == null) return "not started";
		List<String> on = new ArrayList<>(), failed = new ArrayList<>();
		for (Feature f : Features.all()) {
			if (o.settings.on(f.enabled)) on.add(f.id);
			if (f.failed()) failed.add(f.id + " (" + f.errors().lastError() + ")");
		}
		String commit = o.repo.items() == null ? null : o.repo.items().commit();
		return o.version + "; features on: " + String.join(", ", on)
				+ (failed.isEmpty() ? "" : "; switched off after errors: " + String.join("; ", failed))
				+ "; item data " + (commit == null ? "none" : commit.substring(0, Math.min(10, commit.length())))
				+ "; on SkyBlock " + o.state.onSkyBlock();
	}
}
