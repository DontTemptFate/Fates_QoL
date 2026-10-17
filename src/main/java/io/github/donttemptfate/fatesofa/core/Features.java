package io.github.donttemptfate.fatesofa.core;

import io.github.donttemptfate.fatesofa.config.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** All features, in registration order. */
public final class Features {
	private Features() {}

	private static final Map<String, Feature> ALL = new LinkedHashMap<>();
	private static volatile BiConsumer<Feature, String> errorSink = (f, m) -> {
	};
	private static volatile ErrorLogger logger = (f, e, off) -> {
	};

	public interface ErrorLogger {
		void log(Feature f, Throwable e, boolean switchedOff);
	}

	public static synchronized <F extends Feature> F add(F f) {
		if (ALL.putIfAbsent(f.id, f) != null) throw new IllegalStateException("feature id used twice: " + f.id);
		return f;
	}

	public static synchronized List<Feature> all() {
		return Collections.unmodifiableList(new ArrayList<>(ALL.values()));
	}

	public static synchronized Feature get(String id) {
		return ALL.get(id);
	}

	public static void declareAll(Settings store) {
		for (Feature f : all()) f.declare(store);
	}

	public static void initAll() {
		for (Feature f : all()) {
			try {
				f.init();
			} catch (Throwable e) {
				f.fail(e);
			}
		}
	}

	/** Where errors go: the log, and a chat note when a feature is switched off. */
	public static void onError(ErrorLogger log, BiConsumer<Feature, String> switchedOffNotice) {
		logger = log;
		errorSink = switchedOffNotice;
	}

	static void reportError(Feature f, Throwable e, boolean switchedOff) {
		try {
			logger.log(f, e, switchedOff);
			if (switchedOff) errorSink.accept(f, f.errors().lastError());
		} catch (Throwable ignored) {
			// reporting must never fail
		}
	}

	/** For tests. */
	static synchronized void clear() {
		ALL.clear();
	}
}
