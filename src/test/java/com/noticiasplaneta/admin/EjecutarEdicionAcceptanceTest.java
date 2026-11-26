package com.noticiasplaneta.admin;

import com.noticiasplaneta.identity.application.port.out.AccountRepository;
import com.noticiasplaneta.identity.domain.Account;
import com.noticiasplaneta.identity.domain.AccountState;
import com.noticiasplaneta.identity.domain.Role;
import com.noticiasplaneta.identity.domain.Totp;
import com.noticiasplaneta.identity.domain.TotpSecret;
import com.noticiasplaneta.ingest.application.port.in.AddSourceUseCase;
import com.noticiasplaneta.ingest.application.port.out.FeedFetchOutcome;
import com.noticiasplaneta.ingest.application.port.out.FeedFetcher;
import com.noticiasplaneta.ingest.domain.FeedEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F2 · Aceptación del botón «Ejecutar edición».
 *
 * <p>Lo que se exige: con sesión de CHIEF publica y deja rastro; sin sesión, 302; sin
 * CSRF, 403. Además se comprueba el candado (dos ejecuciones no se solapan) y que la
 * confirmación es obligatoria.
 *
 * <p>El feed se sustituye por un doble: el botón no debe depender de internet en un test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("F2 · Aceptación: botón «Ejecutar edición» del panel")
class EjecutarEdicionAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-edicion-test");
        registry.add("security.api-key", () -> "clave-test");
        registry.add("admin.bootstrap.email", () -> "");
        // Nada programado debe tocar la base mientras el test mide lo que hace el botón.
        registry.add("ingest.scheduler.enabled", () -> "false");
        registry.add("autopublish.scheduler.enabled", () -> "false");
        registry.add("publish.scheduler.delay-ms", () -> "3600000");
        registry.add("outbox.relay.delay-ms", () -> "3600000");
        registry.add("ai.worker.delay-ms", () -> "3600000");
    }

    /** Cinco piezas con foto de un solo medio: con cupo 3 por fuente, se publican 3. */
    @TestConfiguration
    static class StubFeed {

        @Bean
        @Primary
        FeedFetcher stubFeedFetcher() {
            Instant base = Instant.now().minusSeconds(600);
            return new FeedFetcher() {
                @Override
                public FeedFetchOutcome fetch(String feedUrl, String etag, String lastModified) {
                    return new FeedFetchOutcome.Fetched(List.of(
                            entry("a", "El Congreso aprueba la reforma fiscal", base),
                            entry("b", "Sube la luz un 12% este mes", base.plusSeconds(60)),
                            entry("c", "La selección gana en el último minuto", base.plusSeconds(120)),
                            entry("d", "Nuevo modelo de inteligencia artificial", base.plusSeconds(180)),
                            entry("e", "El gobierno presenta los presupuestos", base.plusSeconds(240))
                    ), "etag-test", null);
                }
            };
        }

        private static FeedEntry entry(String id, String title, Instant publishedAt) {
            return new FeedEntry(id, title, title + ". Cuerpo de la noticia de prueba.",
                    "https://medio-de-prueba.example/" + id, publishedAt, "Redacción",
                    "https://medio-de-prueba.example/" + id + ".jpg");
        }
    }

    private static final String SECRET = "JBSWY3DPEHPK3PXP";
    private static final String PASSWORD = "contrasena-de-prueba-1234";
    private static final String EMAIL = "jefa@noticiasplaneta.com";
    private static final String EMAIL_AUTOR = "redactor@noticiasplaneta.com";

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accounts;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    AddSourceUseCase addSource;

    @Autowired
    JdbcTemplate jdbc;

    private MockHttpSession sesion;

    @BeforeEach
    void preparar() throws Exception {
        // Base limpia por escenario: el botón publica todo lo elegible, así que las cifras
        // solo son comparables si no arrastran artículos de otro test.
        jdbc.execute("truncate table edicion_ejecucion cascade");
        jdbc.execute("truncate table article cascade");
        jdbc.execute("truncate table ingest_item cascade");
        jdbc.execute("truncate table source cascade");
        jdbc.execute("truncate table audit_log");

        if (accounts.findByEmail(EMAIL).isEmpty()) {
            accounts.save(new Account(UUID.randomUUID(), EMAIL, Role.CHIEF.name(), Role.CHIEF,
                    AccountState.ACTIVE, encoder.encode(PASSWORD), SECRET, Set.of("general")));
        }
        if (accounts.findByEmail(EMAIL_AUTOR).isEmpty()) {
            accounts.save(new Account(UUID.randomUUID(), EMAIL_AUTOR, Role.AUTHOR.name(), Role.AUTHOR,
                    AccountState.ACTIVE, encoder.encode(PASSWORD), SECRET, Set.of("general")));
        }

        sesion = entrar(EMAIL);

        String marca = UUID.randomUUID().toString().substring(0, 8);
        addSource.add("Medio " + marca, "https://medio-" + marca + ".example/rss");
    }

    private MockHttpSession entrar(String email) throws Exception {
        String totp = Totp.code(new TotpSecret(SECRET), Instant.now());
        var result = mvc.perform(post("/admin/login").with(csrf())
                        .param("email", email)
                        .param("password", PASSWORD)
                        .param("totp", totp))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).as("el login debe abrir sesión").isNotNull();
        return session;
    }

    private int publicadas() {
        return jdbc.queryForObject("select count(*) from article where state = 'PUBLISHED'", Integer.class);
    }

    @Test
    @Order(1)
    @DisplayName("Con sesión de CHIEF publica, deja rastro y el informe se ve en el panel")
    void conSesionDeChiefPublica() throws Exception {
        var respuesta = mvc.perform(post("/admin/edicion/ejecutar").session(sesion).with(csrf())
                        .param("confirmo", "1")
                        .param("modo", "publicar"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String destino = respuesta.getResponse().getRedirectedUrl();
        assertThat(destino).as("el POST redirige al informe de la ejecución")
                .startsWith("/admin/edicion/");

        // ---- el informe guardado
        assertThat(jdbc.queryForObject("select estado from edicion_ejecucion", String.class))
                .isEqualTo("COMPLETADA");
        assertThat(jdbc.queryForObject("select publicadas from edicion_ejecucion", Integer.class))
                .as("cupo de 3 por fuente: 3 de las 5 piezas")
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("select fuentes from edicion_ejecucion", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select actor from edicion_ejecucion", String.class))
                .isEqualTo(EMAIL);

        // ---- se ha publicado por la máquina de estados, no por SQL
        assertThat(publicadas()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from article where state = 'DRAFT'", Integer.class))
                .isEqualTo(2);

        // ---- rastro: quién lanzó la edición y quién movió cada pieza
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_log where action = 'EDITION_RUN' and actor_id = ?
                """, Integer.class, EMAIL))
                .as("audit_log debe registrar quién lanzó la edición")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_log where action = 'ARTICLE_TRANSITION' and actor_id = ?
                """, Integer.class, EMAIL))
                .as("cada transición de la cadena deja su entrada con el mismo actor")
                .isGreaterThanOrEqualTo(3);

        // ---- resultado en la misma página (y en la portada del panel)
        String detalle = mvc.perform(get(destino).session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Piezas de esta edición")))
                .andReturn().getResponse().getContentAsString();
        Files.writeString(Path.of("/tmp/panel-edicion-detalle.html"), detalle);
        assertThat(detalle).contains(EMAIL).contains("Publicadas");

        String panel = mvc.perform(get("/admin/edicion").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ejecutar edición")))
                .andReturn().getResponse().getContentAsString();
        Files.writeString(Path.of("/tmp/panel-edicion.html"), panel);
        assertThat(panel).contains("Última ejecución").contains("COMPLETADA").contains(EMAIL);
    }

    @Test
    @Order(2)
    @DisplayName("Sin sesión no se entra ni se ejecuta: 302 al login")
    void sinSesionNoEjecuta() throws Exception {
        mvc.perform(get("/admin/edicion"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        mvc.perform(post("/admin/edicion/ejecutar").with(csrf())
                        .param("confirmo", "1")
                        .param("modo", "publicar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        assertThat(publicadas()).isZero();
    }

    @Test
    @Order(3)
    @DisplayName("Sin token CSRF no se ejecuta: 403")
    void sinCsrfRechaza() throws Exception {
        mvc.perform(post("/admin/edicion/ejecutar").session(sesion)
                        .param("confirmo", "1")
                        .param("modo", "publicar"))
                .andExpect(status().isForbidden());

        assertThat(publicadas()).isZero();
    }

    @Test
    @Order(4)
    @DisplayName("Sin marcar la confirmación no se lanza nada")
    void sinConfirmacionNoEjecuta() throws Exception {
        mvc.perform(post("/admin/edicion/ejecutar").session(sesion).with(csrf())
                        .param("modo", "publicar"))
                .andExpect(redirectedUrl("/admin/edicion?error=confirmar"));

        assertThat(publicadas()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from edicion_ejecucion", Integer.class)).isZero();
    }

    @Test
    @Order(5)
    @DisplayName("Un AUTHOR no puede lanzar la edición")
    void autorNoPublica() throws Exception {
        MockHttpSession sesionAutor = entrar(EMAIL_AUTOR);

        mvc.perform(post("/admin/edicion/ejecutar").session(sesionAutor).with(csrf())
                        .param("confirmo", "1")
                        .param("modo", "publicar"))
                .andExpect(redirectedUrl("/admin/edicion?error=permiso"));

        assertThat(publicadas()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from edicion_ejecucion", Integer.class)).isZero();
    }

    @Test
    @Order(6)
    @DisplayName("Dos ejecuciones no se solapan: la segunda rebota con 302 y no publica")
    void candadoContraSolapes() throws Exception {
        jdbc.update("""
                insert into edicion_ejecucion (id, iniciada_en, actor, modo, estado)
                values (?, now(), 'otra-instancia', 'publicar', 'EN_CURSO')
                """, UUID.randomUUID());

        mvc.perform(post("/admin/edicion/ejecutar").session(sesion).with(csrf())
                        .param("confirmo", "1")
                        .param("modo", "publicar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/edicion?error=en_curso"));

        assertThat(publicadas()).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from edicion_ejecucion where estado = 'EN_CURSO'
                """, Integer.class)).isEqualTo(1);
    }
}
