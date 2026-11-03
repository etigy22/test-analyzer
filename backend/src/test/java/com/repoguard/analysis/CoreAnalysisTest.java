package com.repoguard.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repoguard.importing.SafeZipExtractor;
import com.repoguard.security.BrowserRequestFilter;
import org.apache.commons.compress.archivers.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.mock.web.*;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoreAnalysisTest {
    @TempDir Path root;
    ObjectMapper json = new ObjectMapper();
    SourceRuleEngine engine() { return new SourceRuleEngine(TestSettings.runtime(),new DependencyInspector(json)); }
    @ParameterizedTest @CsvSource({"java,JAVA","py,PY","rs,RUST","swift,SWIFT","ts,JS","cpp,CPP","cs,CS","go,GO","kt,KT","php,PHP"})
    void languageSpecificSecurityAndQualityWithoutExecutingFixtures(String extension,String id) throws Exception {
        String source = switch(extension) {
            case "java" -> "Runtime.getRuntime().exec(input);\ncatch (Exception e) {}";
            case "py" -> "eval(input)\nexcept:";
            case "rs" -> "Command::new(input);\nvalue.unwrap();";
            case "swift" -> "let p = Process()\nlet x = try! read()";
            case "ts" -> "eval(input);\nif (a == b) {}";
            case "cpp" -> "strcpy(target,input);\nusing namespace std;";
            case "cs" -> "Process.Start(input);\ncatch (Exception e) {}";
            case "go" -> "exec.Command(input)\n_, _ := read()";
            case "kt" -> "Runtime.getRuntime().exec(input)\nval x = value!!";
            case "php" -> "eval($input);\nif ($a == $b) {}";
            default -> throw new AssertionError();
        };
        Files.writeString(root.resolve("fixture."+extension),source);
        var result=engine().analyze(root,"hash",12,"signed-db",Long.MAX_VALUE);
        assertThat(result.findings()).anyMatch(f -> f.category().equals("SECURITY") && f.ruleId().startsWith("RG-"+id));
        assertThat(result.findings()).anyMatch(f -> f.category().equals("QUALITY") && f.ruleId().startsWith("RG-"+id));
        assertThat(result.coverage()).anyMatch(c -> c.check().equals("SECURITY") && c.status().equals("COMPLETED"));
        assertThat(result.coverage()).anyMatch(c -> c.status().equals("UNSUPPORTED"));
    }
    @Test void masksSecretsAndKeepsFingerprintsStableAcrossInsertedLines() throws Exception {
        String secret="sensitive-credential-for-test-123"; Path source=root.resolve("app.py");
        Files.writeString(source,"password = \""+secret+"\"\neval(\""+secret+"\")\n");
        var first=engine().analyze(root,"hash",12,"db",Long.MAX_VALUE);
        assertThat(first.findings()).anyMatch(f -> f.category().equals("SECRET"));
        assertThat(json.writeValueAsString(first)).doesNotContain(secret);
        Files.writeString(source,"# extra line\n"+Files.readString(source));
        var second=engine().analyze(root,"hash",12,"db",Long.MAX_VALUE);
        assertThat(second.findings().stream().map(f -> f.fingerprint()).toList()).containsExactlyInAnyOrderElementsOf(first.findings().stream().map(f -> f.fingerprint()).toList());
        assertThat(second.findings()).allMatch(f -> f.snippet()==null || f.snippet().endLine()-f.snippet().startLine()<5);
    }
    @Test void mixedLanguagesBinarySkipAndFindingLimitAreExplicit() throws Exception {
        Files.writeString(root.resolve("one.py"),"eval(x)\nexcept:\n"); Files.writeString(root.resolve("two.ts"),"eval(x);\na == b;\n");
        Files.writeString(root.resolve("three.rs"),"Command::new(x);\nx.unwrap();\n"); Files.write(root.resolve("binary.java"),new byte[]{0,1,2});
        var s=TestSettings.runtime();s.maxFindings=2;
        var result=new SourceRuleEngine(s,new DependencyInspector(json)).analyze(root,"hash",1,"db",Long.MAX_VALUE);
        assertThat(result.languages()).hasSize(4);assertThat(result.findings()).hasSize(2);assertThat(result.truncated()).isTrue();
        assertThat(result.coverage()).anyMatch(c -> c.status().equals("SKIPPED"));
    }
    @Test void detectsBundledCvesWithoutResolvingDependencies() throws Exception {
        Files.writeString(root.resolve("package-lock.json"),"{\"packages\":{\"node_modules/lodash\":{\"version\":\"4.17.20\"}}}");
        Files.writeString(root.resolve("requirements.txt"),"requests==2.30.0\npyyaml==5.3\n");
        Files.writeString(root.resolve("pom.xml"),"<dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-core</artifactId><version>2.14.1</version></dependency>");
        var result=engine().analyze(root,"hash",1,"db",Long.MAX_VALUE);
        assertThat(result.findings().stream().filter(f -> f.category().equals("DEPENDENCY")).map(f -> f.ruleId()).toList()).containsExactlyInAnyOrder("CVE-2021-23337","CVE-2023-32681","CVE-2020-14343","CVE-2021-44228");
    }
    Path zip(String name,String content,int mode) throws Exception {
        Path file=root.resolve("test.zip");
        try(var out=new ZipArchiveOutputStream(file)){var e=new ZipArchiveEntry(name);if(mode!=0)e.setUnixMode(mode);out.putArchiveEntry(e);out.write(content.getBytes());out.closeArchiveEntry();}
        if (name.contains("\\")) {
            byte[] data=Files.readAllBytes(file), needle=name.replace('\\','/').getBytes();
            for(int i=0;i<=data.length-needle.length;i++){
                boolean match=true;for(int j=0;j<needle.length;j++)if(data[i+j]!=needle[j]){match=false;break;}
                if(match)for(int j=0;j<needle.length;j++)if(name.charAt(j)=='\\')data[i+j]='\\';
            }
            Files.write(file,data);
        }
        return file;
    }
    @ParameterizedTest @ValueSource(strings={"../escape.py","/absolute.py","C:/escape.py","dir\\escape.py","dir/../../file","dir//file","NUL.txt","dir/file.","dir/file ","dir/a:b","dir/\u202efile"})
    void rejectsUnsafeZipPathsBeforeWriting(String name) throws Exception {
        Path dest=Files.createDirectory(root.resolve("output")),archive=zip(name,"passive",0);
        assertThatThrownBy(() -> new SafeZipExtractor(TestSettings.properties(root)).extract(archive,dest,Long.MAX_VALUE)).isInstanceOf(AnalysisException.class);
        try(var files=Files.list(dest)){assertThat(files.toList()).isEmpty();}
    }
    @Test void rejectsSymlinksBombsAndCaseCollisions() throws Exception {
        Path dest=Files.createDirectory(root.resolve("output"));var extractor=new SafeZipExtractor(TestSettings.properties(root));
        assertThatThrownBy(() -> extractor.extract(zip("link","../outside",0120777),dest,Long.MAX_VALUE)).isInstanceOfSatisfying(AnalysisException.class,e -> assertThat(e.code()).isEqualTo("ZIP_SPECIAL_FILE"));
        assertThatThrownBy(() -> extractor.extract(zip("bomb.txt","A".repeat(200000),0),dest,Long.MAX_VALUE)).isInstanceOfSatisfying(AnalysisException.class,e -> assertThat(e.code()).isEqualTo("ZIP_RATIO_LIMIT"));
        Path archive=root.resolve("test.zip");try(var output=new ZipArchiveOutputStream(archive)){for(String name:new String[]{"Folder/test.py","folder/TEST.py"}){output.putArchiveEntry(new ZipArchiveEntry(name));output.write(1);output.closeArchiveEntry();}}
        assertThatThrownBy(() -> extractor.extract(archive,dest,Long.MAX_VALUE)).isInstanceOfSatisfying(AnalysisException.class,e -> assertThat(e.code()).isEqualTo("ZIP_COLLISION"));
    }
    @Test void readOnlyExtractedTreeIsRemovedAndOutsideCleanupIsRefused() throws Exception {
        Path work=Files.createDirectory(root.resolve("work")),dest=Workspace.create(work,0);
        new SafeZipExtractor(TestSettings.properties(root)).extract(zip("repo/code.py","eval(input)",0100644),dest,Long.MAX_VALUE);
        assertThat(Files.readString(dest.resolve("repo/code.py"))).isEqualTo("eval(input)");
        Workspace.delete(work,dest);assertThat(dest).doesNotExist();assertThatThrownBy(() -> Workspace.delete(work,root)).isInstanceOf(java.io.IOException.class);
    }
    @Test void malwareFailureNeverStartsExtractionOrCodeAnalysisAndCleansUp() throws Exception {
        var malware=mock(MalwareScanner.class);var extractor=mock(SafeZipExtractor.class);var rules=mock(SourceRuleEngine.class);
        when(malware.scan(any())).thenThrow(new AnalysisException("MALWARE_UNAVAILABLE","No signatures."));
        var worker=new WorkerController(TestSettings.properties(root),TestSettings.runtime(),malware,extractor,rules);
        var request=new MockHttpServletRequest();request.setContent(new byte[30]);
        assertThatThrownBy(() -> worker.analyze(UUID.randomUUID(),request)).isInstanceOf(AnalysisException.class);verifyNoInteractions(extractor,rules);
        try(var files=Files.list(root.resolve("work"))){assertThat(files.toList()).isEmpty();}assertThat(worker.status().phase()).isEqualTo("IDLE");
    }
    @Test void rejectsCrossOriginRebindingHostAndMissingMutationHeader() throws Exception {
        var filter=new BrowserRequestFilter(TestSettings.properties(root));
        for(String variant:new String[]{"valid","origin","host","header"}){
            var request=new MockHttpServletRequest("POST","/api/scans/github");request.addHeader("Host",variant.equals("host")?"attacker.example":"localhost:8088");request.addHeader("Origin",variant.equals("origin")?"https://attacker.example":"http://localhost:8088");if(!variant.equals("header"))request.addHeader("X-RepoGuard-Request","true");
            var response=new MockHttpServletResponse();filter.doFilter(request,response,(a,b) -> ((jakarta.servlet.http.HttpServletResponse)b).setStatus(204));assertThat(response.getStatus()).as(variant).isEqualTo(variant.equals("valid")?204:403);
        }
    }
}
