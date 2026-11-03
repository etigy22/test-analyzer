package com.repoguard.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repoguard.persistence.ScanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BulkDeletionTest {
    @Test void refusesEntireSelectionIfAnyJobIsRunning() {
        var jdbc=mock(JdbcTemplate.class);var repository=new ScanRepository(jdbc,new ObjectMapper());
        Map<String,Object> running=new HashMap<>();running.put("completed_at",null);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("completed_at","done"),running));
        assertThatThrownBy(()->repository.deleteAll(List.of(UUID.randomUUID(),UUID.randomUUID()))).isInstanceOfSatisfying(AnalysisException.class,e->assertThat(e.code()).isEqualTo("BUSY"));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void deduplicatesIdsAndDeletesTerminalJobsInOneStatement() {
        var jdbc=mock(JdbcTemplate.class);var repository=new ScanRepository(jdbc,new ObjectMapper());
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("completed_at","done"),Map.of("completed_at","done")));
        repository.deleteAll(List.of(first,second,first));
        verify(jdbc).update(eq("delete from scan_jobs where id in (?,?)"),any(Object[].class));
    }
    @Test void missingIdsAndEmptySelectionsCannotPartiallyDelete() {
        var jdbc=mock(JdbcTemplate.class);var repository=new ScanRepository(jdbc,new ObjectMapper());
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());
        assertThatThrownBy(()->repository.deleteAll(List.of(UUID.randomUUID()))).isInstanceOf(AnalysisException.class);
        assertThatThrownBy(()->repository.deleteAll(List.of())).isInstanceOf(AnalysisException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
