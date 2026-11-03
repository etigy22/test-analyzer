package com.repoguard.analysis;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Reads only bounded scalar Cargo manifest metadata. Never invokes Cargo or project configuration. */
final class RustEdition {
    private static final Pattern VALUE=Pattern.compile("^edition\\s*=\\s*[\"'](2015|2018|2021|2024)[\"']\\s*(?:#.*)?$");
    private RustEdition() { }
    static String of(Path file,Path root) throws IOException {
        root=root.toAbsolutePath().normalize();
        for(Path directory=file.toAbsolutePath().normalize().getParent();directory!=null && directory.startsWith(root);directory=directory.getParent()) {
            Path manifest=directory.resolve("Cargo.toml");
            if(!Files.isRegularFile(manifest,LinkOption.NOFOLLOW_LINKS))continue;
            Map<String,String> data=read(manifest);
            if(data.containsKey("package"))return data.get("package");
            if(data.containsKey("inherit")) {
                for(Path workspace=directory;workspace!=null && workspace.startsWith(root);workspace=workspace.getParent()) {
                    Path candidate=workspace.resolve("Cargo.toml");
                    if(Files.isRegularFile(candidate,LinkOption.NOFOLLOW_LINKS)) {
                        var values=read(candidate);if(values.containsKey("workspace.package"))return values.get("workspace.package");
                    }
                }
                throw new AnalysisException("RUST_EDITION_UNKNOWN","Workspace edition could not be resolved from bounded static manifest metadata.");
            }
            if(data.containsKey("has-package"))return "2015";
        }
        return "2021";
    }
    private static Map<String,String> read(Path manifest) throws IOException {
        if(Files.size(manifest)>1048576)throw new AnalysisException("RUST_MANIFEST_LIMIT","Cargo manifest exceeds the static metadata limit.");
        Map<String,String> result=new HashMap<>();String section="";
        for(String raw:Files.readAllLines(manifest)) {
            String line=raw.trim();
            if(line.startsWith("[")){section=line.replaceAll("[\\[\\]]","").trim();if(section.equals("package"))result.put("has-package","true");continue;}
            if(!Set.of("package","workspace.package").contains(section))continue;
            var value=VALUE.matcher(line);
            if(value.matches())result.put(section,value.group(1));
            else if(section.equals("package") && line.matches("edition(?:\\.workspace\\s*=\\s*true|\\s*=\\s*\\{\\s*workspace\\s*=\\s*true\\s*})\\s*(?:#.*)?"))result.put("inherit","true");
            else if(line.matches("edition\\s*=.*"))throw new AnalysisException("RUST_EDITION_UNKNOWN","Manifest edition is not a supported explicit scalar.");
        }
        return result;
    }
}
