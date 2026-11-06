-- F09 · Passkeys (WebAuthn). Esquema que espera Spring Security.

create table user_entities (
    id           bytea        primary key,
    name         varchar(200) not null,
    display_name varchar(200) not null,
    constraint uq_user_entities_name unique (name)
);

create table user_credentials (
    credential_id                 bytea        primary key,
    user_entity_user_id           bytea        not null references user_entities (id),
    public_key                    bytea,
    signature_count               bigint       not null default 0,
    uv_initialized                boolean      not null default false,
    backup_eligible               boolean      not null default false,
    authenticator_transports      varchar(255),
    public_key_credential_type    varchar(100) not null,
    backup_state                  boolean      not null default false,
    attestation_object            bytea,
    attestation_client_data_json  varchar(2000),
    created                       timestamptz  not null,
    last_used                     timestamptz,
    label                         varchar(200)
);

create index idx_user_credentials_user on user_credentials (user_entity_user_id);
