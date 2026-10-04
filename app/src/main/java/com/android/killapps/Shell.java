package com.android.killapps;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

final class Shell {
    static final class Result {
        final int code; final String output;
        Result(int code, String output) { this.code = code; this.output = output; }
    }
    static Result root(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            final Process p = process;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try { byte[] buffer = new byte[4096]; int n; while ((n = p.getInputStream().read(buffer)) != -1) out.write(buffer, 0, n); } catch (Exception ignored) { }
            });
            reader.setDaemon(true); reader.start();
            if (!p.waitFor(12, TimeUnit.SECONDS)) { p.destroyForcibly(); reader.join(500); return new Result(-1, "root request timed out"); }
            reader.join(1000);
            return new Result(p.exitValue(), out.toString(StandardCharsets.UTF_8.name()));
        } catch (Exception e) { return new Result(-1, e.toString()); }
        finally { if (process != null) process.destroy(); }
    }
    static boolean available() { Result r = root("id"); return r.code == 0 && r.output.contains("uid=0"); }
}
