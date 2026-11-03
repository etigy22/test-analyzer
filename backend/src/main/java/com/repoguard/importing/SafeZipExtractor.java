package com.repoguard.importing;

import com.repoguard.analysis.AnalysisException;
import com.repoguard.analysis.Workspace;
import com.repoguard.config.RepoGuardProperties;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.zip.CRC32;

@Component
public class SafeZipExtractor {
    private final RepoGuardProperties.ImportLimits limits;
    public SafeZipExtractor(RepoGuardProperties properties) { this.limits = properties.importLimits(); }

    public List<String> extract(Path archive, Path destination, long deadline) throws IOException {
        try (ZipFile zip = ZipFile.builder().setPath(archive).get()) {
            List<ZipArchiveEntry> entries = Collections.list(zip.getEntries());
            if (entries.isEmpty()) fail("ZIP_INVALID", "The archive is empty.");
            if (entries.size() > limits.maxFiles()) fail("ZIP_FILE_LIMIT", "The archive contains too many entries.");
            Map<String, Boolean> paths = new HashMap<>();
            Map<String, ZipArchiveEntry> byName = new HashMap<>();
            Map<String, String> links = new HashMap<>();
            long declared = 0;
            for (ZipArchiveEntry entry : entries) {
                Workspace.checkDeadline(deadline);
                if (entry.getRawName() != null) {
                    for (byte ch : entry.getRawName()) if (ch == '\\') fail("ZIP_PATH", "Backslash archive paths are prohibited.");
                }
                String name = validateName(entry.getName());
                int type = entry.getUnixMode() & 0170000;
                if (type != 0 && type != 0100000 && type != 0040000 && type != 0120000
                        || type == 0040000 && !entry.isDirectory() || type == 0100000 && entry.isDirectory())
                    fail("ZIP_SPECIAL_FILE", "Special archive entries are prohibited.");
                if (!zip.canReadEntryData(entry) || entry.getGeneralPurposeBit().usesEncryption())
                    fail("ZIP_INVALID", "Encrypted or unsupported ZIP entries are prohibited.");
                String key = name.toLowerCase(Locale.ROOT);
                if (paths.putIfAbsent(key, entry.isDirectory()) != null)
                    fail("ZIP_COLLISION", "The archive contains duplicate or colliding paths.");
                if (entry.getSize() < 0 || entry.getCompressedSize() < 0 || entry.getSize() > limits.maxSingleFileBytes())
                    fail("ZIP_SIZE_LIMIT", "An archive entry exceeds the file size limit.");
                if (entry.isDirectory() && entry.getSize() != 0) fail("ZIP_INVALID", "A ZIP directory contains data.");
                if (entry.getSize() > limits.maxExpandedBytes() - declared)
                    fail("ZIP_SIZE_LIMIT", "The expanded archive exceeds the total size limit.");
                declared += entry.getSize();
                if (entry.getSize() / Math.max(1.0, entry.getCompressedSize()) > limits.maxCompressionRatio())
                    fail("ZIP_RATIO_LIMIT", "The archive exceeds the compression ratio limit.");
                byName.put(name, entry);
                if (entry.isUnixSymlink()) {
                    if(entry.getSize()<1 || entry.getSize()>4096) fail("ZIP_SPECIAL_FILE","Invalid symbolic link payload.");
                    byte[] bytes;
                    try(var input=zip.getInputStream(entry)){bytes=input.readNBytes(4097);}
                    CRC32 crc=new CRC32();crc.update(bytes);
                    if(bytes.length!=entry.getSize() || crc.getValue()!=entry.getCrc())fail("ZIP_INVALID","Corrupt symbolic link metadata.");
                    String reference=java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                    if(reference.isBlank() || reference.startsWith("/") || reference.contains("\\") || reference.contains(":") || reference.codePoints().anyMatch(Character::isISOControl))
                        fail("ZIP_SPECIAL_FILE","Unsafe symbolic link target.");
                    Path parent=Path.of(name).getParent();
                    Path resolved=(parent==null?Path.of(reference):parent.resolve(reference)).normalize();
                    if(resolved.isAbsolute() || resolved.startsWith(".."))fail("ZIP_SPECIAL_FILE","Symbolic link leaves the archive.");
                    links.put(name,validateName(resolved.toString().replace('\\','/')));
                }
            }
            for (String name : paths.keySet()) {
                for (int slash = name.indexOf('/'); slash >= 0; slash = name.indexOf('/', slash + 1)) {
                    if (Boolean.FALSE.equals(paths.get(name.substring(0, slash))))
                        fail("ZIP_COLLISION", "An archive file collides with a directory.");
                }
            }
            Set<String> directories=new HashSet<>();directories.add("");
            Map<String, SortedSet<String>> children=new HashMap<>();
            for(var item:byName.entrySet()) {
                if(item.getValue().isDirectory())directories.add(item.getKey());
                String current=item.getKey();
                while(!current.isEmpty()) {
                    int slash=current.lastIndexOf('/');String parent=slash<0?"":current.substring(0,slash);
                    children.computeIfAbsent(parent,ignored -> new TreeSet<>()).add(current);
                    directories.add(parent);current=parent;
                }
            }
            ExtractionPlan plan=new ExtractionPlan();
            for(String child:children.getOrDefault("",Collections.emptySortedSet()))
                planNode(child,child,byName,links,directories,children,new HashSet<>(),plan,deadline);
            long total = 0;
            byte[] buffer = new byte[8192];
            for (Planned entry : plan.entries) {
                Workspace.checkDeadline(deadline);
                String name=entry.name();
                Path target = destination.resolve(name).normalize();
                if (!target.startsWith(destination)) fail("ZIP_PATH", "Archive path escapes the working directory.");
                if (entry.content()==null) { Files.createDirectories(target); continue; }
                ZipArchiveEntry content=entry.content();
                Files.createDirectories(target.getParent());
                long written = 0;
                CRC32 crc = new CRC32();
                try (var input = zip.getInputStream(content); var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        Workspace.checkDeadline(deadline);
                        if (count > limits.maxSingleFileBytes() - written || count > limits.maxExpandedBytes() - total)
                            fail("ZIP_SIZE_LIMIT", "Actual extracted bytes exceed the configured limit.");
                        written += count; total += count;
                        if (written / Math.max(1.0, content.getCompressedSize()) > limits.maxCompressionRatio())
                            fail("ZIP_RATIO_LIMIT", "Actual extraction exceeds the compression ratio limit.");
                        crc.update(buffer, 0, count); output.write(buffer, 0, count);
                    }
                }
                if (written != content.getSize() || crc.getValue() != content.getCrc())
                    fail("ZIP_INVALID", "The ZIP archive contains truncated or corrupt data.");
            }
            // Immutable input for the rule engine. The directory is never on an executable mount.
            if (Files.getFileStore(destination).supportsFileAttributeView("posix")) {
                try (var files = Files.walk(destination)) {
                    for (Path file : files.toList()) Files.setPosixFilePermissions(file,
                            java.nio.file.attribute.PosixFilePermissions.fromString(Files.isDirectory(file) ? "r-x------" : "r--------"));
                }
            }
            return links.keySet().stream().sorted().toList();
        } catch (AnalysisException failure) { throw failure; }
        catch (IOException | IllegalArgumentException failure) {
            throw new AnalysisException("ZIP_INVALID", "The ZIP archive cannot be safely extracted.");
        }
    }

    private record Planned(String name,ZipArchiveEntry content) { }
    private static final class ExtractionPlan {
        final List<Planned> entries=new ArrayList<>();
        final Set<String> paths=new HashSet<>();
        long bytes;
    }
    private void planNode(String original,String output,Map<String,ZipArchiveEntry> files,Map<String,String> links,
                          Set<String> directories,Map<String,SortedSet<String>> children,Set<String> active,
                          ExtractionPlan plan,long deadline) {
        Workspace.checkDeadline(deadline);
        validateName(output);
        if(links.containsKey(original)) {
            if(!active.add(original) || active.size()>limits.maxZipDepth())fail("ZIP_SPECIAL_FILE","Cyclic or excessively nested symbolic link.");
            try {planNode(links.get(original),output,files,links,directories,children,active,plan,deadline);}
            finally {active.remove(original);}
            return;
        }
        boolean directory=directories.contains(original);
        ZipArchiveEntry content=files.get(original);
        if(!directory && content==null)fail("ZIP_SPECIAL_FILE","Symbolic link target is missing from this archive.");
        if(plan.entries.size()>=limits.maxFiles())fail("ZIP_FILE_LIMIT","Materialized archive links exceed the entry limit.");
        if(!plan.paths.add(output.toLowerCase(Locale.ROOT)))fail("ZIP_COLLISION","Materialized archive paths collide.");
        if(!directory) {
            if(content.getSize()>limits.maxExpandedBytes()-plan.bytes)fail("ZIP_SIZE_LIMIT","Materialized links exceed the expanded size limit.");
            plan.bytes+=content.getSize();
        }
        plan.entries.add(new Planned(output,directory?null:content));
        if(directory)for(String child:children.getOrDefault(original,Collections.emptySortedSet())) {
            String basename=child.substring(child.lastIndexOf('/')+1);
            planNode(child,output+"/"+basename,files,links,directories,children,active,plan,deadline);
        }
    }

    private String validateName(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 512 || raw.startsWith("/") || raw.contains("\\"))
            fail("ZIP_PATH", "The archive contains an invalid path.");
        String name = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
        if (!Normalizer.isNormalized(name, Normalizer.Form.NFC)) fail("ZIP_PATH", "Archive paths must be Unicode normalized.");
        String[] components = name.split("/", -1);
        if (components.length > limits.maxZipDepth()) fail("ZIP_DEPTH_LIMIT", "The archive exceeds the path depth limit.");
        for (String part : components) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ")
                    || part.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?"))
                fail("ZIP_PATH", "The archive contains an unsafe path component.");
            for (int ch : part.codePoints().toArray()) {
                if (Character.isISOControl(ch) || Character.getType(ch) == Character.FORMAT || ":*?\"<>|".indexOf(ch) >= 0)
                    fail("ZIP_PATH", "The archive contains unsafe path characters.");
            }
        }
        return name;
    }
    private static void fail(String code, String message) { throw new AnalysisException(code, message); }
}
