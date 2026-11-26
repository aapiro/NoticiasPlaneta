package com.noticiasplaneta.autopublish;

import com.noticiasplaneta.ai.application.port.in.ClassifyArticleUseCase;
import com.noticiasplaneta.ai.application.port.out.AiCapabilitiesAdminPort;
import com.noticiasplaneta.ai.domain.AiCapability;
import com.noticiasplaneta.ai.domain.AiMode;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F6 — aceptación de la sección automatizada.
 *
 * <p>El modelo se sustituye por un doble porque sin clave de DeepSeek no hay proveedor
 * real; lo que se verifica aquí es el mecanismo completo: reglas, clasificación aplicada,
 * publicación con su enfoque y su marca, auditoría, outbox y los dos interruptores.
 * La llamada HTTP al modelo tiene su propio test con un servidor local.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("F6 · Aceptación: sección automatizada con IA")
class AutoPublishAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-auto-test");
        registry.add("security.api-key", () -> "clave-test");
        registry.add("admin.bootstrap.email", () -> "");
    }

    private static final String SECRET = "JBSWY3DPEHPK3PXP";
    private static final String PASSWORD = "contrasena-de-prueba-1234";
    private static final String EMAIL = "jefa@noticiasplaneta.com";
    private static final String CUERPO = "Cuerpo de la noticia de prueba con longitud suficiente para"
            + " que la pieza sea publicable y no se descarte por corta.";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AccountRepository accounts;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    AiCapabilitiesAdminPort capabilities;

    @MockitoBean
    ClassifyArticleUseCase classifier;

    private MockHttpSession sesion;
    private UUID fuente;

    @BeforeEach
    void preparar() throws Exception {
        jdbc.update("delete from article_auto_publicacion");
        jdbc.update("delete from article_auto_classification");
        jdbc.update("delete from outbox");
        jdbc.update("delete from audit_log");
        jdbc.update("delete from ai_job");
        jdbc.update("delete from article");
        jdbc.update("delete from ingest_item");
        jdbc.update("delete from source");
        jdbc.update("update section_config set activa = true, auto_publica = false,"
                + " auto_limite_diario = 3, enfoque = 'NEUTRAL'");
        capabilities.setMode(AiCapability.PUBLISH_AUTO, AiMode.OFF, "test");

        when(classifier.classify(anyString(), anyList(), anyString()))
                .thenReturn(new ClassifyArticleUseCase.Classification(
                        "tecnologia", 0.92, "modelo-de-prueba", "classify-v1"));

        fuente = insertarFuente(true);
        entrar();
    }

    @Test
    @DisplayName("Con tecnología en automático publica con su enfoque, su marca y su auditoría")
    void publicaConEnfoqueYMarca() throws Exception {
        auto("tecnologia", true, 2, "IZQUIERDA");
        UUID articulo = insertarBorrador(fuente, "general", "Nueva ley de IA en el Congreso",
                "nueva-ley-ia", Instant.now().minus(2, ChronoUnit.HOURS));

        encender(true).andExpect(status().is3xxRedirection());
        ejecutar().andExpect(status().is3xxRedirection());

        assertThat(estado(articulo)).isEqualTo("PUBLISHED");
        assertThat(seccion(articulo)).isEqualTo("tecnologia");

        var marca = jdbc.queryForMap(
                "select seccion, enfoque, modelo, prompt_version from article_auto_publicacion"
                        + " where article_id = ?", articulo);
        assertThat(marca.get("seccion")).isEqualTo("tecnologia");
        assertThat(marca.get("enfoque")).isEqualTo("IZQUIERDA");
        assertThat(marca.get("modelo")).isEqualTo("modelo-de-prueba");
        assertThat(marca.get("prompt_version")).isEqualTo("classify-v1");
        assertThat(jdbc.queryForObject(
                "select array_to_string(reglas, ',') from article_auto_publicacion where article_id = ?",
                String.class, articulo))
                .contains("ventana_48h", "fuente_confianza", "sin_reescribir_texto");

        assertThat(jdbc.queryForObject("select editorial_bias from article where id = ?",
                String.class, articulo)).isEqualTo("IZQUIERDA");

        assertThat(jdbc.queryForObject("""
                select count(*) from audit_log
                where entity_id = ? and action = 'ARTICLE_AUTO_PUBLISHED'
                """, Integer.class, articulo.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select after_state from audit_log
                where entity_id = ? and action = 'ARTICLE_AUTO_PUBLISHED'
                """, String.class, articulo.toString()))
                .contains("automatica", "enfoque=IZQUIERDA", "modelo=modelo-de-prueba");

        assertThat(jdbc.queryForObject("""
                select count(*) from outbox where aggregate_id = ? and event_type = 'ArticlePublished.v1'
                """, Integer.class, articulo)).isEqualTo(1);

        String html = mvc.perform(get("/tecnologia/nueva-ley-ia"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Publicación automática");
    }

    @Test
    @DisplayName("Al apagar el interruptor global no publica ni clasifica nada más")
    void interruptorGlobalApagado() throws Exception {
        auto("tecnologia", true, 2, "NEUTRAL");
        UUID articulo = insertarBorrador(fuente, "general", "Nuevo chip de Apple",
                "nuevo-chip-apple", Instant.now().minus(1, ChronoUnit.HOURS));

        ejecutar().andExpect(status().is3xxRedirection());

        assertThat(estado(articulo)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("select count(*) from article_auto_classification where article_id = ?",
                Integer.class, articulo)).isZero();
    }

    @Test
    @DisplayName("Al apagar la sección se clasifica pero no se publica")
    void seccionApagada() throws Exception {
        auto("tecnologia", false, 2, "NEUTRAL");
        UUID articulo = insertarBorrador(fuente, "general", "Ataque de ciberseguridad",
                "ataque-ciberseguridad", Instant.now().minus(1, ChronoUnit.HOURS));

        encender(true);
        ejecutar();

        assertThat(estado(articulo)).isEqualTo("DRAFT");
        assertThat(seccion(articulo)).isEqualTo("tecnologia");
        assertThat(jdbc.queryForObject("select count(*) from article_auto_classification where article_id = ?",
                Integer.class, articulo)).isEqualTo(1);
    }

    @Test
    @DisplayName("Fuera de las 48 h no se publica")
    void ventanaVencida() throws Exception {
        auto("tecnologia", true, 2, "NEUTRAL");
        UUID articulo = insertarBorrador(fuente, "general", "Noticia vieja de tecnología",
                "noticia-vieja", Instant.now().minus(72, ChronoUnit.HOURS));

        encender(true);
        ejecutar();

        assertThat(estado(articulo)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("select count(*) from article_auto_classification where article_id = ?",
                Integer.class, articulo)).isZero();
    }

    @Test
    @DisplayName("No repite un titular ya publicado")
    void noRepiteTitular() throws Exception {
        auto("tecnologia", true, 2, "NEUTRAL");
        String titular = "OpenAI presenta un modelo nuevo";
        insertarPublicada("openai-modelo-previa", titular);
        UUID articulo = insertarBorrador(fuente, "general", titular, "openai-modelo-otra",
                Instant.now().minus(1, ChronoUnit.HOURS));

        encender(true);
        ejecutar();

        assertThat(estado(articulo)).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("Una fuente sin marca de confianza no publica sola")
    void fuenteSinConfianza() throws Exception {
        auto("tecnologia", true, 2, "NEUTRAL");
        jdbc.update("update source set confianza = false where id = ?", fuente);
        UUID articulo = insertarBorrador(fuente, "general", "Startup española levanta capital",
                "startup-capital", Instant.now().minus(1, ChronoUnit.HOURS));

        encender(true);
        ejecutar();

        assertThat(estado(articulo)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("select count(*) from article_auto_classification where article_id = ?",
                Integer.class, articulo)).isZero();
    }

    @Test
    @DisplayName("El cupo diario corta: con cupo 1 se publica una y la otra espera")
    void cupoDiario() throws Exception {
        auto("tecnologia", true, 1, "NEUTRAL");
        UUID primera = insertarBorrador(fuente, "general", "Primera noticia tecnológica",
                "primera-tecnologia", Instant.now().minus(1, ChronoUnit.HOURS));
        UUID segunda = insertarBorrador(fuente, "general", "Segunda noticia tecnológica",
                "segunda-tecnologia", Instant.now().minus(2, ChronoUnit.HOURS));

        encender(true);
        ejecutar();

        long publicadas = jdbc.queryForObject("select count(*) from article where state = 'PUBLISHED'",
                Long.class);
        assertThat(publicadas).isEqualTo(1);
        assertThat(estado(primera)).isEqualTo("PUBLISHED");
        assertThat(estado(segunda)).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("El panel muestra interruptor, secciones, cupo y fuentes")
    void panelRenderiza() throws Exception {
        auto("tecnologia", true, 2, "NEUTRAL");

        String html = mvc.perform(get("/admin/autopublicacion").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html)
                .contains("Publicación automática")
                .contains("Interruptor global")
                .contains("Tecnología")
                .contains("Fuentes de confianza")
                .contains("Fuente de prueba");
    }

    @Test
    @DisplayName("Sin proveedor de IA no se publica ni lo que ya estaba en la sección automática")
    void sinProveedorNoPublica() throws Exception {
        auto("tecnologia", true, 2, "NEUTRAL");
        UUID articulo = insertarBorrador(fuente, "tecnologia",
                "Pieza que el ilike dejo en tecnologia", "ilike-tecnologia",
                Instant.now().minus(1, ChronoUnit.HOURS));
        when(classifier.classify(anyString(), anyList(), anyString()))
                .thenThrow(new com.noticiasplaneta.shared.domain.DomainException(
                        "AI_NOT_CONFIGURED", "sin proveedor"));

        encender(true);
        ejecutar();

        assertThat(estado(articulo)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("select count(*) from article_auto_publicacion", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("Sin sesión no se ejecuta la pasada")
    void sinSesion() throws Exception {
        mvc.perform(post("/admin/autopublicacion/ejecutar").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    // ---------- utilidades ----------

    private org.springframework.test.web.servlet.ResultActions encender(boolean activo) throws Exception {
        return mvc.perform(post("/admin/autopublicacion/global").session(sesion).with(csrf())
                        .param("activo", String.valueOf(activo)))
                .andExpect(status().is3xxRedirection());
    }

    private org.springframework.test.web.servlet.ResultActions ejecutar() throws Exception {
        return mvc.perform(post("/admin/autopublicacion/ejecutar").session(sesion).with(csrf()));
    }

    private void entrar() throws Exception {
        if (accounts.findByEmail(EMAIL).isEmpty()) {
            accounts.save(new Account(UUID.randomUUID(), EMAIL, Role.CHIEF.name(), Role.CHIEF,
                    AccountState.ACTIVE, encoder.encode(PASSWORD), SECRET, Set.of("tecnologia", "politica")));
        }
        String totp = Totp.code(new TotpSecret(SECRET), Instant.now());
        var result = mvc.perform(post("/admin/login").with(csrf())
                        .param("email", EMAIL)
                        .param("password", PASSWORD)
                        .param("totp", totp))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        sesion = (MockHttpSession) result.getRequest().getSession(false);
    }

    private void auto(String slug, boolean autoPublica, int limite, String enfoque) {
        jdbc.update("update section_config set auto_publica = ?, auto_limite_diario = ?, enfoque = ?"
                + " where slug = ?", autoPublica, limite, enfoque, slug);
    }

    private UUID insertarFuente(boolean confianza) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into source (id, name, feed_url, state, failure_count, confianza)"
                        + " values (?, 'Fuente de prueba', ?, 'ACTIVE', 0, ?)",
                id, "https://ejemplo.test/" + id + ".xml", confianza);
        return id;
    }

    private UUID insertarBorrador(UUID sourceId, String seccion, String titulo, String slug, Instant fecha) {
        UUID item = UUID.randomUUID();
        jdbc.update("insert into ingest_item (id, source_id, external_id, content_hash, title, body,"
                        + " canonical_url, published_at, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, now())",
                item, sourceId, "ext-" + item, "hash-" + item, titulo, CUERPO,
                "https://ejemplo.test/" + slug, Timestamp.from(fecha));
        UUID articulo = UUID.randomUUID();
        jdbc.update("insert into article (id, language, section_id, origin, ingest_item_id, slug, title,"
                        + " body, state, created_at) values (?, 'es', ?, 'INGEST', ?, ?, ?, ?, 'DRAFT', now())",
                articulo, seccion, item.toString(), slug, titulo, CUERPO);
        return articulo;
    }

    private void insertarPublicada(String slug, String titulo) {
        UUID item = UUID.randomUUID();
        jdbc.update("insert into ingest_item (id, source_id, external_id, content_hash, title, body,"
                        + " canonical_url, published_at, created_at) values (?, ?, ?, ?, ?, ?, ?, now(), now())",
                item, fuente, "ext-" + item, "hash-" + item, titulo, CUERPO, "https://ejemplo.test/" + slug);
        jdbc.update("insert into article (id, language, section_id, origin, ingest_item_id, slug, title,"
                        + " body, state, published_at, created_at) values (?, 'es', 'tecnologia', 'INGEST', ?,"
                        + " ?, ?, ?, 'PUBLISHED', now(), now())",
                UUID.randomUUID(), item.toString(), slug, titulo, CUERPO);
    }

    private String estado(UUID articulo) {
        return jdbc.queryForObject("select state from article where id = ?", String.class, articulo);
    }

    private String seccion(UUID articulo) {
        return jdbc.queryForObject("select section_id from article where id = ?", String.class, articulo);
    }
}
