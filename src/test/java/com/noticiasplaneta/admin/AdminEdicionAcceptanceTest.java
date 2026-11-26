package com.noticiasplaneta.admin;

import com.noticiasplaneta.edicion.application.port.in.EdicionAdminUseCase;
import com.noticiasplaneta.edicion.application.port.in.EjecucionView;
import com.noticiasplaneta.edicion.application.port.in.SeccionEstadoView;
import com.noticiasplaneta.edicion.application.port.in.TableroEdicionView;
import com.noticiasplaneta.identity.application.port.out.AccountRepository;
import com.noticiasplaneta.identity.domain.Account;
import com.noticiasplaneta.identity.domain.AccountState;
import com.noticiasplaneta.identity.domain.Role;
import com.noticiasplaneta.identity.domain.Totp;
import com.noticiasplaneta.identity.domain.TotpSecret;
import com.noticiasplaneta.ingest.application.port.in.AddSourceUseCase;
import com.noticiasplaneta.ingest.application.port.out.FeedFetcher;
import com.noticiasplaneta.ingest.application.port.out.FeedFetchOutcome;
import com.noticiasplaneta.ingest.domain.FeedEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F7 · Aceptación del panel de estado editorial.
 *
 * <p>Criterio del tablero: <em>tras dos ejecuciones se muestran las dos con sus cifras y el
 * estado coincide con la BD</em>. Aquí se lanzan de verdad por HTTP (sesión + CSRF), con el
 * feed sustituido por un doble para no depender de la red, y se contrasta cada número con
 * una consulta a PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("F7 · Aceptación: panel de estado de la edición")
class AdminEdicionAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-edicion-test");
        registry.add("security.api-key", () -> "clave-test");
        registry.add("admin.bootstrap.email", () -> "");
        // el programador periódico no debe meter ruido en una prueba de ejecución manual
        registry.add("ingest.scheduler.enabled", () -> "false");
    }

    /** Seis piezas con foto y recientes: la sección general tiene cupo 3 por edición. */
    @TestConfiguration
    static class StubFeed {
        @Bean
        @Primary
        FeedFetcher stubFeedFetcher() {
            return (feedUrl, etag, lastModified) -> {
                List<FeedEntry> entradas = new ArrayList<>();
                for (int i = 1; i <= 6; i++) {
                    entradas.add(new FeedEntry(
                            "item-" + i,
                            "Titular de prueba número " + i,
                            "Cuerpo de la noticia " + i + " con detalle suficiente.",
                            "https://ejemplo.es/noticia-" + i,
                            Instant.now().minusSeconds(3600L * i),
                            "Redacción",
                            "https://ejemplo.es/foto-" + i + ".jpg"));
                }
                return new FeedFetchOutcome.Fetched(entradas, "etag-test", null);
            };
        }
    }

    private static final String SECRET = "JBSWY3DPEHPK3PXP";
    private static final String PASSWORD = "contrasena-de-prueba-1234";
    private static final String CHIEF = "jefa@noticiasplaneta.com";
    private static final String AUTHOR = "redactor@noticiasplaneta.com";

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accounts;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    AddSourceUseCase addSource;

    @Autowired
    EdicionAdminUseCase edicion;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        crear(CHIEF, Role.CHIEF);
        crear(AUTHOR, Role.AUTHOR);
        // Los métodos comparten contenedor: sin limpiar, las cifras de uno contaminan al
        // siguiente y un «tras dos ejecuciones» acaba contando seis.
        jdbc.execute("truncate table edicion_ejecucion, article, ingest_item, source cascade");
        addSource.add("Diario de prueba", "https://ejemplo.es/rss-" + UUID.randomUUID());
    }

    private void crear(String email, Role role) {
        if (accounts.findByEmail(email).isEmpty()) {
            accounts.save(new Account(UUID.randomUUID(), email, role.name(), role,
                    AccountState.ACTIVE, encoder.encode(PASSWORD), SECRET, Set.of("general")));
        }
    }

    private MockHttpSession sesion(String email) throws Exception {
        String totp = Totp.code(new TotpSecret(SECRET), Instant.now());
        var resultado = mvc.perform(post("/admin/login").with(csrf())
                        .param("email", email)
                        .param("password", PASSWORD)
                        .param("totp", totp))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    private String ejecutar(MockHttpSession sesion, String modo) throws Exception {
        var resultado = mvc.perform(post("/admin/edicion/ejecutar").session(sesion).with(csrf())
                        .param("confirmo", "1")
                        .param("modo", modo))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return resultado.getResponse().getRedirectedUrl();
    }

    // ------------------------------------------------------------ criterio del tablero

    @Test
    @DisplayName("Dos ejecuciones: las dos aparecen con sus cifras y coinciden con la BD")
    void dosEjecucionesConSusCifras() throws Exception {
        MockHttpSession sesion = sesion(CHIEF);

        String primera = ejecutar(sesion, "publicar");
        String segunda = ejecutar(sesion, "publicar");

        assertThat(primera).startsWith("/admin/edicion/");
        assertThat(segunda).startsWith("/admin/edicion/");
        assertThat(primera).isNotEqualTo(segunda);

        // La BD tiene las dos ejecuciones y ninguna quedó abierta.
        assertThat(jdbc.queryForObject("select count(*) from edicion_ejecucion", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from edicion_ejecucion where estado = 'EN_CURSO'", Integer.class))
                .isZero();

        // Cada cifra guardada es la que produjo esa ejecución.
        Map<String, Object> fila1 = jdbc.queryForMap(
                "select id, fuentes, creadas, borradores, publicadas, errores, estado "
                        + "from edicion_ejecucion order by iniciada_en limit 1");
        Map<String, Object> fila2 = jdbc.queryForMap(
                "select id, fuentes, creadas, borradores, publicadas, errores, estado "
                        + "from edicion_ejecucion order by iniciada_en desc limit 1");

        assertThat(fila1.get("fuentes")).isEqualTo(1);
        assertThat(fila1.get("creadas")).isEqualTo(6);
        assertThat(fila1.get("borradores")).isEqualTo(6);
        assertThat(fila1.get("publicadas")).isEqualTo(3);
        assertThat(fila1.get("errores")).isEqualTo(0);
        assertThat(fila1.get("estado")).isEqualTo("COMPLETADA");

        // La segunda reingiere lo mismo: no duplica piezas ni publica de más (cupo por edición).
        assertThat(fila2.get("creadas")).isEqualTo(0);
        assertThat(fila2.get("publicadas")).isEqualTo(3);

        // Y lo publicado de verdad en `article` es exactamente lo que dicen las ejecuciones.
        int publicadasEnBd = jdbc.queryForObject(
                "select count(*) from article where state = 'PUBLISHED'", Integer.class);
        int publicadasEnEjecuciones = jdbc.queryForObject(
                "select count(*) from edicion_ejecucion_pieza", Integer.class);
        assertThat(publicadasEnBd).isEqualTo(6);
        assertThat(publicadasEnEjecuciones).isEqualTo(publicadasEnBd);

        // Cada ejecución deja rastro de quién la lanzó, además del de cada transición.
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_log where action = 'EDITION_RUN' and actor_id = ?",
                Integer.class, CHIEF)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_log where action = 'ARTICLE_TRANSITION' and actor_id = ?",
                Integer.class, CHIEF)).isEqualTo(24);

        // El panel muestra ambas con sus cifras.
        String html = mvc.perform(get("/admin/edicion").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(fila1.get("id").toString(), fila2.get("id").toString());
        assertThat(html).contains("COMPLETADA");
        assertThat(html).contains("Última ejecución", "Histórico", "Estado por sección");
        // Dos filas de histórico, con su enlace a la edición generada cada una.
        assertThat(html.split(">Ver<", -1).length - 1).isEqualTo(2);
    }

    @Test
    @DisplayName("El estado por sección es el de la BD: piezas de hoy y cupo restante")
    void estadoPorSeccionCoincideConLaBd() throws Exception {
        MockHttpSession sesion = sesion(CHIEF);
        ejecutar(sesion, "publicar");

        TableroEdicionView tablero = edicion.tablero();
        assertThat(tablero.secciones()).isNotEmpty();

        for (SeccionEstadoView seccion : tablero.secciones()) {
            Integer hoyEnBd = jdbc.queryForObject("""
                    select count(*) from article
                     where section_id = ? and state = 'PUBLISHED'
                       and published_at >= (date_trunc('day', now() at time zone 'Europe/Madrid')
                                            at time zone 'Europe/Madrid')
                    """, Integer.class, seccion.slug());
            Integer cupoEnBd = jdbc.queryForObject(
                    "select max_piezas_por_edicion from section_config where slug = ?",
                    Integer.class, seccion.slug());
            Boolean autoEnBd = jdbc.queryForObject(
                    "select auto_publica from section_config where slug = ?", Boolean.class, seccion.slug());

            assertThat(seccion.piezasHoy()).as("piezas de hoy en %s", seccion.slug()).isEqualTo(hoyEnBd);
            assertThat(seccion.cupo()).isEqualTo(cupoEnBd);
            assertThat(seccion.automatica()).isEqualTo(autoEnBd);
            assertThat(seccion.cupoRestante()).isEqualTo(Math.max(0, cupoEnBd - hoyEnBd));
        }

        String html = mvc.perform(get("/admin/edicion").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (SeccionEstadoView seccion : tablero.secciones()) {
            assertThat(html).contains(seccion.nombre());
        }
    }

    @Test
    @DisplayName("Activar el automático de una sección se guarda y se ve en el panel")
    void interruptorDeAutomatico() throws Exception {
        MockHttpSession sesion = sesion(CHIEF);

        mvc.perform(post("/admin/edicion/automatico").session(sesion).with(csrf())
                        .param("slug", "general")
                        .param("auto", "1"))
                .andExpect(redirectedUrl("/admin/edicion?ok=auto"));

        assertThat(jdbc.queryForObject(
                "select auto_publica from section_config where slug = 'general'", Boolean.class))
                .isTrue();

        String html = mvc.perform(get("/admin/edicion").session(sesion))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("automática");

        // Desmarcado: el checkbox no viaja y esa ausencia significa «manual».
        mvc.perform(post("/admin/edicion/automatico").session(sesion).with(csrf())
                        .param("slug", "general"))
                .andExpect(redirectedUrl("/admin/edicion?ok=auto"));

        assertThat(jdbc.queryForObject(
                "select auto_publica from section_config where slug = 'general'", Boolean.class))
                .isFalse();
    }

    @Test
    @DisplayName("El detalle de una ejecución enlaza sus piezas y descarga el markdown")
    void detalleYMarkdown() throws Exception {
        MockHttpSession sesion = sesion(CHIEF);
        String destino = ejecutar(sesion, "publicar");
        String id = destino.substring("/admin/edicion/".length());

        String html = mvc.perform(get(destino).session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Piezas de esta edición", "Titular de prueba número");

        mvc.perform(get("/admin/edicion/" + id + "/edicion.md").session(sesion))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("# Noticias Planeta")));
    }

    // ------------------------------------------------------------ seguridad y candado

    @Test
    @DisplayName("Sin sesión no se ve el panel ni se ejecuta nada")
    void sinSesion() throws Exception {
        mvc.perform(get("/admin/edicion"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        mvc.perform(post("/admin/edicion/ejecutar").with(csrf()).param("confirmo", "1"))
                .andExpect(status().is3xxRedirection());

        assertThat(jdbc.queryForObject("select count(*) from edicion_ejecucion", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Sin CSRF no se ejecuta la edición")
    void sinCsrf() throws Exception {
        MockHttpSession sesion = sesion(CHIEF);

        mvc.perform(post("/admin/edicion/ejecutar").session(sesion).param("confirmo", "1"))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select count(*) from edicion_ejecucion", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Un AUTHOR puede mirar pero no lanzar la edición")
    void authorNoEjecuta() throws Exception {
        MockHttpSession sesion = sesion(AUTHOR);

        mvc.perform(get("/admin/edicion").session(sesion))
                .andExpect(status().isOk());

        mvc.perform(post("/admin/edicion/ejecutar").session(sesion).with(csrf()).param("confirmo", "1"))
                .andExpect(redirectedUrl("/admin/edicion?error=permiso"));

        assertThat(jdbc.queryForObject("select count(*) from edicion_ejecucion", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Sin confirmar no se lanza; y una ejecución abierta bloquea la siguiente")
    void confirmacionYCandado() throws Exception {
        MockHttpSession sesion = sesion(CHIEF);

        mvc.perform(post("/admin/edicion/ejecutar").session(sesion).with(csrf()))
                .andExpect(redirectedUrl("/admin/edicion?error=confirmar"));
        assertThat(jdbc.queryForObject("select count(*) from edicion_ejecucion", Integer.class)).isZero();

        // Otra ejecución en curso (otra pestaña, otro proceso): el índice único la rechaza.
        jdbc.update("""
                insert into edicion_ejecucion (id, iniciada_en, actor, modo, estado)
                values (?, now(), 'otra-pestana@noticiasplaneta.com', 'publicar', 'EN_CURSO')
                """, UUID.randomUUID());

        mvc.perform(post("/admin/edicion/ejecutar").session(sesion).with(csrf()).param("confirmo", "1"))
                .andExpect(redirectedUrl("/admin/edicion?error=en_curso"));

        assertThat(jdbc.queryForObject("select count(*) from edicion_ejecucion", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Una ejecución abandonada se cierra sola y no bloquea para siempre")
    void ejecucionAbandonadaSeCierra() throws Exception {
        UUID abandonada = UUID.randomUUID();
        jdbc.update("""
                insert into edicion_ejecucion (id, iniciada_en, actor, modo, estado)
                values (?, now() - interval '2 hours', 'proceso-muerto@noticiasplaneta.com', 'publicar', 'EN_CURSO')
                """, abandonada);

        MockHttpSession sesion = sesion(CHIEF);
        ejecutar(sesion, "publicar");

        assertThat(jdbc.queryForObject(
                "select estado from edicion_ejecucion where id = ?", String.class, abandonada))
                .isEqualTo("FALLIDA");
        assertThat(jdbc.queryForObject(
                "select count(*) from edicion_ejecucion where estado = 'EN_CURSO'", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("El histórico no se inventa: cada ejecución guarda lo que hizo")
    void historicoPersistente() throws Exception {
        MockHttpSession sesion = sesion(CHIEF);
        ejecutar(sesion, "borrador");


        EjecucionView primera = edicion.tablero().ultima();
        assertThat(primera).isNotNull();
        assertThat(primera.modo()).isEqualTo("borrador");
        assertThat(primera.publicadas()).isZero();
        assertThat(primera.borradores()).isEqualTo(6);
        // En modo borrador no se publica nada, pero la edición queda seleccionada.
        assertThat(jdbc.queryForObject(
                "select count(*) from article where state = 'PUBLISHED'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from edicion_ejecucion_pieza where ejecucion_id = ?::uuid",
                Integer.class, primera.id())).isEqualTo(3);
    }
}
