package com.repoguard.analysis;

import com.repoguard.importing.SafeZipExtractor;
import org.apache.commons.compress.archivers.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.repoguard.analysis.AnalysisModels.*;

class RealRepositoryRegressionTest {
    @TempDir Path root;
    private Path archive(String[][] entries) throws Exception {
        Path archive=root.resolve(UUID.randomUUID()+".zip");
        try(var output=new ZipArchiveOutputStream(archive)) {
            for(String[] spec:entries){var entry=new ZipArchiveEntry(spec[0]);entry.setUnixMode(spec[1].equals("link")?0120777:0100644);output.putArchiveEntry(entry);output.write(spec[2].getBytes(java.nio.charset.StandardCharsets.UTF_8));output.closeArchiveEntry();}
        }
        return archive;
    }
    @Test void preservesSafeRelativeFileLinksWithoutCreatingFilesystemLinks() throws Exception {
        Path destination=Files.createDirectory(root.resolve("output"));
        Path archive=archive(new String[][]{{"repo/skills/do/SKILL.md","link","../../real.md"},{"repo/alias.md","link","skills/do/SKILL.md"},{"repo/real.md","file","passive text"}});
        var links=new SafeZipExtractor(TestSettings.properties(root)).extract(archive,destination,Long.MAX_VALUE);
        assertThat(links).hasSize(2);
        assertThat(Files.isSymbolicLink(destination.resolve("repo/skills/do/SKILL.md"))).isFalse();
        assertThat(Files.readString(destination.resolve("repo/alias.md"))).isEqualTo("passive text");
    }
    @Test void rejectsEscapingMissingCyclicDirectoryAndAbsoluteLinksBeforeWritingFiles() throws Exception {
        for(String target:List.of("../../outside","/etc/passwd","C:/Windows/file","missing.md","alias.md")) {
            Path destination=Files.createDirectory(root.resolve(UUID.randomUUID().toString()));
            Path archive=archive(new String[][]{{"repo/safe.md","file","safe"},{"repo/alias.md","link",target}});
            assertThatThrownBy(()->new SafeZipExtractor(TestSettings.properties(root)).extract(archive,destination,Long.MAX_VALUE)).isInstanceOf(AnalysisException.class);
            try(var files=Files.list(destination)){assertThat(files.toList()).isEmpty();}
        }
        Path destination=Files.createDirectory(root.resolve("directory-link"));
        assertThatThrownBy(()->new SafeZipExtractor(TestSettings.properties(root)).extract(archive(new String[][]{{"repo/file.md","file","safe"},{"repo/alias.md","link","."}}),destination,Long.MAX_VALUE)).isInstanceOf(AnalysisException.class);
    }
    private Finding finding(String fingerprint,String severity,String category,String tool){return new Finding(fingerprint,category,severity,"rule","title","explanation","remediation",tool,"version","Java","src/file.java",1,fingerprint,null);}
    @Test void copiedLinkBytesRemainSubjectToTheExpandedArchiveLimit() throws Exception {
        var random=new Random(17);
        StringBuilder payload=new StringBuilder();for(int i=0;i<800000;i++)payload.append((char)('a'+random.nextInt(26)));
        Path archive=archive(new String[][]{{"repo/source.txt","file",payload.toString()},{"repo/first.txt","link","source.txt"},{"repo/second.txt","link","source.txt"}});
        Path destination=Files.createDirectory(root.resolve("expanded-link-limit"));
        assertThatThrownBy(()->new SafeZipExtractor(TestSettings.properties(root)).extract(archive,destination,Long.MAX_VALUE)).isInstanceOfSatisfying(AnalysisException.class,e->assertThat(e.code()).isEqualTo("ZIP_SIZE_LIMIT"));
        try(var files=Files.list(destination)){assertThat(files.toList()).isEmpty();}
    }
    @Test void materializesInternalDirectoryLinksIncludingImplicitDirectoriesWithoutOsLinks() throws Exception {
        Path archive=archive(new String[][]{{"repo/HomebrewFormula","link","pkg/brew"},{"repo/pkg/brew/formula.rb","file","passive formula text"}});
        Path destination=Files.createDirectory(root.resolve("directory-alias"));
        var links=new SafeZipExtractor(TestSettings.properties(root)).extract(archive,destination,Long.MAX_VALUE);
        assertThat(links).containsExactly("repo/HomebrewFormula");
        assertThat(Files.isDirectory(destination.resolve("repo/HomebrewFormula"))).isTrue();
        assertThat(Files.isSymbolicLink(destination.resolve("repo/HomebrewFormula"))).isFalse();
        assertThat(Files.readString(destination.resolve("repo/HomebrewFormula/formula.rb"))).isEqualTo("passive formula text");
    }
    @Test void rejectsIndirectDirectoryLinkCyclesBeforeAnyFilesystemWrites() throws Exception {
        Path archive=archive(new String[][]{{"repo/a/to-b","link","../b"},{"repo/b/to-a","link","../a"},{"repo/a/plain.txt","file","safe"}});
        Path destination=Files.createDirectory(root.resolve("directory-cycle"));
        assertThatThrownBy(()->new SafeZipExtractor(TestSettings.properties(root)).extract(archive,destination,Long.MAX_VALUE)).isInstanceOf(AnalysisException.class);
        try(var files=Files.list(destination)){assertThat(files.toList()).isEmpty();}
    }
    @Test void lateSecurityAndDependencyFindingsDisplaceEarlyQualityNoiseWithBoundedStorage() {
        var findings=new BoundedFindings(2);
        findings.add(finding("quality-a","LOW","QUALITY","RepoGuard"));findings.add(finding("quality-b","LOW","QUALITY","RepoGuard"));
        findings.add(finding("dependency","CRITICAL","DEPENDENCY","Trivy"));findings.add(finding("security","HIGH","SECURITY","Semgrep"));
        findings.add(finding("security","HIGH","SECURITY","Semgrep"));
        assertThat(findings.values()).extracting(Finding::fingerprint).containsExactly("dependency","security");
        assertThat(findings.truncated()).isTrue();assertThat(findings.size()).isEqualTo(2);
    }
    @Test void keepsNormalLongPathsReadableWhileMaskingRecognizableCredentials() {
        assertThat(SecretMasker.safePath("src/services/sqlite/SessionStore.ts")).isEqualTo("src/services/sqlite/SessionStore.ts");
        assertThat(SecretMasker.safePath("src/ghp_"+"a".repeat(32)+"/file.ts")).doesNotContain("a".repeat(32));
    }
}
