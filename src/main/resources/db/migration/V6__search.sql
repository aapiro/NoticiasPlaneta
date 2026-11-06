create extension if not exists vector;

-- F10 · Búsqueda híbrida (léxica + vectorial) sobre PostgreSQL

alter table article add column search_vector tsvector;
alter table article add column embedding vector(1024);

create index idx_article_search_vector on article using gin (search_vector);
create index idx_article_embedding on article using hnsw (embedding vector_cosine_ops);

-- Se mantiene solo, sin triggers en la aplicación.
create function article_search_vector_update() returns trigger as $$
begin
    new.search_vector :=
        setweight(to_tsvector('spanish', coalesce(new.title, '')), 'A');
    return new;
end;
$$ language plpgsql;

create trigger trg_article_search_vector
    before insert or update of title on article
    for each row execute function article_search_vector_update();
