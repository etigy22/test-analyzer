package com.repoguard.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExternalScannersTest {
    @TempDir Path root;
    @Test void rustfmtSourceDiffContainingErrorTextIsNotAParserFailure() {
        assertThat(ExternalScanners.rustParseFailed("Diff in source.rs:1:\n-println!(\"error: example\");\n+    println!(\"error: example\");")).isFalse();
        assertThat(ExternalScanners.rustParseFailed("error: expected expression\n --> source.rs:1:1")).isTrue();
    }
    @Test void reportsTaggedSemgrepParserLocationsWithoutPersistingRawSourceMessages() throws Exception {
        var settings=TestSettings.runtime();settings.signatureDir=root.resolve("missing");
        Path source=Files.createDirectory(root.resolve("input")),file=source.resolve("fixture.cpp");Files.writeString(file,"passive text");
        var json=new ObjectMapper();
        try(var process=mockStatic(TrustedProcess.class)) {
            process.when(()->TrustedProcess.run(anyList(),any(Path.class),any(Duration.class))).thenReturn(new TrustedProcess.Output(0,"managed-version"));
            var error=Map.of("type",List.of("PartialParsing",List.of(Map.of("path",file.toString(),"start",Map.of("line",7)))),"message","do-not-store-raw-source-secret");
            process.when(()->TrustedProcess.run(anyList(),any(Path.class),any(Duration.class),anyInt())).thenReturn(new TrustedProcess.Output(0,json.writeValueAsString(Map.of("results",List.of(),"errors",List.of(error)))));
            var batch=new ExternalScanners(json,settings,path->true).analyze(source,Set.of(),Long.MAX_VALUE,root);
            assertThat(batch.coverage()).anyMatch(c->c.reason().contains("PartialParsing; file=fixture.cpp; line=7"));
            assertThat(json.writeValueAsString(batch)).doesNotContain("do-not-store-raw-source-secret");
        }
    }
    @Test void masksUnterminatedAndVeryLongLiteralsWithoutRecursiveRegex() {
        assertThat(SecretMasker.mask("value = \"shortsecret")).doesNotContain("shortsecret");
        assertThat(SecretMasker.mask("value = \""+"x".repeat(1000000)+"\"")).isEqualTo("value = \"[REDACTED]\"");
    }
    @Test void unavailableToolsAndDatabaseNeverBecomeSuccessfulZeroFindingChecks() throws Exception {
        var settings=TestSettings.runtime();settings.signatureDir=root.resolve("missing-signatures");
        Path source=Files.createDirectory(root.resolve("source"));Files.writeString(source.resolve("a.py"),"eval(input)");
        var batch=new ExternalScanners(new ObjectMapper(),settings,path -> false).analyze(source,Set.of("Python","Java","Rust","Swift","Kotlin","Go","PHP","C/C++","JavaScript/TypeScript"),Long.MAX_VALUE,root);
        assertThat(batch.findings()).isEmpty();
        assertThat(batch.coverage()).allMatch(c -> Set.of("UNSUPPORTED","SKIPPED").contains(c.status()));
        assertThat(batch.coverage()).anyMatch(c -> c.tool().equals("Trivy") && c.status().equals("SKIPPED"));
    }
    @Test void normalizesScannerFindingsWithoutRawMessagesSecretsOrOutsidePaths() throws Exception {
        var settings=TestSettings.runtime();settings.signatureDir=root.resolve("missing-signatures");
        Path source=Files.createDirectory(root.resolve("source")),file=source.resolve("a.py");
        Files.writeString(file,"eval(input)\npassword = \"secret-for-external-test-1234\"");
        var json=new ObjectMapper();
        try(var process=mockStatic(TrustedProcess.class)) {
            process.when(() -> TrustedProcess.run(anyList(),any(Path.class),any(Duration.class))).thenReturn(new TrustedProcess.Output(0,"managed-test-version"));
            process.when(() -> TrustedProcess.run(anyList(),any(Path.class),any(Duration.class),anyInt())).thenAnswer(call -> {
                List<String> command=call.getArgument(0);
                if(command.getFirst().endsWith("bandit"))return new TrustedProcess.Output(1,json.writeValueAsString(Map.of("results",List.of(Map.of("filename",file.toString(),"line_number",1,"test_id","B307","issue_severity","HIGH","issue_text","secret-for-external-test-1234"),Map.of("filename",root.resolve("outside.py").toString(),"line_number",1,"test_id","B999")))));
                if(command.getFirst().endsWith("ruff"))return new TrustedProcess.Output(2,"sensitive raw scanner error");
                assertThat(command).contains("--config","/etc/repoguard/scanners/semgrep.yaml","--metrics=off","--no-git-ignore","--disable-nosem");
                return new TrustedProcess.Output(0,"{\"results\":[],\"errors\":[]}");
            });
            var batch=new ExternalScanners(json,settings,path -> true).analyze(source,Set.of("Python"),Long.MAX_VALUE,root);
            assertThat(batch.findings()).hasSize(1);
            assertThat(batch.findings().getFirst().tool()).isEqualTo("Bandit");
            assertThat(json.writeValueAsString(batch)).doesNotContain("secret-for-external-test-1234","sensitive raw scanner error","outside.py");
            assertThat(batch.coverage()).anyMatch(c -> c.tool().equals("Ruff") && c.status().equals("FAILED"));
            assertThat(batch.coverage()).anyMatch(c -> c.tool().equals("Bandit") && c.status().equals("COMPLETED"));
        }
    }
}
