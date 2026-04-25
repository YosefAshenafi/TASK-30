package com.meridian.backups;

import java.io.IOException;

@FunctionalInterface
public interface ProcessExecutor {

    ProcessResult run(ProcessBuilder pb) throws IOException, InterruptedException;

    record ProcessResult(int exitCode, String stdout) {}

    static ProcessExecutor system() {
        return pb -> {
            Process p = pb.start();
            // Read stdout before waitFor() to prevent buffer-full deadlock.
            String stdout = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            return new ProcessResult(p.exitValue(), stdout.trim());
        };
    }
}
