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
}
