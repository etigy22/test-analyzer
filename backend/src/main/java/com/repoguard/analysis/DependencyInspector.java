package com.repoguard.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.*;

/** Small, explicitly limited offline advisory set. No target dependency resolution or downloads. */
@Component
public class DependencyInspector {
    public static final String VERSION = "curated-2026.10.29.1";
    public record Advisory(String ecosystem, String packageName, String id, String fixedVersion, String url) { }
    private final ObjectMapper json;
    private static final List<Advisory> ADVISORIES = List.of(
            new Advisory("npm", "lodash", "CVE-2021-23337", "4.17.21", "https://nvd.nist.gov/vuln/detail/CVE-2021-23337"),
            new Advisory("pip", "pyyaml", "CVE-2020-14343", "5.4", "https://nvd.nist.gov/vuln/detail/CVE-2020-14343"),
            new Advisory("pip", "requests", "CVE-2023-32681", "2.31.0", "https://nvd.nist.gov/vuln/detail/CVE-2023-32681"),
            new Advisory("maven", "org.apache.logging.log4j:log4j-core", "CVE-2021-44228", "2.15.0", "https://nvd.nist.gov/vuln/detail/CVE-2021-44228"));
    public DependencyInspector(ObjectMapper json) { this.json = json; }
    public void inspect(String path, String text, BiConsumer<Advisory, String> finding) {
        try {
            if (path.endsWith("package-lock.json")) {
                JsonNode root = json.readTree(text);
                if (root.has("packages")) root.path("packages").fields().forEachRemaining(e -> {
                    String key = e.getKey(); int at = key.lastIndexOf("node_modules/");
                    if (at >= 0) check("npm", key.substring(at + 13), e.getValue().path("version").asText(), finding);
                });
                if (root.has("dependencies")) npm(root.path("dependencies"), finding, 0);
            }
            if (path.endsWith("requirements.txt")) {
                Matcher matcher = Pattern.compile("(?m)^\\s*([A-Za-z0-9_.-]+)==([0-9]+(?:\\.[0-9]+){1,3})\\s*(?:#.*)?$").matcher(text);
                while (matcher.find()) check("pip", matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2), finding);
            }
            if (path.endsWith("pom.xml")) {
                // Restricted text extraction, not an XML loader: no entities, DTDs, build or property expansion.
                Matcher blocks = Pattern.compile("<dependency>[^<]*(?:(?!</dependency>).){0,4096}</dependency>", Pattern.DOTALL).matcher(text);
                while (blocks.find()) {
                    String block = blocks.group();
                    String group = tag(block, "groupId"), artifact = tag(block, "artifactId"), version = tag(block, "version");
                    if (group != null && artifact != null && version != null) check("maven", group + ":" + artifact, version, finding);
                }
            }
        } catch (Exception ignored) {
            throw new AnalysisException("MANIFEST_INVALID", "A supported dependency manifest could not be read safely.");
        }
    }
    private void npm(JsonNode dependencies, BiConsumer<Advisory, String> finding, int depth) {
        if (depth > 16) throw new AnalysisException("MANIFEST_DEPTH", "Dependency manifest nesting exceeds its limit.");
        dependencies.fields().forEachRemaining(e -> {
            check("npm", e.getKey(), e.getValue().path("version").asText(), finding);
            if (e.getValue().has("dependencies")) npm(e.getValue().path("dependencies"), finding, depth + 1);
        });
    }
    private static String tag(String text, String tag) {
        Matcher matcher = Pattern.compile("<" + tag + ">([^<]{1,256})</" + tag + ">").matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }
    private static void check(String ecosystem, String name, String version, BiConsumer<Advisory, String> finding) {
        if (!version.matches("[0-9]+(?:\\.[0-9]+){1,3}")) return;
        for (Advisory advisory : ADVISORIES) {
            if (!advisory.ecosystem.equals(ecosystem) || !advisory.packageName.equals(name)) continue;
            // This Log4Shell advisory applies only to Log4j 2, not Log4j 1 or maintained 2.12.2/2.3.1 backports.
            if (ecosystem.equals("maven") && (!version.startsWith("2.")
                    || version.startsWith("2.12.") && !less(version, "2.12.2")
                    || version.startsWith("2.3.") && !less(version, "2.3.1"))) continue;
            if (advisory.packageName.equals("requests") && less(version, "2.3.0")) continue;
            if (less(version, advisory.fixedVersion)) finding.accept(advisory, version);
        }
    }
    static boolean less(String a, String b) {
        String[] left = a.split("\\."), right = b.split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int l = i < left.length ? Integer.parseInt(left[i]) : 0, r = i < right.length ? Integer.parseInt(right[i]) : 0;
            if (l != r) return l < r;
        }
        return false;
    }
}
