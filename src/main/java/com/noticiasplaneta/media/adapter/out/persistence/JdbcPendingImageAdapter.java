package com.noticiasplaneta.media.adapter.out.persistence;

import com.noticiasplaneta.media.application.port.out.PendingImageRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
class JdbcPendingImageAdapter implements PendingImageRepository {

    private final JdbcTemplate jdbc;

    JdbcPendingImageAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingImage> findPending(int limit) {
        return jdbc.query("""
                select a.id, a.title, i.image_url
                from article a
                join ingest_item i on i.id::text = a.ingest_item_id
                where a.state = 'PUBLISHED'
                  and i.image_url is not null
                  and not exists (select 1 from article_media am where am.article_id = a.id)
                order by a.published_at desc nulls last
                limit ?""",
                (rs, n) -> new PendingImage(
                        rs.getString("id"), rs.getString("image_url"), rs.getString("title")),
                limit);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPending() {
        Long total = jdbc.queryForObject("""
                select count(*)
                from article a
                join ingest_item i on i.id::text = a.ingest_item_id
                where a.state = 'PUBLISHED'
                  and i.image_url is not null
                  and not exists (select 1 from article_media am where am.article_id = a.id)""",
                Long.class);
        return total == null ? 0 : total;
    }
}
