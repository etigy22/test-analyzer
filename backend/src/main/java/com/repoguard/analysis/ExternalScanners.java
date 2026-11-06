package com.repoguard.analysis;

import com.fasterxml.jackson.databind.*;
import com.repoguard.config.RuntimeSettings;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static com.repoguard.analysis.AnalysisModels.*;

/** Fixed source-only invocations. Target configuration, plugins and suppression files are never loaded. */
@Component
public class ExternalScanners {
    public record Batch(List<Finding> findings, List<Coverage> coverage) { }
    private final ObjectMapper json;
    private final RuntimeSettings settings;
    private final java.util.function.Predicate<Path> installed;
    @org.springframework.beans.factory.annotation.Autowired
    public ExternalScanners(ObjectMapper json, RuntimeSettings settings) { this(json,settings,Files::isExecutable); }
    ExternalScanners(ObjectMapper json, RuntimeSettings settings, java.util.function.Predicate<Path> installed) { this.json=json;this.settings=settings;this.installed=installed; }
    public Batch analyze(Path source, Set<String> languages, long deadline, Path workspace) throws java.io.IOException {
        BoundedFindings findings=new BoundedFindings(settings.maxFindings); List<Coverage> coverage=new ArrayList<>();
        Map<Path,List<String>> snippets=new LinkedHashMap<>(4,0.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<Path,List<String>> entry){return size()>3;}};
        Path state=Files.createDirectory(workspace.resolve("tool-state"));
        if(languages.contains("Swift"))run("SwiftLint","Swift","QUALITY",List.of("/opt/swiftlint/ld-linux-x86-64.so.2","--library-path","/opt/swiftlint","/opt/swiftlint/swiftlint","lint","--config","/etc/repoguard/scanners/swiftlint.yaml","--no-cache","--quiet","--reporter","json",source.toString()),state,deadline,coverage,output -> {
            for(JsonNode f:json.readTree(output))add(findings,source,snippets,f.path("file").asText(),f.path("line").asInt(),f.path("rule_id").asText(),"QUALITY","MEDIUM","SwiftLint");
        });
        run("Semgrep",null,"Managed AST security rules",List.of("/opt/repoguard/bin/semgrep","scan","--config","/etc/repoguard/scanners/semgrep.yaml","--metrics=off","--disable-version-check","--no-git-ignore","--x-ignore-semgrepignore-files","--disable-nosem","--json","--quiet","--jobs=1","--timeout=30","--max-target-bytes="+settings.maxTextBytes,source.toString()),state,deadline,coverage,output -> {
            JsonNode report=json.readTree(output);
            for(JsonNode f:report.path("results"))add(findings,source,snippets,f.path("path").asText(),f.path("start").path("line").asInt(),f.path("check_id").asText(),"SECURITY","HIGH","Semgrep");
            if(report.path("errors").size()>0) {
                int diagnosticCount=0;
                for(JsonNode error:report.path("errors")) {
                    if(++diagnosticCount>50)break;
                    JsonNode type=error.path("type"), location=type.isArray()?type.path(1).path(0):error.path("spans").path(0);
                    String kind=(type.isArray()?type.path(0).asText("Unknown"):type.asText("Unknown")).replaceAll("[^A-Za-z0-9_.-]","_");
                    if(kind.isBlank())kind="Unknown";
                    if(kind.length()>80)kind=kind.substring(0,80);
                    String path=confinedPath(source,error.path("path").asText(location.path("path").asText()));
                    coverage.add(new Coverage(null,"Semgrep","Input diagnostic","FAILED",kind+"; file="+path+"; line="+location.path("start").path("line").asInt()+". Raw messages/source are not stored.",null));
                }
                throw new AnalysisException("SEMGREP_PARTIAL",report.path("errors").size()+" input/rule diagnostics; parsed findings retained. See input diagnostic rows.");
            }
            if(report.path("paths").path("scanned").isArray() && report.path("paths").path("scanned").isEmpty())throw new AnalysisException("SEMGREP_NO_FILES","No input files were analyzed.");
        });
        if(languages.contains("Rust")) {
            Map<String,List<Path>> editions=new TreeMap<>();
            try(var files=Files.walk(source)) {
                for(Path file:files.filter(f -> f.toString().endsWith(".rs")).sorted().toList()) {
                    try{editions.computeIfAbsent(RustEdition.of(file,source),ignored -> new ArrayList<>()).add(file);}
                    catch(Exception metadata){coverage.add(new Coverage("Rust","rustfmt","Edition metadata","FAILED","Edition could not be determined safely for "+confinedPath(source,file.toString()),null));}
                }
            }
            for(var group:editions.entrySet())for(int offset=0;offset<group.getValue().size();offset+=64) {
                List<String> command=new ArrayList<>(List.of("/usr/bin/rustfmt","--check","--config-path","/etc/repoguard/scanners/rustfmt.toml","--edition",group.getKey()));
                group.getValue().subList(offset,Math.min(offset+64,group.getValue().size())).forEach(f -> command.add(f.toString()));
                run("rustfmt","Rust","QUALITY (edition "+group.getKey()+", batch "+(offset/64+1)+")",command,state,deadline,coverage,output -> {
                    if(rustParseFailed(output))throw new AnalysisException("RUST_PARSE_FAILED","Rust parser rejected input with edition "+group.getKey()+". No Cargo/build or project rustfmt configuration was invoked.");
                    var match=java.util.regex.Pattern.compile("Diff in ([^\\n]+?):([0-9]+):").matcher(output);
                    while(match.find())add(findings,source,snippets,match.group(1),Integer.parseInt(match.group(2)),"format","QUALITY","LOW","rustfmt");
                });
            }
        }
        if(languages.contains("JavaScript/TypeScript")) {
          List<Path> inputs;
          try(var files=Files.walk(source)){inputs=files.filter(Files::isRegularFile).filter(f->f.toString().toLowerCase(Locale.ROOT).matches(".*\\.(js|jsx|ts|tsx|mjs|cjs)$")).sorted().toList();}
          int batchIndex=0;
          for(List<Path> batch:inputBatches(inputs,16,262144)) {
            List<String> command=new ArrayList<>(List.of("/usr/local/bin/node","--max-old-space-size=1536","/opt/eslint/node_modules/eslint/bin/eslint.js","--no-config-lookup","--config","/etc/repoguard/scanners/eslint.config.mjs","--no-ignore","--no-inline-config","--no-error-on-unmatched-pattern","--format","json"));
            batch.forEach(file->command.add(file.toString()));
            run("ESLint","JavaScript/TypeScript","SECURITY / QUALITY batch "+(++batchIndex),command,source,deadline,coverage,output -> {
            JsonNode files=json.readTree(output);
            if(!files.isArray() || files.isEmpty())throw new AnalysisException("ESLINT_NO_FILES","No input files were analyzed.");
            int failedInputs=0;
            for(JsonNode file:files) for(JsonNode error:file.path("messages")) {
                if(error.path("fatal").asBoolean() || error.path("message").asText().startsWith("File ignored")) {
                    failedInputs++;
                    coverage.add(new Coverage("JavaScript/TypeScript","ESLint","Input parsing","FAILED","Managed parser did not analyze "+confinedPath(source,file.path("filePath").asText())+":"+error.path("line").asInt()+". Raw messages/source are not stored.",null));
                }
                String rule=error.path("ruleId").asText("parse-error");
                boolean security=Set.of("no-eval","no-implied-eval","no-new-func","no-script-url").contains(rule);
                add(findings,source,snippets,file.path("filePath").asText(),error.path("line").asInt(),rule,security?"SECURITY":"QUALITY",security?"HIGH":"LOW","ESLint");
            }
            if(failedInputs>0)throw new AnalysisException("ESLINT_INPUT_FAILURE",failedInputs+" inputs could not be fully analyzed; parsed findings retained.");
            });
          }
        }
        if(languages.contains("Kotlin")) run("detekt","Kotlin","QUALITY",List.of("/opt/java/openjdk/bin/java","-jar","/opt/detekt/cli.jar","--input",source.toString(),"--base-path",source.toString(),"--config","/etc/repoguard/scanners/detekt.yaml","--build-upon-default-config","--report","sarif:"+state.resolve("detekt.sarif")),state,deadline,coverage,output -> {
            Path report=state.resolve("detekt.sarif");if(Files.size(report)>16*1024*1024)throw new java.io.IOException("Report too large");
            for(JsonNode scan:json.readTree(Files.readString(report)).path("runs")) for(JsonNode result:scan.path("results")) for(JsonNode location:result.path("locations")) {
                JsonNode physical=location.path("physicalLocation");String uri=physical.path("artifactLocation").path("uri").asText();
                String path=uri.startsWith("file:")?Path.of(java.net.URI.create(uri)).toString():java.net.URI.create(uri).getPath();
                add(findings,source,snippets,path,physical.path("region").path("startLine").asInt(),result.path("ruleId").asText(),"QUALITY","LOW","detekt");
            }
        });
        if(languages.contains("Java")) run("Checkstyle","Java","QUALITY",List.of("/opt/java/openjdk/bin/java","-cp","/opt/checkstyle/lib/*","com.puppycrawl.tools.checkstyle.Main","-c","/etc/repoguard/scanners/checkstyle.xml","-f","xml",source.toString()),state,deadline,coverage,output -> {
            var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            int start=output.indexOf("<?xml");if(start<0)start=output.indexOf("<checkstyle");
            var files=factory.newDocumentBuilder().parse(new ByteArrayInputStream(output.substring(start).getBytes(StandardCharsets.UTF_8))).getElementsByTagName("file");
            for(int i=0;i<files.getLength();i++) {var file=(org.w3c.dom.Element)files.item(i);var errors=file.getElementsByTagName("error");for(int j=0;j<errors.getLength();j++){var error=(org.w3c.dom.Element)errors.item(j);add(findings,source,snippets,file.getAttribute("name"),Integer.parseInt(error.getAttribute("line")),error.getAttribute("source"),"QUALITY","LOW","Checkstyle");}}
        });
        if(languages.contains("Go")) run("gofmt","Go","QUALITY",List.of("/usr/bin/gofmt","-l",source.toString()),state,deadline,coverage,output -> {
            for(String file:output.lines().toList())if(!file.isBlank())add(findings,source,snippets,file,0,"format","QUALITY","LOW","gofmt");
        });
        if(languages.contains("PHP")) run("PHP_CodeSniffer","PHP","QUALITY",List.of("/opt/phpcs/vendor/bin/phpcs","--standard=/etc/repoguard/scanners/phpcs.xml","--ignore-annotations","--report=json",source.toString()),state,deadline,coverage,output -> {
            var files=json.readTree(output).path("files").fields();while(files.hasNext()){var file=files.next();for(JsonNode error:file.getValue().path("messages"))add(findings,source,snippets,file.getKey(),error.path("line").asInt(),error.path("source").asText(),"QUALITY","LOW","PHP_CodeSniffer");}
        });
        if(languages.contains("Python")) {
            run("Bandit", "Python", "SECURITY", List.of("/opt/repoguard/bin/bandit","-r",source.toString(),"-f","json","-q","--ignore-nosec","--ini","/etc/repoguard/scanners/bandit.ini","-c","/etc/repoguard/scanners/bandit.yaml"),state,deadline,coverage,output -> {
                for(JsonNode f:json.readTree(output).path("results")) add(findings,source,snippets,f.path("filename").asText(),f.path("line_number").asInt(),f.path("test_id").asText(),"SECURITY",f.path("issue_severity").asText(),"Bandit");
                if(json.readTree(output).path("errors").size()>0)throw new AnalysisException("BANDIT_PARTIAL","Some Python inputs could not be analyzed.");
            });
            run("Ruff", "Python", "QUALITY", List.of("/opt/repoguard/bin/ruff","check","--isolated","--no-cache","--no-respect-gitignore","--output-format","concise","--select","ALL",source.toString()),state,deadline,coverage,output -> {
                var diagnostics=java.util.regex.Pattern.compile("(?m)^(.+?):([0-9]+):([0-9]+):\\s+([A-Z][A-Z0-9]*|invalid-syntax)\\b").matcher(output);
                int count=0;
                while(diagnostics.find()){count++;add(findings,source,snippets,diagnostics.group(1),Integer.parseInt(diagnostics.group(2)),diagnostics.group(4),"QUALITY","LOW","Ruff");}
                if(count==0 && !output.contains("All checks passed"))throw new AnalysisException("RUFF_OUTPUT_UNKNOWN","Ruff output was not a recognized concise diagnostic report.");
            });
        }
        if(languages.contains("C/C++")) {
            Path inputList=state.resolve("cppcheck-inputs.txt");
            try(var files=Files.walk(source)) {
                Files.write(inputList,files.filter(Files::isRegularFile).filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(c|cc|cpp|cxx|c\\+\\+|h|hh|hpp|hxx)$")).sorted().map(Path::toString).toList());
            }
            run("Cppcheck","C/C++","SECURITY / QUALITY",List.of("/usr/bin/cppcheck","--quiet","--xml","--xml-version=2","--enable=warning,style,performance,portability","--max-configs=1","--file-list="+inputList),state,deadline,coverage,output -> {
            var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            var errors=factory.newDocumentBuilder().parse(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8))).getElementsByTagName("error");
            for(int i=0;i<errors.getLength();i++) {
                var error=(org.w3c.dom.Element)errors.item(i);var locations=error.getElementsByTagName("location");if(locations.getLength()==0)continue;
                var location=(org.w3c.dom.Element)locations.item(0);String severity=error.getAttribute("severity");
                int line;try{line=Integer.parseInt(location.getAttribute("line"));}catch(NumberFormatException invalid){line=0;}
                add(findings,source,snippets,location.getAttribute("file"),line,error.getAttribute("id"),severity.equals("error")?"SECURITY":"QUALITY",severity.equals("error")?"HIGH":"LOW","Cppcheck");
            }
            });
        }
        Path database=null;
        try {
            String snapshot=Files.readString(settings.signatureDir.resolve("current")).trim();
            if(snapshot.matches("[a-f0-9-]{36}")) database=settings.signatureDir.resolve(snapshot).resolve("trivy");
        }catch(java.io.IOException missing){ }
        if(database==null || !Files.isRegularFile(database.resolve("db/trivy.db"))) {
            coverage.add(new Coverage(null,"Trivy","Dependency CVEs / secrets","SKIPPED","No local vulnerability snapshot. Run the manual security data update. No database is downloaded during analysis.",null));
        } else {
            database=privateDatabaseCopy(database);
            Path cache=Files.createDirectory(state.resolve("trivy"));
            Files.createSymbolicLink(cache.resolve("db"),database.resolve("db").toAbsolutePath());
            if(Files.isDirectory(database.resolve("java-db"))) Files.createSymbolicLink(cache.resolve("java-db"),database.resolve("java-db").toAbsolutePath());
            run("Trivy",null,"Dependency CVEs / secrets",List.of("/usr/local/bin/trivy","--cache-dir",cache.toString(),"fs","--config","/etc/repoguard/scanners/trivy.yaml","--ignorefile","/etc/repoguard/scanners/empty.ignore","--offline-scan","--skip-db-update","--skip-java-db-update","--scanners","vuln,secret","--format","json","--quiet",source.toString()),state,deadline,coverage,output -> {
                for(JsonNode result:json.readTree(output).path("Results")) {
                    for(JsonNode f:result.path("Vulnerabilities")) {
                        String target=result.path("Target").asText();
                        if(!Files.isRegularFile(source.resolve(target)))target=f.path("PkgPath").asText(target);
                        add(findings,source,snippets,target,0,f.path("VulnerabilityID").asText(),"DEPENDENCY",f.path("Severity").asText(),"Trivy");
                    }
                    for(JsonNode f:result.path("Secrets")) add(findings,source,snippets,result.path("Target").asText(),f.path("StartLine").asInt(),f.path("RuleID").asText(),"SECRET",f.path("Severity").asText(),"Trivy");
                }
            });
            coverage.add(new Coverage(null,"Trivy","Dependency coverage limits","UNSUPPORTED","Offline manifest/lockfile and existing archive analysis only. No dependency installation, build, remote resolution or executable repository configuration. These general exclusions are outside the requested static scope, not evidence that an applicable check ran.",null,false));
        }
        if(findings.truncated())coverage.add(new Coverage(null,"RepoGuard","Scanner result limit","SKIPPED","Managed scanner results exceeded the finding limit; highest severity results were retained.",null));
        Map<String,String> versions=new HashMap<>();for(Coverage check:coverage)if(check.version()!=null)versions.put(check.tool(),check.version());
        var normalized=findings.values().stream().map(f -> new Finding(f.id(),f.category(),f.severity(),f.ruleId(),f.title(),f.explanation(),f.remediation(),f.tool(),versions.getOrDefault(f.tool(),"unknown"),f.language(),f.path(),f.line(),f.fingerprint(),f.snippet())).toList();
        return new Batch(normalized,coverage);
    }
    private Path privateDatabaseCopy(Path snapshot) throws java.io.IOException {
        Path root=settings.scannerCache.toAbsolutePath().normalize();Files.createDirectories(root);
        String version=snapshot.getParent().getFileName().toString();
        if(!version.matches("[a-f0-9-]{36}"))throw new java.io.IOException("Invalid snapshot identifier");
        Path active=root.resolve(version),staging=root.resolve(version+".new");
        if(Files.isDirectory(active))return active;
        Workspace.delete(root,staging);Files.createDirectory(staging);
        for(String directory:List.of("db","java-db")) {
            Path source=snapshot.resolve(directory);if(!Files.isDirectory(source))continue;
            Path destination=Files.createDirectory(staging.resolve(directory));
            try(var files=Files.list(source)) {
                for(Path file:files.toList()) {
                    if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new java.io.IOException("Invalid database snapshot entry");
                    Files.copy(file,destination.resolve(file.getFileName()));
                }
            }
        }
        Files.move(staging,active,StandardCopyOption.ATOMIC_MOVE);
        try(var versions=Files.list(root)) {
            for(Path old:versions.toList())if(!old.equals(active) && old.getFileName().toString().matches("[a-f0-9-]{36}(\\.new)?"))Workspace.delete(root,old);
        }
        return active;
    }
    @FunctionalInterface private interface Parse { void parse(String output) throws Exception; }
    static List<List<Path>> inputBatches(List<Path> files,int maxFiles,long maxBytes) throws java.io.IOException {
        List<List<Path>> batches=new ArrayList<>();List<Path> batch=new ArrayList<>();long bytes=0;
        for(Path file:files){long size=Files.size(file);if(!batch.isEmpty() && (batch.size()>=maxFiles || size>maxBytes-bytes)){batches.add(List.copyOf(batch));batch.clear();bytes=0;}batch.add(file);bytes+=size;}
        if(!batch.isEmpty())batches.add(List.copyOf(batch));return batches;
    }
    static boolean rustParseFailed(String output) {
        return java.util.regex.Pattern.compile("(?m)^error(?:\\[E[0-9]+\\])?:").matcher(output).find();
    }
    private void run(String tool,String language,String check,List<String> command,Path state,long deadline,List<Coverage> coverage,Parse parse) {
        if(!installed.test(Path.of(command.getFirst()))) {coverage.add(new Coverage(language,tool,check,"UNSUPPORTED","Managed tool is not installed.",null));return;}
        long remaining=(deadline-System.nanoTime())/1_000_000;
        if(remaining<1000){coverage.add(new Coverage(language,tool,check,"SKIPPED","Job time budget exhausted.",null));return;}
        String version=null;
        try {
            List<String> versionCommand=tool.equals("gofmt")?List.of("/usr/bin/go","version"):tool.equals("ESLint")?List.of("/usr/local/bin/node","/opt/eslint/node_modules/eslint/bin/eslint.js","--version"):tool.equals("detekt")?List.of(command.get(0),"-jar",command.get(2),"--version"):List.of(command.getFirst(),"--version");
            if(tool.equals("SwiftLint")){versionCommand=new ArrayList<>(command.subList(0,4));versionCommand.add("--version");}
            if(tool.equals("Checkstyle")){versionCommand=new ArrayList<>(command.subList(0,4));versionCommand.add("--version");}
            var v=TrustedProcess.run(versionCommand,state,Duration.ofSeconds(5));
            version=v.text().lines().filter(line -> !line.startsWith("Picked up JAVA_TOOL_OPTIONS")).findFirst().orElse(tool);if(version.length()>128)version=version.substring(0,128);
            var output=TrustedProcess.run(command,state,Duration.ofMillis(Math.min(tool.equals("Semgrep")?180000:90000,remaining)),16*1024*1024);
            if(output.exit()!=0 && !(output.exit()==1 && Set.of("Bandit","Ruff","ESLint","rustfmt").contains(tool))
                    && !(tool.equals("detekt") && output.exit()==2)
                    && !(tool.equals("SwiftLint") && output.exit()==2)
                    && !(tool.equals("Checkstyle") && output.exit()>0 && output.exit()<255)
                    && !(tool.equals("PHP_CodeSniffer") && output.exit()<=3)) throw new AnalysisException("SCANNER_FAILED","Managed scanner exit="+output.exit()+"; "+diagnostic(tool,output));
            parse.parse(tool.equals("Cppcheck")?output.stderr():tool.equals("rustfmt")?output.text()+"\n"+output.stderr():output.text());
            coverage.add(new Coverage(language,tool,check,"COMPLETED","Managed configuration; static, offline invocation. Repository plugins and configuration were not loaded.",version));
        } catch(Exception failure){coverage.add(new Coverage(language,tool,check,"FAILED",failure instanceof AnalysisException a?a.code()+": "+a.getMessage():"Scanner output could not be parsed safely.",version));}
    }
    private String confinedPath(Path source,String raw) {
        try{Path path=Path.of(raw);if(!path.isAbsolute())path=source.resolve(path);path=path.normalize();return path.startsWith(source)?SecretMasker.safePath(source.relativize(path).toString().replace('\\','/')):"outside-source";}catch(Exception invalid){return "unavailable";}
    }
    private String diagnostic(String tool,TrustedProcess.Output output) {
        if(output.stderr().contains("heap out of memory") || output.exit()==137)return "resource/memory limit";
        if(tool.equals("Cppcheck")) {
            if(output.text().contains("no C or C++ source files") || output.stderr().contains("no C or C++ source files"))return "no supported inputs";
            var ids=java.util.regex.Pattern.compile("<error[^>]+id=\"([A-Za-z0-9_-]{1,80})\"").matcher(output.stderr());
            Set<String> types=new TreeSet<>();while(ids.find() && types.size()<10)types.add(ids.group(1));
            if(!types.isEmpty())return "XML diagnostic IDs="+String.join(",",types);
        }
        return "no raw diagnostics persisted";
    }
    private void add(BoundedFindings output,Path source,Map<Path,List<String>> snippets,String raw,int line,String rule,String category,String severity,String tool) throws java.io.IOException {
        Path path;
        try {path=Path.of(raw);if(!path.isAbsolute())path=source.resolve(path);path=path.normalize();}catch(Exception invalid){return;}
        if(!path.startsWith(source) || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))return;
        String relative=source.relativize(path).toString().replace('\\','/');
        rule=rule.replaceAll("[^A-Za-z0-9_.:/-]","_");if(rule.length()>128)rule=rule.substring(0,128);
        if(!Set.of("CRITICAL","HIGH","MEDIUM","LOW","INFO").contains(severity))severity="UNKNOWN";
        Snippet snippet=null;
        if(line>0 && Files.size(path)<=settings.maxTextBytes){
            List<String> lines;
            lines=snippets.get(path);if(lines==null){try{lines=Files.readAllLines(path);}catch(Exception invalid){lines=List.of();}snippets.put(path,lines);}
            if(line<=lines.size()) {
                int start=Math.max(0,line-1-settings.snippetLines/2),end=Math.min(lines.size(),start+settings.snippetLines);StringBuilder text=new StringBuilder();
                for(int i=start;i<end;i++){if(!text.isEmpty())text.append('\n');String masked=SecretMasker.mask(lines.get(i).substring(0,Math.min(2048,lines.get(i).length())));text.append(masked.substring(0,Math.min(masked.length(),settings.snippetLineLength)));}
                snippet=new Snippet(start+1,end,text.toString(),true);
            }
        }
        String fingerprint=SourceRuleEngine.hash(tool+"\n"+rule+"\n"+relative+"\n"+line);
        output.add(new Finding(UUID.randomUUID().toString(),category,severity,rule,tool+": "+rule,
                "The managed scanner reported this rule. Raw tool messages and matched secret values are deliberately not stored.",
                category.equals("DEPENDENCY")?"Review the advisory and upgrade the affected package to a fixed maintained version.":"Review this source location against the tool's documented rule; confirm applicability and remediate.",tool,"See scanner coverage",com.repoguard.model.LanguageGroup.fromPath(relative).orElse(com.repoguard.model.LanguageGroup.UNKNOWN).displayName(),SecretMasker.safePath(relative),line,fingerprint,snippet));
    }
}
