package com.repoguard.analysis;

import java.util.regex.Pattern;

public final class SecretMasker {
    private SecretMasker() { }
    private static final Pattern LITERALS = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`[^`]*`");
    private static final Pattern ASSIGNMENT = Pattern.compile("(?i)(password|passwd|secret|token|api[_-]?key|private[_-]?key|authorization)\\s*[:=]");
    private static final Pattern LONG_TOKEN = Pattern.compile("[A-Za-z0-9_+/=.-]{24,}");
    public static String mask(String line) {
        if (line.contains("PRIVATE KEY") || ASSIGNMENT.matcher(line).find()) return "[REDACTED: sensitive line]";
        StringBuilder masked=new StringBuilder();
        char quote=0;boolean escaped=false;
        for(int i=0;i<line.length();i++) {
            char ch=line.charAt(i);
            if(quote!=0) {
                if(escaped){escaped=false;continue;}
                if(ch=='\\'){escaped=true;continue;}
                if(ch==quote)quote=0;
            } else if(ch=='\"' || ch=='\'' || ch=='`') {
                quote=ch;masked.append("\"[REDACTED]\"");
            } else masked.append(ch);
        }
        return LONG_TOKEN.matcher(masked.toString()).replaceAll("[REDACTED]");
    }
    public static String safePath(String path) {
        return path.replaceAll("(?:AKIA[A-Z0-9]{16}|gh[pousr]_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9_-]{24,})", "[REDACTED]");
    }
}
