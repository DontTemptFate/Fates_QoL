package io.github.donttemptfate.fatesofa.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

/**
 * The only way OFA talks to the internet: plain GETs of public data (no cookies, no tokens), with a
 * timeout, a size cap and a clear User-Agent. Never follows redirects to another host.
 */
public final class Http {
	private Http() {}

	public static volatile String userAgent = "FatesOFA (https://github.com/DontTemptFate/Fates_QoL)";

	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	public record Response(int status, byte[] body, String etag, long retryAfterS) {
		public String text() {
			return new String(body, StandardCharsets.UTF_8);
		}

		public boolean ok() {
			return status == 200;
		}
	}

	/** Hosts OFA may contact. Anything else is refused. */
	static final java.util.Set<String> HOSTS = java.util.Set.of("github.com", "codeload.github.com", "raw.githubusercontent.com", "api.hypixel.net", "fatesflipper.com");

	static URI check(String url) throws IOException {
		URI uri = URI.create(url);
		if (!"https".equals(uri.getScheme())) throw new IOException("only https is allowed: " + url);
		if (uri.getHost() == null || !HOSTS.contains(uri.getHost().toLowerCase(java.util.Locale.ROOT))) throw new IOException("host not allowed: " + uri.getHost());
		return uri;
	}

	/**
	 * GET {@code url}; at most {@code maxBytes} (after gunzip). The whole exchange, body included, must finish
	 * within 90 seconds.
	 */
	public static Response get(String url, long maxBytes, String ifNoneMatch) throws IOException, InterruptedException {
		URI uri = check(url);
		HttpRequest.Builder b = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(30))
				.header("User-Agent", userAgent)
				.header("Accept-Encoding", "gzip")
				.GET();
		if (ifNoneMatch != null) b.header("If-None-Match", ifNoneMatch);
		long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(90);
		HttpResponse<InputStream> r;
		try {
			r = CLIENT.sendAsync(b.build(), HttpResponse.BodyHandlers.ofInputStream()).get(90, java.util.concurrent.TimeUnit.SECONDS);
		} catch (java.util.concurrent.ExecutionException e) {
			throw new IOException(e.getCause() == null ? e.toString() : e.getCause().toString());
		} catch (java.util.concurrent.TimeoutException e) {
			throw new IOException("timed out: " + uri.getHost());
		}
		boolean gz = r.headers().firstValue("Content-Encoding").map(v -> v.equalsIgnoreCase("gzip")).orElse(false);
		// the size cap is enforced while reading: a response never grows past maxBytes in memory
		InputStream raw = r.body();
		java.util.concurrent.CompletableFuture<byte[]> reading = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
			try (InputStream in = gz ? new GZIPInputStream(raw) : raw) {
				return readCapped(in, maxBytes);
			} catch (IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		});
		byte[] body;
		try {
			body = reading.get(Math.max(1, deadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS);
		} catch (java.util.concurrent.ExecutionException e) {
			Throwable c = e.getCause() instanceof java.io.UncheckedIOException u ? u.getCause() : e.getCause();
			throw c instanceof IOException io ? io : new IOException(String.valueOf(c));
		} catch (java.util.concurrent.TimeoutException e) {
			raw.close();
			throw new IOException("timed out reading: " + uri.getHost());
		}
		long retry = r.headers().firstValue("Retry-After").map(v -> {
			try {
				return Long.parseLong(v.trim());
			} catch (NumberFormatException e) {
				return 60L;
			}
		}).orElse(0L);
		return new Response(r.statusCode(), body, r.headers().firstValue("ETag").orElse(null), retry);
	}

	/** Streams a GET into {@code file} (big downloads), within 5 minutes and {@code maxBytes}. Returns the status. */
	public static int download(String url, java.nio.file.Path file, long maxBytes) throws IOException, InterruptedException {
		URI uri = check(url);
		HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("User-Agent", userAgent).GET().build();
		HttpResponse<java.nio.file.Path> r;
		try {
			r = CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofFile(file)).get(5, java.util.concurrent.TimeUnit.MINUTES);
		} catch (java.util.concurrent.ExecutionException e) {
			throw new IOException(e.getCause() == null ? e.toString() : e.getCause().toString());
		} catch (java.util.concurrent.TimeoutException e) {
			throw new IOException("download timed out: " + uri.getHost());
		}
		if (r.statusCode() == 200 && java.nio.file.Files.size(file) > maxBytes) throw new IOException("download larger than " + maxBytes + " bytes");
		return r.statusCode();
	}

	static byte[] readCapped(InputStream in, long max) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[64 * 1024];
		long total = 0;
		int n;
		while ((n = in.read(buf)) > 0) {
			total += n;
			if (total > max) throw new IOException("response larger than " + max + " bytes");
			out.write(buf, 0, n);
		}
		return out.toByteArray();
	}
}
