-- F13 · Cuentas del panel editorial

create table account (
    id            uuid primary key,
    email         text not null unique,
    display_name  text not null,
    role          text not null,
    state         text not null,
    password_hash text not null,
    totp_secret   text not null,
    created_at    timestamptz not null default now()
);

create table account_section (
    account_id uuid not null references account (id) on delete cascade,
    section_id text not null,
    primary key (account_id, section_id)
);
