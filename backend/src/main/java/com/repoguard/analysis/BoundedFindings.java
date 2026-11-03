package com.repoguard.analysis;

import java.util.*;
import static com.repoguard.analysis.AnalysisModels.Finding;

/** Retains the most actionable findings in bounded memory, independent of input traversal order. */
final class BoundedFindings {
    private static final Comparator<Finding> ORDER=Comparator.comparingInt(BoundedFindings::priority).thenComparing(Finding::fingerprint);
    private final int limit;
    private final PriorityQueue<Finding> queue=new PriorityQueue<>(ORDER);
    private final Set<String> fingerprints=new HashSet<>();
    private boolean truncated;
    BoundedFindings(int limit){this.limit=Math.max(1,limit);}
    void add(Finding finding) {
        if(fingerprints.contains(finding.fingerprint()))return;
        if(queue.size()>=limit) {
            truncated=true;
            if(ORDER.compare(finding,queue.peek())<=0)return;
            fingerprints.remove(queue.remove().fingerprint());
        }
        queue.add(finding);fingerprints.add(finding.fingerprint());
    }
    int size(){return queue.size();}
    boolean truncated(){return truncated;}
    List<Finding> values(){return queue.stream().sorted(ORDER.reversed()).toList();}
    private static int priority(Finding finding){
        int severity=switch(finding.severity()){case "CRITICAL"->6;case "HIGH"->5;case "MEDIUM"->4;case "LOW"->3;case "INFO"->2;default->1;};
        int category=switch(finding.category()){case "DEPENDENCY","SECRET","SECURITY"->3;case "CONFIGURATION"->2;default->1;};
        return severity*100+category*10+(finding.tool().equals("RepoGuard")?0:1);
    }
}
