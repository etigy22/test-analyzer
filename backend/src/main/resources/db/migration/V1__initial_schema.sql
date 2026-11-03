create table scan_jobs (
    id uuid primary key,
    source_type varchar(32) not null,
    sanitized_source text not null,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    completed_at timestamptz,
    failure_code varchar(128),
    failure_message text
);

create table detected_languages (
    id uuid primary key,
    scan_id uuid not null references scan_jobs(id) on delete cascade,
    language varchar(64) not null,
    file_count integer not null,
    byte_count bigint not null
);

create table scanner_checks (
    id uuid primary key,
    scan_id uuid not null references scan_jobs(id) on delete cascade,
    language varchar(64),
    tool varchar(128) not null,
    check_name varchar(256) not null,
    status varchar(32) not null,
    reason text,
    tool_version varchar(128)
);

create table findings (
    id uuid primary key,
    scan_id uuid not null references scan_jobs(id) on delete cascade,
    category varchar(64) not null,
    severity varchar(32) not null,
    rule_id varchar(256) not null,
    title text not null,
    explanation text not null,
    remediation text not null,
    tool varchar(128) not null,
    tool_version varchar(128),
    language varchar(64),
    relative_path text not null,
    start_line integer,
    start_column integer,
    end_line integer,
    end_column integer,
    fingerprint varchar(128) not null,
    created_at timestamptz not null
);

create table finding_snippets (
    id uuid primary key,
    finding_id uuid not null references findings(id) on delete cascade,
    start_line integer not null,
    end_line integer not null,
    masked_text text not null,
    truncated boolean not null default false
);

create index idx_findings_scan on findings(scan_id);
create index idx_findings_fingerprint on findings(fingerprint);
