package com.repoguard.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/** Enforces managed executable profiles before starting any scanner or updater. */
public final class TrustedProcess {
    private TrustedProcess() { }
    public record Output(int exit, String text, String stderr) {
        public Output(int exit,String text){this(exit,text,"");}
    }
    public static Output run(List<String> command, Path cwd, Duration timeout) throws IOException {
        return run(command, cwd, timeout, 65536);
    }
    public static Output run(List<String> command, Path cwd, Duration timeout, int maxOutput) throws IOException {
        return run(command,cwd,timeout,maxOutput,java.util.Map.of());
    }
    public static Output run(List<String> command, Path cwd, Duration timeout, int maxOutput, java.util.Map<String,String> managedEnvironment) throws IOException {
        ManagedCommandPolicy.validate(command, managedEnvironment);
        ProcessBuilder builder = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(false);
        builder.environment().clear();
        builder.environment().put("PATH", "/opt/java/openjdk/bin:/usr/bin:/bin");
        builder.environment().put("HOME", "/tmp");
        builder.environment().put("LANG", "C.UTF-8");
        builder.environment().put("SEMGREP_ENABLE_VERSION_CHECK", "0");
        builder.environment().put("SEMGREP_SEND_METRICS", "off");
        builder.environment().put("LINUX_SOURCEKIT_LIB_PATH", "/opt/swiftlint");
        builder.environment().put("JAVA_TOOL_OPTIONS", "-Duser.home=/tmp -XX:MaxRAMPercentage=35");
        builder.environment().put("GOTELEMETRY", "off");
        builder.environment().put("GOTOOLCHAIN", "local");
        builder.environment().putAll(managedEnvironment);
        Process process = builder.start();
        FutureTask<String> reader = new FutureTask<>(() -> {
            try (var input = process.getInputStream()) {
                byte[] bytes = input.readNBytes(maxOutput + 1);
                if (bytes.length > maxOutput) {
                    process.destroyForcibly();
                    throw new AnalysisException("SCANNER_OUTPUT_LIMIT", "Scanner output exceeded its limit.");
                }
                return new String(bytes, StandardCharsets.UTF_8);
            }
        });
        Thread.ofVirtual().start(reader);
        FutureTask<String> errors=new FutureTask<>(() -> {
            try(var input=process.getErrorStream()){
                byte[] bytes=input.readNBytes(maxOutput+1);
                if(bytes.length>maxOutput){process.destroyForcibly();throw new AnalysisException("SCANNER_OUTPUT_LIMIT","Scanner diagnostic output exceeded its limit.");}
                return new String(bytes,StandardCharsets.UTF_8);
            }
        });
        Thread.ofVirtual().start(errors);
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS))
                throw new AnalysisException("SCANNER_TIMEOUT", "A local security tool exceeded its time limit.");
            return new Output(process.exitValue(), reader.get(5, TimeUnit.SECONDS),errors.get(5,TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new AnalysisException("SCAN_INTERRUPTED", "Analysis was interrupted.");
        } catch (ExecutionException ex) {
            if(ex.getCause() instanceof AnalysisException bounded)throw bounded;
            throw new AnalysisException("SCANNER_FAILED", "A local security tool could not complete.");
        } catch (TimeoutException ex) {
            throw new AnalysisException("SCANNER_FAILED", "A local security tool could not complete.");
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            if (process.isAlive()) process.destroyForcibly();
            reader.cancel(true);
            errors.cancel(true);
        }
    }
}
