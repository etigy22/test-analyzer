package com.repoguard.analysis;
import com.repoguard.config.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
public final class TestSettings {
    private TestSettings() { }
    public static RepoGuardProperties properties(Path root) {
        return new RepoGuardProperties("http://localhost:8088",new RepoGuardProperties.Storage(root.resolve("quarantine"),root.resolve("work"),root.resolve("reports")),
                new RepoGuardProperties.ImportLimits(1048576,2097152,1048576,30,8,100,Duration.ofSeconds(5),Duration.ofSeconds(10)),
                new RepoGuardProperties.Security(true,true,false),List.of("http://localhost:8088"));
    }
    public static RuntimeSettings runtime() {
        RuntimeSettings s = new RuntimeSettings(); s.maxFindings=1000; s.maxTextBytes=1048576;
        s.snippetLines=5; s.snippetLineLength=160; s.minFreeBytes=0; s.historyDays=90;
        s.malwareTimeout=Duration.ofSeconds(5); return s;
    }
}
