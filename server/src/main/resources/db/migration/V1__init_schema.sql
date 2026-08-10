create table ip_system (
    id bigint auto_increment primary key,
    system_code varchar(40) not null unique,
    system_name varchar(100) not null,
    base_url varchar(500),
    health_status varchar(20) not null default 'UNKNOWN',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table ip_interface (
    id bigint auto_increment primary key,
    interface_code varchar(80) not null unique,
    interface_name varchar(160) not null,
    description varchar(500),
    source_system_id bigint not null,
    target_system_id bigint not null,
    http_method varchar(10) not null,
    interface_path varchar(300) not null unique,
    target_url varchar(1000) not null,
    connect_timeout_ms int not null default 3000,
    read_timeout_ms int not null default 15000,
    enabled boolean not null default true,
    today_calls bigint not null default 0,
    success_rate decimal(6,2) not null default 0,
    avg_duration_ms bigint not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_interface_source foreign key (source_system_id) references ip_system(id),
    constraint fk_interface_target foreign key (target_system_id) references ip_system(id)
);

create table ip_datasource (
    id bigint auto_increment primary key,
    datasource_code varchar(80) not null unique,
    datasource_name varchar(160) not null,
    db_type varchar(40) not null,
    jdbc_url varchar(1000) not null,
    driver_class_name varchar(200) not null,
    encrypted_username varchar(1000) not null,
    encrypted_password varchar(1000) not null,
    health_status varchar(20) not null default 'UNKNOWN',
    enabled boolean not null default true,
    last_checked_at timestamp null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table ip_sql_api (
    id bigint auto_increment primary key,
    api_code varchar(80) not null unique,
    api_name varchar(160) not null,
    description varchar(500),
    api_path varchar(300) not null unique,
    http_method varchar(10) not null default 'POST',
    datasource_id bigint not null,
    select_sql text not null,
    timeout_seconds int not null default 10,
    max_rows int not null default 1000,
    enabled boolean not null default true,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_sql_api_datasource foreign key (datasource_id) references ip_datasource(id)
);

create table ip_api_client (
    id bigint auto_increment primary key,
    client_code varchar(80) not null unique,
    client_name varchar(160) not null,
    app_key varchar(120) not null unique,
    encrypted_app_secret varchar(1000) not null,
    enabled boolean not null default true,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table ip_invocation_log (
    id bigint auto_increment primary key,
    trace_id varchar(64) not null unique,
    route_type varchar(20) not null,
    interface_code varchar(80) not null,
    interface_name varchar(160) not null,
    caller varchar(160) not null,
    target_system varchar(160) not null,
    request_method varchar(10) not null,
    request_path varchar(500) not null,
    target_address varchar(1000),
    call_status varchar(20) not null,
    platform_code varchar(40),
    http_status int,
    duration_ms bigint not null,
    request_headers text,
    request_summary text,
    response_headers text,
    response_summary text,
    error_message text,
    call_time timestamp not null default current_timestamp,
    completed_at timestamp null
);

create index idx_invocation_log_time on ip_invocation_log(call_time);
create index idx_invocation_log_interface on ip_invocation_log(interface_code);
create index idx_invocation_log_status on ip_invocation_log(call_status);
