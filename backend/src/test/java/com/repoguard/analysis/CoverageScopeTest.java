package com.repoguard.analysis;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.repoguard.analysis.AnalysisModels.*;
class CoverageScopeTest {
    @Test void declaredExclusionsDoNotPretendToBeExecutedButDoNotFailAStaticRequest(){
        var scope=new Coverage(null,"RepoGuard","Build","UNSUPPORTED","Prohibited",null,false);
        assertThat(scope.status()).isEqualTo("UNSUPPORTED");assertThat(scope.incomplete()).isFalse();
    }
    @Test void failuresSkipsUnsupportedAndLegacyChecksRemainIncomplete(){
        for(String status:new String[]{"FAILED","SKIPPED","UNSUPPORTED"}){
            assertThat(new Coverage(null,"Tool","Actual check",status,"reason","v").incomplete()).isTrue();
            assertThat(new Coverage(null,"Tool","Legacy",status,"reason","v",null).incomplete()).isTrue();
        }
        for(String status:new String[]{"FAILED","SKIPPED"})assertThat(new Coverage(null,"Tool","Failure cannot be hidden",status,"reason","v",false).incomplete()).isTrue();
    }
}
