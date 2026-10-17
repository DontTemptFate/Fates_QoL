package io.github.donttemptfate.fatesofa.data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Predicate;

/** Test access to package-private helpers. */
public final class GitRepoAccess {
	private GitRepoAccess() {}

	public static String ref(String listing, String ref) {
		return GitRepo.refFromListing(listing, ref);
	}

	public static int unzip(InputStream in, Path dir, Predicate<String> keep) throws IOException {
		return GitRepo.unzip(in, dir, keep);
	}
}
