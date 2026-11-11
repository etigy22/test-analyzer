package com.repoguard.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class ContextRulesTest {
    @TempDir Path root;
    record Example(String extension,String source,String rule) { }
    static Stream<Example> dangerous() {
        return Stream.of(
            new Example("ts","eval(input);","RG-JS-EVAL"),
            new Example("java","db.execute(\"SELECT id FROM users WHERE id=\" /* comment */ + user);","RG-SQL-CONCAT"),
            new Example("java","db.execute(\"SELECT id FROM users WHERE id=\" + /* comment */ user);","RG-SQL-CONCAT"),
            new Example("java","class Example { void safe(){String placeholders=String.join(\",\",Collections.nCopies(ids.size(),\"?\"));} void unsafe(String placeholders){jdbc.queryForList(\"SELECT id FROM users WHERE id in (\"+placeholders+\")\");}}","RG-SQL-CONCAT"),
            new Example("java","class Example { void unsafe(){jdbc.queryForList(\"SELECT id FROM users WHERE id in (\"+placeholders+\")\");} void safe(){String placeholders=String.join(\",\",Collections.nCopies(ids.size(),\"?\"));}}","RG-SQL-CONCAT"),
            new Example("java","db.execute(\"SELECT \" + user);","RG-SQL-CONCAT"),
            new Example("java","String sql = \"SELECT \" + column + \" FROM users\";","RG-SQL-CONCAT"),
            new Example("java","String sql = String.format(\"SELECT id FROM users WHERE id=%s\",user);","RG-SQL-CONCAT"),
            new Example("java","// password = \"real-hardcoded-credential\"","RG-SECRET"),
            new Example("ts","eval /* comment */ \n(input);","RG-JS-EVAL"),
            new Example("ts","const x = 'value' / eval(input) / 2;","RG-JS-EVAL"),
            new Example("ts","const x = number / eval(input) / 2;","RG-JS-EVAL"),
            new Example("ts","const x = `result ${eval(input)}`;","RG-JS-EVAL"),
            new Example("ts","node.innerHTML = input;","RG-JS-DOM"),
            new Example("py","value = f\"{eval(user)}\"","RG-PY-EVAL"),
            new Example("java","new ProcessBuilder(userInput).start();","RG-JAVA-EXEC"),
            new Example("java","jdbc.queryForList(\"SELECT id FROM users WHERE name='\" + input + \"'\");","RG-SQL-CONCAT"),
            new Example("java","String sql = \"SELECT id FROM users WHERE id=\"\n + input; jdbc.queryForList(sql);","RG-SQL-CONCAT"),
            new Example("java","String sql = \"SELECT id FROM users\"; sql += user; jdbc.queryForList(sql);","RG-SQL-CONCAT"),
            new Example("java","String sql = \"SELECT id FROM users\"; jdbc.queryForList(sql + input);","RG-SQL-CONCAT"),
            new Example("ts","db.query(`SELECT id FROM users WHERE id=${input}`);","RG-SQL-CONCAT"),
            new Example("py","db.execute(f\"SELECT id FROM users WHERE id={user}\")","RG-SQL-CONCAT"),
            new Example("py","db.execute(\"SELECT id FROM users WHERE id=%s\" % user)","RG-SQL-CONCAT"),
            new Example("py","db.execute(\"SELECT id FROM users WHERE id={}\".format(user))","RG-SQL-CONCAT"),
            new Example("php","$db->query(\"SELECT id FROM users WHERE id=\" . $user);","RG-SQL-CONCAT"),
            new Example("java","String placeholders = String.join(\",\",Collections.nCopies(ids.size(),\"?\")); placeholders = input; jdbc.queryForList(\"SELECT id FROM users WHERE id in (\"+placeholders+\")\");","RG-SQL-CONCAT"),
            new Example("py","password = \"real-hardcoded-credential\"","RG-SECRET"),
            new Example("ts","const config = {apiKey: \"real-hardcoded-credential\"};","RG-SECRET"),
            new Example("json","{\"password\": \"real-hardcoded-credential\"}","RG-SECRET"),
            new Example("ts","const config = {SECRET: 'real-hardcoded-credential'};","RG-SECRET"),
            new Example("py","password = 'password'","RG-SECRET"));
    }
    static Stream<Example> inert() {
        return Stream.of(
            new Example("ts","const fixture = 'eval(input)';","RG-JS-EVAL"),
            new Example("ts","// eval(input)\n/* new Function(input) */","RG-JS-EVAL"),
            new Example("ts","const fixture = `eval(input)`;","RG-JS-EVAL"),
            new Example("ts","const fixture = /eval(input)/;","RG-JS-EVAL"),
            new Example("ts","function sample(){return /eval(input)/;}","RG-JS-EVAL"),
            new Example("go","const fixture = `exec.Command(input)`;","RG-GO-PROCESS"),
            new Example("java","String fixture = \"Runtime.getRuntime().exec(input)\";","RG-JAVA-EXEC"),
            new Example("java","String fixture = \"\"\"\nnew ProcessBuilder(input)\n\"\"\";","RG-JAVA-EXEC"),
            new Example("py","example = '''\neval(input)\n'''","RG-PY-EVAL"),
            new Example("ts","const selectable = true; const label = `Delete ${id}`;","RG-SQL-CONCAT"),
            new Example("yml","updater-url: ${REPOGUARD_UPDATER_URL:http://updater:8080}","RG-SQL-CONCAT"),
            new Example("java","jdbc.queryForList(\"SELECT id FROM users WHERE id=?\", input);","RG-SQL-CONCAT"),
            new Example("py","db.execute(\"SELECT id FROM users WHERE id=%s\", (user,))","RG-SQL-CONCAT"),
            new Example("java","String sql = \"SELECT id FROM users \" + \"WHERE id=?\";","RG-SQL-CONCAT"),
            new Example("java","String placeholders=String.join(\",\",Collections.nCopies(ids.size(),\"?\")); jdbc.queryForList(\"select id from users where id in (\"+placeholders+\")\", ids.toArray());","RG-SQL-CONCAT"),
            new Example("ts","const fixture = 'password = \"embedded-fixture-value\"';","RG-SECRET"),
            new Example("ts","const labels = {SECRET: 'Secrets', SECURITY: 'Security', QUALITY: 'Quality', DEPENDENCY: 'Dependencies'};","RG-SECRET"));
    }
    private List<AnalysisModels.Finding> scan(Example example) throws Exception {
        Path testDirectory=Files.createDirectories(root.resolve("tests"));
        Files.writeString(testDirectory.resolve("security.spec."+example.extension()),example.source());
        return new SourceRuleEngine(TestSettings.runtime(),new DependencyInspector(new ObjectMapper()))
                .analyze(root,"fixture-hash",1,"fixture-db",Long.MAX_VALUE).findings();
    }
    @ParameterizedTest @MethodSource("dangerous") void realPatternsInTestFilesRemainDetected(Example example) throws Exception {
        assertThat(scan(example)).anyMatch(f -> f.ruleId().equals(example.rule()) && f.severity().equals("HIGH"));
    }
    @ParameterizedTest @MethodSource("inert") void dataAndSafeBindingsAreNotExecutableVulnerabilities(Example example) throws Exception {
        assertThat(scan(example)).noneMatch(f -> f.ruleId().equals(example.rule()));
    }
    @Test void providerTokensInTestStringsStillDetectedAndMasked() throws Exception {
        String providerToken="ghp_"+"a".repeat(32);
        var findings=scan(new Example("ts","const fixture='"+providerToken+"';","RG-SECRET"));
        assertThat(findings).anyMatch(f -> f.ruleId().equals("RG-SECRET"));
        assertThat(new ObjectMapper().writeValueAsString(findings)).doesNotContain(providerToken);
    }
    @Test void multilineContextRetainsOriginalLineNumbers() throws Exception {
        var findings=scan(new Example("ts","/*\neval(fake)\n*/\nconst fixture=`data\nmore`;\neval(user);","RG-JS-EVAL"));
        assertThat(findings.stream().filter(f -> f.ruleId().equals("RG-JS-EVAL")).map(AnalysisModels.Finding::line)).containsExactly(6);
    }
    @Test void embeddedCredentialCandidatesAreRetainedForReviewRatherThanHidden() throws Exception {
        var findings=scan(new Example("ts","const fixture = 'password = \"embedded-real-or-test-credential\"';","RG-SECRET-DATA"));
        assertThat(findings).anyMatch(f -> f.ruleId().equals("RG-SECRET-DATA") && f.severity().equals("MEDIUM"));
        assertThat(new ObjectMapper().writeValueAsString(findings)).doesNotContain("embedded-real-or-test-credential");
    }
    @Test void escapedFixtureNewlinesDoNotHideEmbeddedCredentialCandidates() throws Exception {
        var findings=scan(new Example("ts","const fixtures={'fixture.py':'eval(input)\\npassword = \"embedded-real-or-test-credential\"'};","RG-SECRET-DATA"));
        assertThat(findings).anyMatch(f -> f.ruleId().equals("RG-SECRET-DATA"));
        assertThat(new ObjectMapper().writeValueAsString(findings)).doesNotContain("embedded-real-or-test-credential");
    }
    @Test void lexicalPassHonorsJobDeadline() {
        assertThatThrownBy(() -> new SourceContext("eval(input)",com.repoguard.model.LanguageGroup.JAVASCRIPT_TYPESCRIPT,System.nanoTime()-1))
                .isInstanceOfSatisfying(AnalysisException.class,e -> assertThat(e.code()).isEqualTo("SCAN_TIMEOUT"));
    }
    @Test void hostileInterpolationNestingFailsClosed() {
        String nested="eval(input)";
        for(int i=0;i<70;i++)nested="`${"+nested+"}`";
        final String source=nested;
        assertThatThrownBy(() -> new SourceContext(source,com.repoguard.model.LanguageGroup.JAVASCRIPT_TYPESCRIPT,Long.MAX_VALUE))
                .isInstanceOfSatisfying(AnalysisException.class,e -> assertThat(e.code()).isEqualTo("SOURCE_NESTING_LIMIT"));
    }
}
