package dev.maplesu.manager.runtime;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Test APK entry point, launched as its real UID independently of the Manager process. */
public final class StandaloneSuLifecycleProbe {
    private static final int MAX_TRANSCRIPT_BYTES = 64 * 1024;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("existing|new|denied");
        checkMode(args[0]);
        String command = "existing".equals(args[0])
                ? "printf 'UID_START='; id -u; printf 'SESSION_STARTED\\n'; sleep 12; printf 'UID_END='; id -u; getenforce"
                : "printf 'UID_START='; id -u; getenforce; exit 37";
        Process child = new ProcessBuilder("/dev/maplesu/bin/su", "-c", command)
                .redirectErrorStream(true).start();
        run(child, args[0], 20_000, System.out);
    }

    static void run(Process child, String mode, long timeoutMillis, PrintStream output) throws Exception {
        checkMode(mode);
        ExecutorService reader = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "standalone-su-output");
            thread.setDaemon(true);
            return thread;
        });
        Future<byte[]> transcript = reader.submit(() -> {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream stream = child.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (count == 0) continue;
                    if (count > MAX_TRANSCRIPT_BYTES - bytes.size())
                        throw new IllegalStateException("su transcript exceeds " + MAX_TRANSCRIPT_BYTES + " bytes");
                    bytes.write(buffer, 0, count);
                    output.write(buffer, 0, count);
                    output.flush();
                }
            }
            child.waitFor();
            return bytes.toByteArray();
        });
        Throwable primary = null;
        try {
            String text;
            try {
                // One deadline covers both EOF and process termination, including an inherited open pipe.
                text = new String(transcript.get(timeoutMillis, TimeUnit.MILLISECONDS), StandardCharsets.UTF_8);
            } catch (TimeoutException failure) {
                throw new IllegalStateException("su timeout", failure);
            } catch (ExecutionException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                if (cause instanceof Error) throw (Error) cause;
                throw new IllegalStateException(cause);
            }
            boolean existing = "existing".equals(mode);
            boolean denied = "denied".equals(mode);
            int expected = denied ? 77 : existing ? 0 : 37;
            if (child.exitValue() != expected)
                throw new IllegalStateException("su exit=" + child.exitValue() + " expected=" + expected);
            if (!denied && (!exactUid(text, "UID_START=") || !exactLine(text, "Enforcing") ||
                    existing && (!exactUid(text, "UID_END=") || !exactLine(text, "SESSION_STARTED"))))
                throw new IllegalStateException("su root/SELinux lifecycle evidence missing");
            output.println("LIFECYCLE_PROBE_PASSED mode=" + mode + " exit=" + child.exitValue());
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            // Destroy before cancellation so a live child stops holding the reader's pipe open.
            try { child.destroyForcibly(); }
            catch (RuntimeException | Error cleanup) {
                if (primary != null) primary.addSuppressed(cleanup); else throw cleanup;
            } finally { transcript.cancel(true); reader.shutdownNow(); }
        }
    }

    private static boolean exactUid(String text, String prefix) {
        int matches = 0;
        for (String line : text.split("\\r?\\n", -1)) if (line.startsWith(prefix)) {
            if (!line.equals(prefix + "0")) return false;
            matches++;
        }
        return matches == 1;
    }

    private static boolean exactLine(String text, String expected) {
        int matches = 0;
        for (String line : text.split("\\r?\\n", -1)) if (expected.equals(line)) matches++;
        return matches == 1;
    }

    private static void checkMode(String mode) {
        if (!"existing".equals(mode) && !"new".equals(mode) && !"denied".equals(mode))
            throw new IllegalArgumentException("existing|new|denied");
    }
}
