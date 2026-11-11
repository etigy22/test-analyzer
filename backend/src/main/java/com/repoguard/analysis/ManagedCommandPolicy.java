package com.repoguard.analysis;

import java.nio.file.Path;
import java.util.*;

/** A single application-owned executable/profile allowlist. Repository data cannot select a program or plugin. */
final class ManagedCommandPolicy {
    private ManagedCommandPolicy() { }
    private static final Set<String> BINARIES = Set.of(
            "/opt/repoguard/bin/semgrep", "/opt/repoguard/bin/bandit", "/opt/repoguard/bin/ruff",
            "/usr/bin/rustfmt", "/usr/bin/gofmt", "/usr/bin/go", "/usr/bin/cppcheck",
            "/opt/phpcs/vendor/bin/phpcs", "/usr/local/bin/trivy", "/usr/bin/clamscan", "/usr/bin/freshclam");
    static void validate(List<String> command, Map<String,String> environment) {
        if(command==null || command.isEmpty() || command.size()>30000
                || command.stream().anyMatch(s -> s==null || s.indexOf('\0')>=0 || s.length()>131072))deny();
        String executable=command.getFirst();
        if(!Path.of(executable).isAbsolute() || !Path.of(executable).normalize().toString().equals(executable))deny();
        String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        if(executable.equals(java) || executable.equals("/opt/java/openjdk/bin/java")) {
            if(!command.equals(List.of(executable,"--version"))
                    && !prefix(command,List.of(executable,"-jar","/opt/detekt/cli.jar"))
                    && !prefix(command,List.of(executable,"-cp","/opt/checkstyle/lib/*","com.puppycrawl.tools.checkstyle.Main")))deny();
        } else if(executable.equals("/usr/local/bin/node")) {
            if(!prefix(command,List.of(executable,"/opt/eslint/node_modules/eslint/bin/eslint.js"))
                    && !prefix(command,List.of(executable,"--max-old-space-size=1536","/opt/eslint/node_modules/eslint/bin/eslint.js")))deny();
        } else if(executable.equals("/opt/swiftlint/ld-linux-x86-64.so.2")) {
            if(!prefix(command,List.of(executable,"--library-path","/opt/swiftlint","/opt/swiftlint/swiftlint")))deny();
        } else if(!BINARIES.contains(executable))deny();
        if(executable.equals("/usr/bin/go") && !command.equals(List.of(executable,"version")))deny();
        Map<String,String> configs=Map.ofEntries(
                Map.entry("-c","/etc/repoguard/scanners/"),Map.entry("--config","/etc/repoguard/scanners/"),Map.entry("--config-path","/etc/repoguard/scanners/"),
                Map.entry("--ini","/etc/repoguard/scanners/bandit.ini"),Map.entry("--standard","/etc/repoguard/scanners/phpcs.xml"),
                Map.entry("--config-file","/etc/repoguard/freshclam.conf"),Map.entry("--ignorefile","/etc/repoguard/scanners/empty.ignore"));
        for(int i=1;i<command.size();i++) {
            String arg=command.get(i);
            if(arg.matches("--(?:plugins?|rulesdir|require|load)(?:=.*)?"))deny();
            for(var config:configs.entrySet()) {
                String value=arg.equals(config.getKey())?(i+1<command.size()?command.get(i+1):"")
                        :arg.startsWith(config.getKey()+"=")?arg.substring(config.getKey().length()+1):null;
                if(value!=null && (!(config.getValue().endsWith("/") ? value.startsWith(config.getValue()) : value.equals(config.getValue())) || value.contains("..")
                        || !Path.of(value).normalize().toString().equals(value)))deny();
            }
        }
        if(environment==null || !environment.keySet().stream().allMatch("TMPDIR"::equals))deny();
        if(environment.containsKey("TMPDIR") && (environment.get("TMPDIR")==null
                || !Path.of(environment.get("TMPDIR")).isAbsolute()))deny();
    }
    private static boolean prefix(List<String> actual,List<String> prefix) {
        return actual.size()>=prefix.size() && actual.subList(0,prefix.size()).equals(prefix);
    }
    private static void deny() { throw new AnalysisException("UNTRUSTED_COMMAND","Only managed scanner executables and runtime profiles may be launched."); }
}
