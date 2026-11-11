package com.repoguard.analysis;

import com.repoguard.model.LanguageGroup;
import java.util.*;
import java.util.regex.*;

/** Bounded lexical context for bundled heuristics, not an AST or a taint analysis.
 * Managed external AST scanners remain independent. No path-based exclusions. */
final class SourceContext {
    record Literal(int start, int end, String value, boolean interpolated) { }
    private static final Pattern SQL = Pattern.compile("(?is)^\\s*(?:select\\b.+?\\bfrom\\b|insert\\s+into\\b|update\\b.+?\\bset\\b|delete\\s+from\\b)");
    private static final Pattern KEY = Pattern.compile("(?i)(?:[\\w$]+[_-])?(?:password|passwd|secret|token|api[_-]?key)");
    private static final Pattern ASSIGN = Pattern.compile("([\\w$]+)\\s*[:=]\\s*$");
    private static final Pattern TOKEN = Pattern.compile("AKIA[A-Z0-9]{16}|gh[pousr]_[A-Za-z0-9]{20,}|BEGIN[^-\\r\\n]{0,30}PRIVATE KEY");
    private final long deadline;
    private final String text;
    private final char[] code;
    private final LanguageGroup language;
    private final List<Literal> literals = new ArrayList<>();
    private final List<int[]> comments = new ArrayList<>();
    private final Map<Integer,Integer> commentEnds = new HashMap<>();
    private final Map<Integer, Literal> byStart = new HashMap<>();
    private final Set<Integer> sqlLines = new HashSet<>(), secretLines = new HashSet<>(), embeddedSecretLines = new HashSet<>();
    private final int[] lineStarts;

    SourceContext(String text, LanguageGroup language, long deadline) {
        this.deadline=deadline; Workspace.checkDeadline(deadline);
        this.text = text; this.code = text.toCharArray(); this.language = language;
        List<Integer> starts = new ArrayList<>(); starts.add(0);
        for (int i=0;i<text.length();i++) {
            if((i & 4095)==0)Workspace.checkDeadline(deadline);
            char c=text.charAt(i);
            if(c=='\r') { if(i+1<text.length() && text.charAt(i+1)=='\n')i++; starts.add(i+1); }
            else if(c=='\n' || c=='\u000b' || c=='\f' || c=='\u0085' || c=='\u2028' || c=='\u2029')starts.add(i+1);
        }
        lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
        scan(0, false, 0);
        literals.sort(Comparator.comparingInt(Literal::start));
        for(Literal literal:literals)byStart.put(literal.start(),literal);
        for(int[] comment:comments)commentEnds.put(comment[0],comment[1]);
        detectSql(); detectSecrets();
    }
    String executableCode() { return new String(code); }
    int sourceLine(int offset) { return line(offset); }
    boolean sqlAt(int index) { return sqlLines.contains(index); }
    boolean secretAt(int index) { return secretLines.contains(index); }
    boolean embeddedSecretAt(int index) { return embeddedSecretLines.contains(index) && !secretLines.contains(index); }
    private int line(int offset) {
        int at=Arrays.binarySearch(lineStarts,offset); return at>=0?at:-at-2;
    }
    private void blank(int start,int end) {
        for(int j=start;j<end;j++)if(code[j]!='\n' && code[j]!='\r')code[j]=' ';
    }
    private boolean at(int i,String s) { return text.startsWith(s,i); }
    private int space(int i) {
        while(i<text.length()) {
            if((i & 4095)==0)Workspace.checkDeadline(deadline);
            if(Character.isWhitespace(text.charAt(i)))i++;
            else if(commentEnds.containsKey(i))i=commentEnds.get(i);
            else break;
        }
        return i;
    }
    private int scan(int from,boolean expression,int depth) {
        if(depth>64)throw new AnalysisException("SOURCE_NESTING_LIMIT","Source lexical nesting exceeds its limit.");
        int braces=0;
        for(int i=from;i<text.length();) {
            if((i & 4095)==0)Workspace.checkDeadline(deadline);
            char c=text.charAt(i);
            if(expression && c=='}' && braces==0)return i+1;
            if(c=='{')braces++; if(c=='}')braces--;
            if(at(i,"//") && language!=LanguageGroup.PYTHON || c=='#' && (language==LanguageGroup.PYTHON || language==LanguageGroup.PHP || language==LanguageGroup.UNKNOWN)) {
                int end=text.indexOf('\n',i);if(end<0)end=text.length();comments.add(new int[]{i,end});blank(i,end);i=end;continue;
            }
            if(at(i,"/*") && language!=LanguageGroup.PYTHON) {
                int end=text.indexOf("*/",i+2);end=end<0?text.length():end+2;comments.add(new int[]{i,end});blank(i,end);i=end;continue;
            }
            if(c=='/' && language==LanguageGroup.JAVASCRIPT_TYPESCRIPT) {
                String before=text.substring(Math.max(0,i-32),i).stripTrailing();
                if(before.isEmpty() || "(=:[,!{;".indexOf(before.charAt(before.length()-1))>=0 || before.matches("(?s).*\\breturn")) {
                    int end=i+1;boolean characterClass=false;
                    while(end<text.length() && text.charAt(end)!='\n') {
                        char r=text.charAt(end++);
                        if(r=='\\') {end=Math.min(text.length(),end+1);continue;}
                        if(r=='[')characterClass=true;if(r==']')characterClass=false;
                        if(r=='/' && !characterClass){blank(i,end);i=end;break;}
                    }
                    if(i==end)continue;
                }
            }
            // Rust lifetimes are identifiers, not character literals.
            if(c=='\'' && language==LanguageGroup.RUST && i+1<text.length() && Character.isJavaIdentifierStart(text.charAt(i+1)) && (i+2>=text.length() || text.charAt(i+2)!='\'')) {i++;continue;}
            if(c=='\'' || c=='"' || c=='`') {i=literal(i,depth);continue;}
            i++;
        }
        return text.length();
    }
    private int literal(int start,int depth) {
        char quote=text.charAt(start);
        boolean triple=at(start,String.valueOf(quote).repeat(3));
        int width=triple?3:1,content=start+width,i=content,segment=start;
        boolean dynamic=false;
        boolean pythonFormat=language==LanguageGroup.PYTHON && start>0 && "fF".indexOf(text.charAt(start-1))>=0;
        boolean csharpFormat=language==LanguageGroup.CSHARP && start>0 && (text.charAt(start-1)=='$' || start>1 && text.substring(start-2,start).contains("$@"));
        boolean verbatim=language==LanguageGroup.CSHARP && start>0 && text.charAt(start-1)=='@';
        while(i<text.length()) {
            if((i & 4095)==0)Workspace.checkDeadline(deadline);
            if(!verbatim && text.charAt(i)=='\\') {i=Math.min(text.length(),i+2);continue;}
            if(verbatim && at(i,"\"\"")) {i+=2;continue;}
            if(at(i,String.valueOf(quote).repeat(width))) {
                int end=i+width;blank(segment,end);
                literals.add(new Literal(start,end,text.substring(content,i),dynamic));return end;
            }
            boolean dollar=at(i,"${") && (quote=='`' && language==LanguageGroup.JAVASCRIPT_TYPESCRIPT || language==LanguageGroup.KOTLIN || language==LanguageGroup.PHP);
            boolean formatted=(pythonFormat || csharpFormat) && text.charAt(i)=='{';
            if(formatted && at(i,"{{")) {i+=2;continue;}
            if(dollar || formatted) {
                int next=i+(dollar?2:1);blank(segment,next);dynamic=true;
                i=scan(next,true,depth+1);segment=i;continue;
            }
            if(language==LanguageGroup.PHP && quote=='"' && text.charAt(i)=='$')dynamic=true;
            i++;
        }
        blank(segment,text.length());literals.add(new Literal(start,text.length(),text.substring(content),dynamic));return text.length();
    }
    private String codeBefore(int at,int max) { return new String(code,Math.max(0,at-max),Math.min(at,max)); }
    private void detectSecrets() {
        for(int literalIndex=0;literalIndex<literals.size();literalIndex++) {
            Workspace.checkDeadline(deadline);
            Literal literal=literals.get(literalIndex);
            Matcher assignment=ASSIGN.matcher(codeBefore(literal.start(),256));
            String key=assignment.find()?assignment.group(1):null;
            // Quoted JSON/object keys are themselves literals.
            if(key==null) {
                for(int i=literalIndex-1;i>=0;i--) {
                    Literal previous=literals.get(i);if(previous.end()>literal.start())continue;
                    String between=text.substring(previous.end(),literal.start()).trim();
                    if(between.equals(":"))key=previous.value();break;
                }
            }
            if(key!=null && KEY.matcher(key).matches() && literal.value().length()>=6 && !literal.interpolated()
                    && !literal.value().matches("\\$\\{[A-Z_][A-Z0-9_]*}")) {
                // An enum-to-display-label dictionary is data, not a credential assignment.
                // Require both the identity label and sibling category labels; passwords like "password" stay findings.
                String surrounding=new String(code,Math.max(0,literal.start()-2048),Math.min(code.length,literal.end()+2048)-Math.max(0,literal.start()-2048));
                boolean label=key.equals("SECRET") && literal.value().equalsIgnoreCase("Secrets")
                        && surrounding.matches("(?s).*\\bSECURITY\\s*:.*") && surrounding.matches("(?s).*\\bQUALITY\\s*:.*")
                        && surrounding.matches("(?s).*\\bDEPENDENCY\\s*:.*");
                if(!label)secretLines.add(line(literal.start()));
            }
        }
        Pattern commentedCredential=Pattern.compile("(?i)\\b(?:password|passwd|secret|token|api[_-]?key)\\s*[:=]\\s*['\"][^'\"\\r\\n]{6,}['\"]");
        for(int[] comment:comments) {
            Matcher credential=commentedCredential.matcher(text.substring(comment[0],comment[1]));
            while(credential.find())secretLines.add(line(comment[0]+credential.start()));
        }
        // Credential candidates in embedded JSON, examples or fixtures remain visible
        // as lower-confidence review findings, rather than being silently discarded.
        Pattern embeddedCredential=Pattern.compile("(?i)\\b(?:password|passwd|secret|token|api[_-]?key)['\"]?\\s*[:=]\\s*['\"][^'\"\\r\\n]{6,}['\"]");
        for(Literal literal:literals) {
            Workspace.checkDeadline(deadline);
            String decoded=literal.value().replace("\\n","\n").replace("\\r","\r").replace("\\t","\t").replace("\\\"","\"").replace("\\'","'");
            if(embeddedCredential.matcher(decoded).find())embeddedSecretLines.add(line(literal.start()));
        }
        // Provider tokens and private keys remain detectable in strings, comments and test data.
        Matcher token=TOKEN.matcher(text);while(token.find())secretLines.add(line(token.start()));
    }
    private Map<String,int[]> safePlaceholders() {
        Map<String,int[]> safe=new HashMap<>();
        if(language!=LanguageGroup.JAVA)return safe;
        Matcher m=Pattern.compile("\\b([A-Za-z_$][\\w$]*)\\s*=\\s*String\\.join\\(\\s*\",\"\\s*,\\s*Collections\\.nCopies\\(\\s*[\\w.]+\\(\\)\\s*,\\s*\"\\?\"\\s*\\)\\s*\\)\\s*;").matcher(text);
        while(m.find()) {
            Workspace.checkDeadline(deadline);
            if(Character.isWhitespace(code[m.start()]))continue;
            String name=m.group(1);
            Matcher writes=Pattern.compile("\\b"+Pattern.quote(name)+"\\s*(?:[+*/%-]?=(?!=)|\\+\\+|--)").matcher(new String(code));
            int count=0;while(writes.find())count++;
            Matcher declarations=Pattern.compile("\\b[A-Za-z_$][\\w$<>\\[\\].]*\\s+"+Pattern.quote(name)+"\\b").matcher(new String(code));
            int declared=0;while(declarations.find())declared++;
            int end=m.end(),depth=0;
            for(;end<code.length;end++){if(code[end]=='{')depth++;if(code[end]=='}'){if(depth==0)break;depth--;}}
            if(count==1 && declared==1)safe.put(name,new int[]{m.end(),end});
        }
        return safe;
    }
    private boolean dynamicTail(int end,Map<String,int[]> safe) {
        return dynamicTail(end,safe,0);
    }
    private boolean dynamicTail(int end,Map<String,int[]> safe,int depth) {
        if(depth>64)return true;
        int i=space(end);
        if(i>=text.length())return false;
        if(at(i,".format(") || text.charAt(i)=='%')return true;
        if(text.charAt(i)!='+' && !(language==LanguageGroup.PHP && text.charAt(i)=='.'))return false;
        i=space(i+1);
        Literal next=byStart.get(i);
        if(next!=null)return next.interpolated() || dynamicTail(next.end(),safe,depth+1);
        Matcher name=Pattern.compile("[A-Za-z_$][\\w$]*").matcher(text);name.region(i,text.length());
        if(name.lookingAt() && safe.containsKey(name.group()) && end>=safe.get(name.group())[0] && end<safe.get(name.group())[1]) {
            int after=space(name.end());
            if(after<text.length() && (text.charAt(after)=='.' || text.charAt(after)=='(' || text.charAt(after)=='['))return true;
            return dynamicTail(name.end(),safe,depth+1);
        }
        return true;
    }
    private void detectSql() {
        Map<String,int[]> safe=safePlaceholders();Map<String,Integer> sqlVariables=new HashMap<>();
        for(Literal literal:literals) {
            Workspace.checkDeadline(deadline);
            String before=codeBefore(literal.start(),256);
            boolean sqlContext=before.matches("(?is).*\\b(?:query|queryForList|execute|executeQuery|prepareStatement|rawQuery|format)\\s*\\(\\s*$")
                    || before.matches("(?is).*\\b(?:sql|query|statement)\\s*=\\s*$");
            if(!SQL.matcher(literal.value()).find() && !(sqlContext && literal.value().matches("(?is)^\\s*(?:select|update)\\s+.*")))continue;
            if(literal.interpolated() || dynamicTail(literal.end(),safe) || before.matches("(?s).*\\b(?:format|sprintf)\\s*\\(\\s*$") && literal.value().contains("%s"))sqlLines.add(line(literal.start()));
            Matcher assigned=ASSIGN.matcher(codeBefore(literal.start(),256));
            if(assigned.find())sqlVariables.put(assigned.group(1),literal.start());
        }
        String executable=new String(code);
        for(var entry:sqlVariables.entrySet()) {
            Workspace.checkDeadline(deadline);
            Matcher uses=Pattern.compile("\\b"+Pattern.quote(entry.getKey())+"\\s*(?:\\+=|\\+)").matcher(executable);
            while(uses.find())if(uses.start()>entry.getValue())sqlLines.add(line(uses.start()));
        }
    }
}
