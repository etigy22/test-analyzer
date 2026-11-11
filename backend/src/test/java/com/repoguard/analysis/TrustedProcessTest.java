package com.repoguard.analysis;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class TrustedProcessTest {
    @TempDir Path root;
    private String javaBinary(){return Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();}
    @Test void stdoutAndDiagnosticsAreCapturedSeparately() throws Exception {
        var result=TrustedProcess.run(List.of(javaBinary(),"--version"),root,Duration.ofSeconds(10));
        assertThat(result.exit()).isZero();
        assertThat(result.text()).contains("openjdk").doesNotContain("Picked up JAVA_TOOL_OPTIONS");
        assertThat(result.stderr()).contains("JAVA_TOOL_OPTIONS");
    }
    @Test void stderrIsBoundedToo() {
        assertThatThrownBy(() -> TrustedProcess.run(List.of(javaBinary(),"--version"),root,Duration.ofSeconds(10),4))
                .isInstanceOfSatisfying(AnalysisException.class,error -> assertThat(error.code()).isEqualTo("SCANNER_OUTPUT_LIMIT"));
    }
    @Test void rejectsShellRepositoryExecutablesAndAlternateRuntimeScriptsBeforeLaunch() {
        for(var command:List.of(List.of("/bin/sh","-c","echo unsafe"),List.of(root.resolve("semgrep").toString()),
                List.of(javaBinary(),"-jar",root.resolve("plugin.jar").toString()),
                List.of("/usr/local/bin/node",root.resolve("plugin.js").toString()),
                List.of("/usr/bin/../bin/clamscan","--version"),
                List.of("/usr/bin/go","run",root.resolve("payload.go").toString()),
                List.of("/opt/repoguard/bin/bandit","-c","/etc/repoguard/scanners/../../../tmp/rules.yaml"),
                List.of("/opt/repoguard/bin/semgrep","--config",root.resolve("rules.yaml").toString()),
                List.of("/opt/java/openjdk/bin/java","-jar","/opt/detekt/cli.jar","--plugins=/tmp/plugin.jar"))) {
            assertThatThrownBy(() -> TrustedProcess.run(command,root,Duration.ofSeconds(1)))
                    .isInstanceOfSatisfying(AnalysisException.class,e -> assertThat(e.code()).isEqualTo("UNTRUSTED_COMMAND"));
        }
    }
    @Test void rejectsRuntimeEnvironmentInjection() {
        assertThatThrownBy(() -> TrustedProcess.run(List.of(javaBinary(),"--version"),root,Duration.ofSeconds(1),65536,
                java.util.Map.of("JAVA_TOOL_OPTIONS","-javaagent:/tmp/plugin.jar")))
                .isInstanceOfSatisfying(AnalysisException.class,e -> assertThat(e.code()).isEqualTo("UNTRUSTED_COMMAND"));
    }
}
