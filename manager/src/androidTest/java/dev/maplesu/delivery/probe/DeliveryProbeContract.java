package dev.maplesu.delivery.probe;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Shared only by device test APKs and the host regression; contains no admission mechanism. */
public final class DeliveryProbeContract {
    public static final String RECEIPT_KEY = "maplesuDelivery";
    public static final int MAX_BYTES = 64 * 1024;
    private static final String ROOT_COMMAND =
            "printf 'UID_START='; /system/bin/id -u; /system/bin/getenforce; exit 37";

    private DeliveryProbeContract() {}

    public static String[] grantedArgv(String path) {
        checkPath(path);
        return new String[] {path, "-c", ROOT_COMMAND};
    }

    public static String[] foreignArgv(String path) {
        checkPath(path);
        // Exact 126 is the shell's execution-denied status. A root-capable execution
        // would run ROOT_COMMAND and exit 37 instead. No delivered bytes are logged.
        String script = quote(path) + " -c " + quote(ROOT_COMMAND) +
                " >/dev/null 2>&1; rc=$?; printf 'EXEC_RC=%s\\n' \"$rc\"; exit \"$rc\"";
        return new String[] {"/system/bin/sh", "-c", script};
    }

    public static String quote(String value) {
        if (value.indexOf('\0') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new IllegalArgumentException("multiline shell argument");
        return "'" + value.replace("'", "'\\''") + "'";
    }

    public static void checkIdentity(int observed, int expected, int manager, int other) {
        if (observed != expected || observed % 100000 < 10000 ||
                observed == manager || observed == other)
            throw new IllegalStateException("ordinary fullUID identity mismatch");
    }

    public static byte[] readBounded(InputStream stream) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (count == 0) continue;
            if (count > MAX_BYTES - bytes.size()) throw new IllegalStateException("probe output limit");
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }

    public static final class Outcome {
        public final int code;
        public final String output;
        public Outcome(int code, String output) { this.code = code; this.output = output; }
    }

    /** A single deadline covers EOF and waitFor, with first-error-preserving cleanup. */
    public static Outcome collect(Process process, long timeoutMillis) throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "delivery-probe-output");
            thread.setDaemon(true);
            return thread;
        });
        Future<Outcome> pending = worker.submit(() -> {
            byte[] bytes;
            try (InputStream stream = process.getInputStream()) { bytes = readBounded(stream); }
            return new Outcome(process.waitFor(), new String(bytes, StandardCharsets.UTF_8));
        });
        Throwable primary = null;
        try {
            return pending.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (ExecutionException failure) {
            primary = failure.getCause();
            if (primary instanceof Exception) throw (Exception) primary;
            if (primary instanceof Error) throw (Error) primary;
            throw new IllegalStateException(primary);
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try { process.destroyForcibly(); }
            catch (RuntimeException | Error cleanup) {
                if (primary != null) primary.addSuppressed(cleanup); else throw cleanup;
            } finally { pending.cancel(true); worker.shutdownNow(); }
        }
    }

    public static void verifyGranted(Outcome result) {
        if (result.code != 37 || !uniqueLine(result.output, "UID_START=0", "UID_START=") ||
                !uniqueLine(result.output, "Enforcing", "Enforcing"))
            throw new IllegalStateException("delivered UID0/Enforcing/exit37 evidence missing");
    }

    public static void verifyForeign(int readErrno, Outcome result) {
        if (readErrno != 13 || result.code != 126 ||
                !uniqueLine(result.output, "EXEC_RC=126", "EXEC_RC="))
            throw new IllegalStateException("foreign read EACCES/execute126 evidence missing");
    }

    public static String receipt(String mode, String nonce, String packageName, int uid, int pid,
                                 int afterUid, Outcome result, int readErrno) {
        checkBinding(mode, nonce, packageName);
        if (uid != afterUid || uid % 100000 < 10000 || pid <= 0)
            throw new IllegalStateException("probe caller changed identity");
        if ("granted".equals(mode)) verifyGranted(result); else verifyForeign(readErrno, result);
        return String.join("|", "MAPLESU_DELIVERY_V1", mode, nonce, packageName,
                Integer.toString(uid), Integer.toString(pid), Integer.toString(afterUid),
                Integer.toString(result.code), Integer.toString(readErrno),
                "granted".equals(mode) ? "0" : "-1", "granted".equals(mode) ? "Enforcing" : "-");
    }

    public static String verifyInstrumentation(String output, String mode, String nonce,
                                                String packageName, int expectedUid) {
        checkBinding(mode, nonce, packageName);
        String prefix = "INSTRUMENTATION_RESULT: " + RECEIPT_KEY + "=";
        String wire = null;
        int successes = 0;
        for (String line : output.split("\\r?\\n", -1)) {
            if (line.startsWith(prefix)) {
                if (wire != null) throw new IllegalStateException("duplicate receipt");
                wire = line.substring(prefix.length());
            }
            if (line.startsWith("INSTRUMENTATION_CODE:")) {
                if (!line.equals("INSTRUMENTATION_CODE: -1"))
                    throw new IllegalStateException("fixture instrumentation failed");
                successes++;
            }
        }
        if (wire == null || successes != 1) throw new IllegalStateException("fixture receipt missing");
        String[] fields = wire.split("\\|", -1);
        if (fields.length != 11 || !fields[0].equals("MAPLESU_DELIVERY_V1") ||
                !fields[1].equals(mode) || !fields[2].equals(nonce) || !fields[3].equals(packageName) ||
                !fields[4].equals(Integer.toString(expectedUid)) || !fields[6].equals(fields[4]) ||
                !fields[5].matches("[1-9][0-9]*"))
            throw new IllegalStateException("fixture receipt binding mismatch");
        if ("granted".equals(mode)) {
            if (!fields[7].equals("37") || !fields[8].equals("-1") ||
                    !fields[9].equals("0") || !fields[10].equals("Enforcing"))
                throw new IllegalStateException("granted receipt result mismatch");
        } else if (!fields[7].equals("126") || !fields[8].equals("13") ||
                !fields[9].equals("-1") || !fields[10].equals("-"))
            throw new IllegalStateException("foreign receipt result mismatch");
        return wire;
    }

    private static boolean uniqueLine(String text, String expected, String prefix) {
        int count = 0;
        for (String line : text.split("\\r?\\n", -1)) if (line.startsWith(prefix)) {
            if (!line.equals(expected)) return false;
            count++;
        }
        return count == 1;
    }

    private static void checkBinding(String mode, String nonce, String packageName) {
        if ((!mode.equals("granted") && !mode.equals("foreign")) ||
                !nonce.matches("[0-9a-f]{32}") || !packageName.matches("[a-zA-Z][a-zA-Z0-9_.]+"))
            throw new IllegalArgumentException("probe binding malformed");
    }

    private static void checkPath(String path) {
        if (!path.startsWith("/") || path.indexOf('\0') >= 0 ||
                path.indexOf('\n') >= 0 || path.indexOf('\r') >= 0)
            throw new IllegalArgumentException("probe path malformed");
    }
}
