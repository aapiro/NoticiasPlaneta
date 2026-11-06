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
import com.noticiasplaneta.news.application.port.in.TransitionArticleUseCase;
import com.noticiasplaneta.news.application.port.in.TransitionCommand;
import com.noticiasplaneta.shared.domain.Actor;
import com.noticiasplaneta.news.domain.ArticleOrigin;
import com.noticiasplaneta.news.domain.ArticleState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("F13 · Aceptación: panel editorial")
class PanelEditorialAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-panel-test");
        registry.add("security.api-key", () -> "clave-test");
        // el panel no debe bootstrapear cuentas en tests: las crea el propio test
        registry.add("admin.bootstrap.email", () -> "");
    }

    private static final String SECRET = "JBSWY3DPEHPK3PXP";
    private static final String PASSWORD = "contrasena-de-prueba-1234";

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accounts;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    CreateDraftFromItemUseCase createDraft;

    @Autowired
    TransitionArticleUseCase transicion;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void crearCuentas() {
        crear("jefa@noticiasplaneta.com", Role.CHIEF);
        crear("redactor@noticiasplaneta.com", Role.AUTHOR);
    }

    private void crear(String email, Role role) {
        if (accounts.findByEmail(email).isPresent()) {
            return;
        }
        accounts.save(new Account(UUID.randomUUID(), email, role.name(), role,
                AccountState.ACTIVE, encoder.encode(PASSWORD), SECRET, Set.of("economia")));
    }


    private static org.springframework.test.web.servlet.request.RequestPostProcessor conSesion(MockHttpSession s) {
        return request -> {
            request.setSession(s);
            return request;
        };
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
                "Cuerpo de la noticia para el panel.", "https://ejemplo.es/p", ArticleOrigin.MANUAL));
        return draft.articleId().value().toString();
    }

    private void publicar(String id) {
        Actor chief = Actor.human("u-chief", Set.of("CHIEF"), Set.of("economia"));
        for (ArticleState state : new ArticleState[]{ArticleState.IN_REVIEW, ArticleState.APPROVED,
                ArticleState.SCHEDULED, ArticleState.PUBLISHED}) {
            transicion.transition(new TransitionCommand(
                    com.noticiasplaneta.news.domain.ArticleId.of(id), state, chief, null));
        }
    }

    // ---------- escenarios de la spec ----------

    @Test
    @DisplayName("Sin sesión, /admin redirige al login")
    void sinSesion() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    @DisplayName("TOTP incorrecto no crea sesión")
    void totpIncorrecto() throws Exception {
        var result = mvc.perform(post("/admin/login").with(csrf())
                        .param("email", "jefa@noticiasplaneta.com")
                        .param("password", PASSWORD)
                        .param("totp", "000000"))
                .andExpect(redirectedUrl("/admin/login?error=bad_totp"))
                .andReturn();

        org.assertj.core.api.Assertions
                .assertThat(result.getRequest().getSession(false))
                .as("no debe haber sesión autenticada")
                .isNull();
    }

    @Test
    @DisplayName("Contraseña correcta y TOTP válido: entra y ve la bandeja")
    void entra() throws Exception {
        borrador("Titular para la bandeja del panel editorial");
        MockHttpSession session = sesion("jefa@noticiasplaneta.com");

        mvc.perform(get("/admin").with(conSesion(session)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers
                        .containsString("Titular para la bandeja del panel editorial")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DRAFT")));
    }

    @Test
    @DisplayName("Un AUTHOR no puede publicar desde el panel (403) y el estado no cambia")
    void autorNoPublica() throws Exception {
        String id = borrador("Noticia que un autor intenta publicar sin permiso");
        // la jefa la deja lista para publicar; el último paso lo intenta el redactor
        Actor chief = Actor.human("u-chief", Set.of("CHIEF"), Set.of("economia"));
        for (ArticleState state : new ArticleState[]{ArticleState.IN_REVIEW,
                ArticleState.APPROVED, ArticleState.SCHEDULED}) {
            transicion.transition(new TransitionCommand(
                    com.noticiasplaneta.news.domain.ArticleId.of(id), state, chief, null));
        }

        MockHttpSession session = sesion("redactor@noticiasplaneta.com");
        mvc.perform(post("/admin/articulos/" + id + "/transicion").with(conSesion(session)).with(csrf())
                        .param("destino", "PUBLISHED"))
                .andExpect(status().isForbidden());

        String estado = jdbc.queryForObject("select state from article where id = ?::uuid", String.class, id);
        org.assertj.core.api.Assertions.assertThat(estado).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("El editor de un artículo publicado avisa de que el cambio se ve al instante")
    void avisoRevision() throws Exception {
        String id = borrador("Noticia ya publicada que se va a revisar");
        publicar(id);

        mvc.perform(get("/admin/articulos/" + id).with(conSesion(sesion("jefa@noticiasplaneta.com"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("se ve al instante")));
    }

    @Test
    @DisplayName("CHIEF publica desde el panel y la acción queda auditada")
    void publicaDesdeElPanel() throws Exception {
        String id = borrador("Noticia que se publica desde el panel");
        MockHttpSession session = sesion("jefa@noticiasplaneta.com");

        for (String destino : new String[]{"IN_REVIEW", "APPROVED", "SCHEDULED", "PUBLISHED"}) {
            mvc.perform(post("/admin/articulos/" + id + "/transicion").with(conSesion(session)).with(csrf())
                            .param("destino", destino))
                    .andExpect(status().is3xxRedirection());
        }

        String auditados = jdbc.queryForObject(
                "select count(*)::text from audit_log where entity_id = ? and actor_id = ?",
                String.class, id, "jefa@noticiasplaneta.com");
        org.assertj.core.api.Assertions.assertThat(auditados).isEqualTo("4");
    }

    @Test
    @DisplayName("Retirar sin motivo se rechaza con 422 y el artículo sigue publicado")
    void retiradaSinMotivo() throws Exception {
        String id = borrador("Noticia que se intenta retirar sin motivo");
        publicar(id);

        mvc.perform(post("/admin/articulos/" + id + "/retirar")
                        .with(conSesion(sesion("jefa@noticiasplaneta.com"))).with(csrf())
                        .param("tipo", "RETIRADA")
                        .param("motivo", "corto"))
                .andExpect(status().isUnprocessableEntity());

        String estado = jdbc.queryForObject("select state from article where id = ?::uuid", String.class, id);
        org.assertj.core.api.Assertions.assertThat(estado).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("POST sin token CSRF se rechaza")
    void postSinTokenCsrf() throws Exception {
        String id = borrador("Noticia con POST sin CSRF");
        mvc.perform(post("/admin/articulos/" + id + "/transicion")
                        .with(conSesion(sesion("jefa@noticiasplaneta.com")))
                        .param("destino", "IN_REVIEW"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Editar el titular y el cuerpo desde el panel queda auditado")
    void edita() throws Exception {
        String id = borrador("Titular viejo que se va a corregir");
        MockHttpSession session = sesion("jefa@noticiasplaneta.com");

        mvc.perform(post("/admin/articulos/" + id + "/editar").with(conSesion(session)).with(csrf())
                        .param("title", "Titular nuevo, ya corregido")
                        .param("body", "Cuerpo reescrito por la redactora jefa.")
                        .param("revision", "1"))
                .andExpect(status().is3xxRedirection());

        var fila = jdbc.queryForMap("select title, body, revision_count from article where id = ?::uuid", id);
        org.assertj.core.api.Assertions.assertThat(fila.get("title")).isEqualTo("Titular nuevo, ya corregido");
        org.assertj.core.api.Assertions.assertThat(fila.get("body")).isEqualTo("Cuerpo reescrito por la redactora jefa.");
        org.assertj.core.api.Assertions.assertThat(fila.get("revision_count")).isEqualTo(2);

        String auditados = jdbc.queryForObject(
                "select count(*)::text from audit_log where entity_id = ? and action = 'ARTICLE_EDITED'",
                String.class, id);
        org.assertj.core.api.Assertions.assertThat(auditados).isEqualTo("1");
    }

    @Test
    @DisplayName("Editar con una versión vieja se rechaza con 409 y no pisa el texto")
    void conflictoDeVersion() throws Exception {
        String id = borrador("Noticia que dos personas editan a la vez");
        MockHttpSession session = sesion("jefa@noticiasplaneta.com");

        // primera edición: sube la versión a 2
        mvc.perform(post("/admin/articulos/" + id + "/editar").with(conSesion(session)).with(csrf())
                        .param("title", "Primera edición")
                        .param("body", "Texto de la primera edición.")
                        .param("revision", "1"))
                .andExpect(status().is3xxRedirection());

        // segunda edición con la versión vieja: es la otra pestaña que se quedó atrás
        mvc.perform(post("/admin/articulos/" + id + "/editar").with(conSesion(session)).with(csrf())
                        .param("title", "Segunda edición que llega tarde")
                        .param("body", "Texto de la segunda edición.")
                        .param("revision", "1"))
                .andExpect(status().isConflict());

        String titulo = jdbc.queryForObject("select title from article where id = ?::uuid", String.class, id);
        org.assertj.core.api.Assertions.assertThat(titulo).isEqualTo("Primera edición");
    }

    @Test
    @DisplayName("Editar sin cuerpo se rechaza y no guarda nada")
    void editaSinCuerpo() throws Exception {
        String id = borrador("Noticia a la que se le borra el cuerpo");
        MockHttpSession session = sesion("jefa@noticiasplaneta.com");

        mvc.perform(post("/admin/articulos/" + id + "/editar").with(conSesion(session)).with(csrf())
                        .param("title", "Titular válido")
                        .param("body", "   ")
                        .param("revision", "1"))
                .andExpect(status().isUnprocessableEntity());

        String titulo = jdbc.queryForObject("select title from article where id = ?::uuid", String.class, id);
        org.assertj.core.api.Assertions.assertThat(titulo).isEqualTo("Noticia a la que se le borra el cuerpo");
    }

    @Test
    @DisplayName("Editar un artículo publicado refresca lo que ve el lector")
    void editaPublicado() throws Exception {
        String id = borrador("Noticia publicada cuyo titular se reescribe");
        publicar(id);
        MockHttpSession session = sesion("jefa@noticiasplaneta.com");

        mvc.perform(post("/admin/articulos/" + id + "/editar").with(conSesion(session)).with(csrf())
                        .param("title", "Titular reescrito después de publicar")
                        .param("body", "Cuerpo actualizado con el dato nuevo.")
                        .param("revision", "1"))
                .andExpect(status().is3xxRedirection());

        String slug = jdbc.queryForObject("select slug from article where id = ?::uuid", String.class, id);
        mvc.perform(get("/economia/" + slug))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers
                        .containsString("Titular reescrito después de publicar")));
    }
}
