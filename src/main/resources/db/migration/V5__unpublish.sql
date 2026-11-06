-- F07 · Despublicación y corrección

alter table article add column unpublished_at    timestamptz;
alter table article add column unpublish_reason  text;
alter table article add column correction_note   text;

create index idx_article_unpublished on article (unpublished_at desc)
    where unpublished_at is not null;
