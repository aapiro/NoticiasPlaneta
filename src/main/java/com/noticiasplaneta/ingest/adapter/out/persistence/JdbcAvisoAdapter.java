package com.noticiasplaneta.ingest.adapter.out.persistence;

import com.noticiasplaneta.ingest.application.port.in.FuenteAdminUseCase;
import com.noticiasplaneta.ingest.application.port.out.AvisoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
class JdbcAvisoAdapter implements AvisoRepository {

    private final JdbcTemplate jdbc;

    JdbcAvisoAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FuenteAdminUseCase.Aviso> recientes(int limite) {
        // El nombre se resuelve con un left join: una fuente borrada a mano no debe
        // impedir que su aviso se vea.
        return jdbc.query("""
                select coalesce(s.name, a.entity_id) as fuente, a.action, a.after_state, a.at
                from audit_log a
                left join source s on s.id::text = a.entity_id
                where a.action in ('SOURCE_DEGRADED', 'SOURCE_RECOVERED')
                order by a.at desc
                limit ?""",
                (rs, n) -> new FuenteAdminUseCase.Aviso(
                        rs.getString("fuente"),
                        rs.getString("action"),
                        rs.getString("after_state"),
                        rs.getTimestamp("at").toInstant()),
                limite);
    }
}
