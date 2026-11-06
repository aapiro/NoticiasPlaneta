-- F11 · Temas y línea temporal

create table topic (
    id         uuid primary key,
    slug       text not null unique,
    title      text not null,
    summary    text,
    created_at timestamptz not null default now()
);

create table article_topic (
    article_id uuid not null references article (id) on delete cascade,
    topic_id   uuid not null references topic (id) on delete cascade,
    primary key (article_id, topic_id)
);

create index idx_article_topic_topic on article_topic (topic_id);
