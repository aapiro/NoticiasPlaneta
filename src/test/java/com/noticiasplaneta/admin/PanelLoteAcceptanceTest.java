package com.noticiasplaneta.admin;

import com.noticiasplaneta.identity.application.port.out.AccountRepository;
import com.noticiasplaneta.identity.domain.Account;
import com.noticiasplaneta.identity.domain.AccountState;
import com.noticiasplaneta.identity.domain.Role;
import com.noticiasplaneta.identity.domain.Totp;
import com.noticiasplaneta.identity.domain.TotpSecret;
import com.noticiasplaneta.news.application.port.in.CreateDraftCommand;
import com.noticiasplaneta.news.application.port.in.CreateDraftFromItemUseCase;
import com.noticiasplaneta.news.application.port.in.CreateDraftResult;
import com.noticiasplaneta.news.domain.ArticleOrigin;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F5 · Aceptación de la publicación por lotes.
 *
 * <p>Postgres real (Testcontainers), login real por el filtro del panel y el mismo
 * formulario que usa el editor: lo que se prueba aquí es lo que se publica mañana.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("F5 · Aceptación: publicación por lotes")
class PanelLoteAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-lote-test");
        registry.add("security.api-key", () -> "clave-test");
        registry.add("admin.bootstrap.email", () -> "");
    }

    private static final String SECRET = "JBSWY3DPEHPK3PXP";
    private static final String PASSWORD = "contrasena-de-prueba-1234";
    private static final String JEFA = "jefa-lote@noticiasplaneta.com";
    private static final String REDACTOR = "redactor-lote@noticiasplaneta.com";
    private static final String LOTE = "/admin/articulos/publicar-lote";

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accounts;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    CreateDraftFromItemUseCase createDraft;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void crearCuentas() {
        crear(JEFA, Role.CHIEF);
        crear(REDACTOR, Role.AUTHOR);
    }

    private void crear(String email, Role role) {
        if (accounts.findByEmail(email).isPresent()) {
            return;
        }
        accounts.save(new Account(UUID.randomUUID(), email, role.name(), role,
                AccountState.ACTIVE, encoder.encode(PASSWORD), SECRET, Set.of("economia")));
    }

    private MockHttpSession sesion(String email) throws Exception {
        String totp = Totp.code(new TotpSecret(SECRET), Instant.now());
        var result = mvc.perform(post("/admin/login").with(csrf())
                        .param("email", email)
                        .param("password", PASSWORD)
                        .param("totp", totp))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String borrador(String titulo) {
        CreateDraftResult draft = createDraft.create(new CreateDraftCommand(
                UUID.randomUUID().toString(), "es", "economia", titulo,
                "Cuerpo de la noticia del lote.", "https://ejemplo.es/" + UUID.randomUUID(),
                ArticleOrigin.MANUAL));
        return draft.articleId().value().toString();
    }

    private List<String> borradores(String prefijo, int cuantos) {
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= cuantos; i++) {
            ids.add(borrador(prefijo + " número " + i));
        }
        return ids;
    }

    private static String[] enArray(List<String> ids) {
        return ids.toArray(String[]::new);
    }

    private static String comoArray(List<String> ids) {
        return String.join(",", ids);
    }

    private String estado(String id) {
        return jdbc.queryForObject("select state from article where id = ?::uuid", String.class, id);
    }

    private int publicadosDe(List<String> ids) {
        return jdbc.queryForObject(
                "select count(*) from article where id = any (string_to_array(?, ',')::uuid[])"
                        + " and state = 'PUBLISHED'",
                Integer.class, comoArray(ids));
    }

    private int asientosDe(List<String> ids, String actor) {
        return jdbc.queryForObject("""
                select count(*) from audit_log
                where action = 'ARTICLE_BATCH_PUBLISHED' and actor_id = ? and entity = 'Article'
                  and entity_id = any (string_to_array(?, ',')::text[])
                """, Integer.class, actor, comoArray(ids));
    }

    // ---------- aceptación ----------

    @Test
    @DisplayName("20 borradores → 20 publicados, portada con las piezas y 20 asientos con el mismo actor")
    void veinteBorradoresALaPortada() throws Exception {
        List<String> ids = borradores("Lote aceptacion", 20);
        MockHttpSession session = sesion(JEFA);

        mvc.perform(post(LOTE).with(csrf()).session(session).param("ids", enArray(ids)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeExists("resumenLote"));

        assertThat(publicadosDe(ids)).isEqualTo(20);
        assertThat(asientosDe(ids, JEFA)).isEqualTo(20);

        Integer distintos = jdbc.queryForObject("""
                select count(distinct entity_id) from audit_log
                where action = 'ARTICLE_BATCH_PUBLISHED' and actor_id = ?
                  and entity_id = any (string_to_array(?, ',')::text[])
                """, Integer.class, JEFA, comoArray(ids));
        assertThat(distintos).as("los ids afectados, no un contador").isEqualTo(20);

        // la portada los muestra
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lote aceptacion número 1")));

        // y el resumen le cuenta al editor qué ha pasado (misma sesión: es un flash)
        mvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("20 publicados")));
    }

    @Test
    @DisplayName("El resumen separa publicados de omitidos y explica el motivo")
    void resumenConMotivos() throws Exception {
        String bueno = borrador("Lote mixto publicable");
        String yaPublicado = borrador("Lote mixto ya publicado");
        jdbc.update("update article set state = 'PUBLISHED', published_at = now() where id = ?::uuid",
                yaPublicado);
        MockHttpSession session = sesion(JEFA);

        mvc.perform(post(LOTE).with(csrf()).session(session)
                        .param("ids", bueno, yaPublicado,
                                "00000000-0000-0000-0000-000000000000", "no-es-un-uuid"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("resumenLote"));

        assertThat(estado(bueno)).isEqualTo("PUBLISHED");
        assertThat(asientosDe(List.of(bueno), JEFA)).isEqualTo(1);

        String html = mvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html)
                .contains("omitidos")
                .contains("El artículo ya está publicado")
                .contains("No existe el artículo")
                .contains("Identificador no válido");
    }

    @Test
    @DisplayName("«Seleccionar todo de la página» publica lo que la bandeja está mostrando")
    void todaLaPagina() throws Exception {
        List<String> ids = borradores("Lote pagina", 3);
        MockHttpSession session = sesion(JEFA);

        mvc.perform(post(LOTE).with(csrf()).session(session)
                        .param("todos", "pagina")
                        .param("q", "Lote pagina"))
                .andExpect(status().is3xxRedirection());

        assertThat(publicadosDe(ids)).isEqualTo(3);
    }

    @Test
    @DisplayName("Sin sesión se redirige al login y no se publica nada")
    void sinSesion() throws Exception {
        String id = borrador("Lote sin sesion");

        mvc.perform(post(LOTE).with(csrf()).param("ids", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        assertThat(estado(id)).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("Sin token CSRF se rechaza (403) y no se publica nada")
    void sinCsrf() throws Exception {
        String id = borrador("Lote sin csrf");

        mvc.perform(post(LOTE).session(sesion(JEFA)).param("ids", id))
                .andExpect(status().isForbidden());

        assertThat(estado(id)).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("Un AUTHOR no puede publicar en lote: 403 y ni un artículo cambia de estado")
    void autorNoPublica() throws Exception {
        List<String> ids = borradores("Lote de autor", 2);

        mvc.perform(post(LOTE).with(csrf()).session(sesion(REDACTOR)).param("ids", enArray(ids)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Solo CHIEF o ADMIN pueden publicar")));

        for (String id : ids) {
            assertThat(estado(id)).isEqualTo("DRAFT");
        }
        assertThat(asientosDe(ids, REDACTOR)).isZero();
    }

    @Test
    @DisplayName("Sin selección no se publica nada y se avisa")
    void sinSeleccion() throws Exception {
        MockHttpSession session = sesion(JEFA);

        mvc.perform(post(LOTE).with(csrf()).session(session))
                .andExpect(status().is3xxRedirection());

        String html = mvc.perform(get("/admin").session(session))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("No hay nada seleccionado");
    }
}
