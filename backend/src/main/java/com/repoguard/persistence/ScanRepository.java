package com.repoguard.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repoguard.analysis.AnalysisException;
import com.repoguard.analysis.AnalysisModels.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Repository
@ConditionalOnProperty(name = "repoguard.role", havingValue = "api", matchIfMissing = true)
public class ScanRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public ScanRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public UUID create(String type, String source) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into scan_jobs(id,source_type,sanitized_source,status,created_at,updated_at) values(?,?,?,'QUEUED',now(),now())", id, type, source);
        return id;
    }
    public void phase(UUID id, String phase) { jdbc.update("update scan_jobs set status=?,updated_at=now() where id=? and completed_at is null", phase, id); }
    public void fail(UUID id, String code, String message) {
        String state = code.startsWith("MALWARE") ? "BLOCKED" : "FAILED";
        jdbc.update("update scan_jobs set status=?,failure_code=?,failure_message=?,updated_at=now(),completed_at=now() where id=?", state, code, message, id);
    }
    @Transactional
    public void finish(UUID id, Result result) {
        try {
            jdbc.update("insert into scan_results(scan_id,result_json) values(?,?)", id, json.writeValueAsString(result));
            boolean limited = result.truncated() || result.coverage().stream().anyMatch(c -> !c.status().equals("COMPLETED"));
            jdbc.update("update scan_jobs set status=?,updated_at=now(),completed_at=now() where id=?", limited ? "PARTIAL" : "COMPLETED", id);
        } catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot encode analysis results.", ex); }
    }
    public Scan get(UUID id) {
        var rows = jdbc.query("select j.*,r.result_json from scan_jobs j left join scan_results r on r.scan_id=j.id where j.id=?", (rs, index) -> {
            Result result = null;
            String encoded = rs.getString("result_json");
            try { if (encoded != null) result = json.readValue(encoded, Result.class); }
            catch (JsonProcessingException ex) { throw new java.sql.SQLException("Stored analysis cannot be read."); }
            return new Scan(rs.getObject("id", UUID.class), rs.getString("source_type"), rs.getString("sanitized_source"), rs.getString("status"),
                    rs.getTimestamp("created_at").toInstant().toString(), rs.getTimestamp("updated_at").toInstant().toString(),
                    rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant().toString(),
                    rs.getString("failure_code"), rs.getString("failure_message"), result);
        }, id);
        if (rows.isEmpty()) throw new AnalysisException("NOT_FOUND", "Analysis not found.");
        return rows.getFirst();
    }
    public List<Scan> list() {
        return jdbc.query("select * from scan_jobs order by created_at desc limit 200", (rs, index) -> new Scan(rs.getObject("id", UUID.class),
                rs.getString("source_type"), rs.getString("sanitized_source"), rs.getString("status"), rs.getTimestamp("created_at").toInstant().toString(),
                rs.getTimestamp("updated_at").toInstant().toString(), rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant().toString(),
                rs.getString("failure_code"), rs.getString("failure_message"), null));
    }
    @Transactional
    public void delete(UUID id) {
        deleteAll(List.of(id));
    }
    @Transactional
    public void deleteAll(List<UUID> ids) {
        if(ids==null || ids.isEmpty() || ids.size()>200 || ids.stream().anyMatch(Objects::isNull))throw new AnalysisException("INVALID_REQUEST","Select between one and 200 analyses.");
        List<UUID> unique=ids.stream().distinct().sorted().toList();
        String placeholders=String.join(",",Collections.nCopies(unique.size(),"?"));
        var rows=jdbc.queryForList("select id,completed_at from scan_jobs where id in ("+placeholders+") order by id for update",unique.toArray());
        if(rows.size()!=unique.size())throw new AnalysisException("NOT_FOUND","At least one selected analysis no longer exists. Nothing was deleted.");
        if(rows.stream().anyMatch(row -> row.get("completed_at")==null))throw new AnalysisException("BUSY","Running analyses cannot be deleted. Nothing was deleted.");
        jdbc.update("delete from scan_jobs where id in ("+placeholders+")",unique.toArray());
    }
    public void recover(int retentionDays) {
        jdbc.update("update update_runs set status='FAILED',completed_at=now(),message='Data update interrupted by restart. Previous signed data remains active.' where status='RUNNING'");
        jdbc.update("update scan_jobs set status='FAILED', failure_code='RESTART_INTERRUPTED',failure_message='Analysis interrupted by restart; temporary files were removed.',updated_at=now(),completed_at=now() where completed_at is null");
        jdbc.update("delete from scan_jobs where completed_at < ?", java.sql.Timestamp.from(Instant.now().minusSeconds(retentionDays * 86400L)));
    }
    public UUID startUpdate() {
        UUID id = UUID.randomUUID(); jdbc.update("insert into update_runs(id,status,started_at,message) values(?,'RUNNING',now(),'Manual update started.')", id); return id;
    }
    public void finishUpdate(UUID id, boolean success, String message, String version) {
        jdbc.update("update update_runs set status=?,completed_at=now(),message=?,data_version=? where id=?", success ? "COMPLETED" : "FAILED", message, version, id);
    }
    public Map<String,Object> lastUpdate() {
        var rows = jdbc.queryForList("select id,status,started_at,completed_at,message,data_version from update_runs order by started_at desc limit 1");
        return rows.isEmpty() ? Map.of("status", "NEVER") : rows.getFirst();
    }
    public boolean ready() { return Boolean.TRUE.equals(jdbc.queryForObject("select true", Boolean.class)); }
}
