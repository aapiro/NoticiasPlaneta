package com.noticiasplaneta.admin;

import com.noticiasplaneta.identity.application.port.out.AccountRepository;
import com.noticiasplaneta.identity.domain.Account;
import com.noticiasplaneta.identity.domain.AccountState;
import com.noticiasplaneta.identity.domain.Role;
import com.noticiasplaneta.identity.domain.Totp;
import com.noticiasplaneta.identity.domain.TotpSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Backlog #10 · Métricas de ingesta")
class MetricasAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-metricas-test");
        registry.add("security.api-key", () -> "clave-test");
        registry.add("admin.bootstrap.email", () -> "");
    }

    private static final String SECRET = "JBSWY3DPEHPK3PXP";
    private static final String PASSWORD = "contrasena-de-prueba-1234";
    private static final String EMAIL = "jefa@noticiasplaneta.com";

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accounts;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JdbcTemplate jdbc;

    private MockHttpSession sesion;

    /** Fuente con piezas: {@code piezas} items y {@code publicadas} de ellos publicados. */
    private String fuente(String nombre, int piezas, int publicadas) {
        String id = UUID.randomUUID().toString();
        String slug = nombre.toLowerCase().replace(' ', '-');
        jdbc.update("""
                insert into source (id, name, feed_url, state, failure_count, confianza, last_checked_at)
                values (?::uuid, ?, ?, 'ACTIVE', 0, false, now())""",
                id, nombre, "https://ejemplo.com/" + slug + "/rss");

        for (int i = 0; i < piezas; i++) {
            String itemId = UUID.randomUUID().toString();
            jdbc.update("""
                    insert into ingest_item (id, source_id, external_id, content_hash, title, body,
                                             canonical_url, published_at, created_at)
                    values (?::uuid, ?::uuid, ?, ?, ?, 'cuerpo', ?, now(), now())""",
                    itemId, id, "ext-" + i, "hash-" + i, "Titular " + i,
                    "https://ejemplo.com/" + slug + "/" + i);

            if (i < publicadas) {
                jdbc.update("""
                        insert into article (id, language, section_id, origin, ingest_item_id, slug,
                                             title, body, state, published_at, created_at)
                        values (gen_random_uuid(), 'es', 'general', 'INGEST', ?, ?, ?, 'cuerpo',
                                'PUBLISHED', now(), now())""",
                        itemId, slug + "-" + i, "Titular " + i);
            }
        }
        return id;
    }

    @BeforeEach
    void entrar() throws Exception {
        if (accounts.findByEmail(EMAIL).isEmpty()) {
            accounts.save(new Account(UUID.randomUUID(), EMAIL, Role.CHIEF.name(), Role.CHIEF,
                    AccountState.ACTIVE, encoder.encode(PASSWORD), SECRET, Set.of("general")));
        }
        if (jdbc.queryForObject("select count(*) from source", Integer.class) == 0) {
            fuente("El País", 60, 3);      // 5 % → justo en el umbral de ruidosa
            fuente("ABC", 20, 15);         // 75 %
        }

        String totp = Totp.code(new TotpSecret(SECRET), Instant.now());
        var result = mvc.perform(post("/admin/login").with(csrf())
                        .param("email", EMAIL)
                        .param("password", PASSWORD)
                        .param("totp", totp))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        sesion = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(sesion).isNotNull();
    }

    @Test
    @DisplayName("El panel pinta totales, la tabla por fuente y el porcentaje")
    void pintaElInforme() throws Exception {
        String html = mvc.perform(get("/admin/metricas").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Métricas de ingesta")))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("El País").contains("ABC");
        assertThat(html).contains("piezas ingeridas");
        // ABC: 15 de 20 → 75 %
        assertThat(html).contains("75%");
    }

    @Test
    @DisplayName("Una fuente que trae mucho y no publica sale marcada como ruidosa")
    void senalaLasRuidosas() throws Exception {
        String html = mvc.perform(get("/admin/metricas").session(sesion))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Traen mucho y no publican nada");
        assertThat(html).contains("El País (60 piezas, 3 publicadas)");
        // ABC publica el 75 %: no debe aparecer en esa lista
        assertThat(html).doesNotContain("ABC (20 piezas");
    }

    @Test
    @DisplayName("Sin artículos publicados avisa de que falta pasar por la bandeja")
    void avisaSinPublicados() throws Exception {
        jdbc.update("update article set state = 'DRAFT'");
        String html = mvc.perform(get("/admin/metricas").session(sesion))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Nada publicado todavía");
    }

    @Test
    @DisplayName("La ventana de días es configurable y no revienta con valores raros")
    void ventanaDeDias() throws Exception {
        mvc.perform(get("/admin/metricas").param("dias", "7").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("últimos")));
        mvc.perform(get("/admin/metricas").param("dias", "999").session(sesion))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/metricas").param("dias", "-5").session(sesion))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Sin sesión no se ven las métricas")
    void sinSesion() throws Exception {
        mvc.perform(get("/admin/metricas")).andExpect(status().is3xxRedirection());
    }
}
