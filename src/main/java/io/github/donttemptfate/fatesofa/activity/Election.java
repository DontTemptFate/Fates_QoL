package io.github.donttemptfate.fatesofa.activity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.donttemptfate.fatesofa.data.Http;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** The current mayor (and minister) and their perks, from Hypixel's keyless election resource. */
public final class Election {
	static final String URL = "https://api.hypixel.net/v2/resources/skyblock/election";

	public record Mayor(String name, List<String> perks, String minister) {
	}

	private final Executor io;
	private final AtomicBoolean busy = new AtomicBoolean();
	private volatile Mayor mayor;
	private volatile long at, tried;

	public Election(Executor io) {
		this.io = io;
	}

	public Mayor mayor(long now) {
		if (now - at > 30 * 60_000L && now - tried > 5 * 60_000L && busy.compareAndSet(false, true)) {
			tried = now;
			io.execute(() -> {
				try {
					Http.Response r = Http.get(URL, 4L * 1024 * 1024, null);
					if (r.ok()) {
						mayor = parse(r.text());
						at = System.currentTimeMillis();
					}
				} catch (Exception ignored) {
					// try again later
				} finally {
					busy.set(false);
				}
			});
		}
		return mayor;
	}

	static Mayor parse(String json) {
		JsonObject m = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("mayor");
		List<String> perks = new ArrayList<>();
		if (m.has("perks")) m.getAsJsonArray("perks").forEach(p -> perks.add(p.getAsJsonObject().get("name").getAsString()));
		String minister = m.has("minister") ? m.getAsJsonObject("minister").get("name").getAsString() : null;
		return new Mayor(m.get("name").getAsString(), List.copyOf(perks), minister);
	}
}
