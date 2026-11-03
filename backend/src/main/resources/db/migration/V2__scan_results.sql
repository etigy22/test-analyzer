create table scan_results (
    scan_id uuid primary key references scan_jobs(id) on delete cascade,
    result_json text not null
);
create table update_runs (
    id uuid primary key,
    status varchar(32) not null,
    started_at timestamptz not null,
    completed_at timestamptz,
    message text not null,
    data_version text
);
create index idx_scan_jobs_created on scan_jobs(created_at desc);
