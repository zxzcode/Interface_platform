create table ip_user (
    id bigint auto_increment primary key,
    username varchar(80) not null unique,
    password_hash varchar(100) not null,
    display_name varchar(120) not null,
    role varchar(20) not null,
    enabled boolean not null default true,
    token_version bigint not null default 1,
    last_login_at timestamp null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table ip_client_permission (
    id bigint auto_increment primary key,
    client_id bigint not null,
    route_type varchar(20) not null,
    resource_code varchar(80) not null,
    created_at timestamp not null default current_timestamp,
    constraint fk_client_permission_client foreign key (client_id) references ip_api_client(id) on delete cascade,
    constraint uk_client_permission unique (client_id, route_type, resource_code)
);

create table ip_api_nonce (
    id bigint auto_increment primary key,
    client_id bigint not null,
    nonce_value varchar(100) not null,
    expires_at timestamp not null,
    created_at timestamp not null default current_timestamp,
    constraint fk_api_nonce_client foreign key (client_id) references ip_api_client(id) on delete cascade,
    constraint uk_api_nonce unique (client_id, nonce_value)
);

create index idx_api_nonce_expiry on ip_api_nonce(expires_at);
