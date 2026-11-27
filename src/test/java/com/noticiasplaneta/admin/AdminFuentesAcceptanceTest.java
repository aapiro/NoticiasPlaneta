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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Backlog #4 · Gestión de fuentes desde el panel")
class AdminFuentesAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-fuentes-test");
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

    @BeforeEach
    void entrar() throws Exception {
        if (accounts.findByEmail(EMAIL).isEmpty()) {
            accounts.save(new Account(UUID.randomUUID(), EMAIL, Role.CHIEF.name(), Role.CHIEF,
                    AccountState.ACTIVE, encoder.encode(PASSWORD), SECRET, Set.of("general")));
        }
        if (jdbc.queryForObject("select count(*) from source", Integer.class) == 0) {
            jdbc.update("""
                    insert into source (id, name, feed_url, state, failure_count, confianza)
                    values (?::uuid, 'El País', 'https://feeds.elpais.com/portada',
                            'ACTIVE', 0, true)""", UUID.randomUUID().toString());
            jdbc.update("""
                    insert into source (id, name, feed_url, state, failure_count, confianza)
                    values (?::uuid, 'ABC', 'https://www.abc.es/rss/portada/',
                            'ACTIVE', 0, false)""", UUID.randomUUID().toString());
        }

        String totp = Totp.code(new TotpSecret(SECRET), Instant.now());
        var result = mvc.perform(post("/admin/login").with(csrf())
                        .param("email", EMAIL)
                        .param("password", PASSWORD)
                        .param("totp", totp))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        sesion = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(sesion).as("el login debe abrir sesión").isNotNull();
    }

    private String idDe(String nombre) {
        return jdbc.queryForObject("select id::text from source where name = ?", String.class, nombre);
    }

    /** Fuente propia de cada test: compartir las sembradas hace que se pisen entre sí. */
    private String crearFuente(String nombre) {
        String id = UUID.randomUUID().toString();
        String slug = nombre.toLowerCase().replace(' ', '-');
        jdbc.update("""
                insert into source (id, name, feed_url, state, failure_count, confianza)
                values (?::uuid, ?, ?, 'ACTIVE', 0, false)""",
                id, nombre, "https://ejemplo.com/" + slug + "/rss");
        return id;
    }

    private String estadoDe(String nombre) {
        return jdbc.queryForObject("select state from source where name = ?", String.class, nombre);
    }

    @Test
    @DisplayName("Lista los medios con su feed y su estado")
    void lista() throws Exception {
        mvc.perform(get("/admin/fuentes").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Fuentes de ingesta")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("El País")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("https://feeds.elpais.com/portada")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("de confianza")));
    }

    @Test
    @DisplayName("Añade un medio y aparece en el listado")
    void anade() throws Exception {
        mvc.perform(post("/admin/fuentes").session(sesion).with(csrf())
                        .param("nombre", "La Razón")
                        .param("feedUrl", "https://www.larazon.es/rss/"))
                .andExpect(redirectedUrl("/admin/fuentes?ok=creada"));

        assertThat(estadoDe("La Razón")).isEqualTo("ACTIVE");
        mvc.perform(get("/admin/fuentes").session(sesion))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("La Razón")));
    }

    @Test
    @DisplayName("Una URL que no es http/https la rechaza el dominio")
    void urlInvalida() throws Exception {
        mvc.perform(post("/admin/fuentes").session(sesion).with(csrf())
                        .param("nombre", "Malo")
                        .param("feedUrl", "ftp://no.es.un.feed"))
                .andExpect(redirectedUrl("/admin/fuentes?error=SOURCE_URL_INVALID"));

        assertThat(jdbc.queryForObject(
                "select count(*) from source where name = 'Malo'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Desactivar no borra la fuente ni su marca de confianza")
    void desactivarConservaConfianza() throws Exception {
        mvc.perform(post("/admin/fuentes/" + idDe("El País") + "/estado").session(sesion).with(csrf())
                        .param("accion", "desactivar"))
                .andExpect(redirectedUrl("/admin/fuentes?ok=desactivar"));

        assertThat(estadoDe("El País")).isEqualTo("DISABLED");
        assertThat(jdbc.queryForObject(
                "select confianza from source where name = 'El País'", Boolean.class))
                .as("la confianza se guarda en otra capa: desactivar no debe pisarla")
                .isTrue();
    }

    @Test
    @DisplayName("Reactivar deja la fuente en ACTIVE con los fallos a cero")
    void reactivar() throws Exception {
        String id = crearFuente("ABC reactivar");
        jdbc.update("update source set state = 'DISABLED', failure_count = 4 where id = ?::uuid", id);

        mvc.perform(post("/admin/fuentes/" + id + "/estado").session(sesion).with(csrf())
                        .param("accion", "activar"))
                .andExpect(redirectedUrl("/admin/fuentes?ok=activar"));

        assertThat(jdbc.queryForObject(
                "select state from source where id = ?::uuid", String.class, id)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "select failure_count from source where id = ?::uuid", Integer.class, id))
                .as("reactivar con los fallos viejos la degradaría al primer error")
                .isZero();
    }

    @Test
    @DisplayName("Sin sesión no se ve el panel")
    void sinSesion() throws Exception {
        mvc.perform(get("/admin/fuentes")).andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Sin CSRF se rechaza el alta")
    void sinCsrf() throws Exception {
        mvc.perform(post("/admin/fuentes").session(sesion)
                        .param("nombre", "Sin CSRF")
                        .param("feedUrl", "https://ejemplo.com/rss"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Editar cambia nombre y feed")
    void editar() throws Exception {
        String id = crearFuente("ABC editar");

        mvc.perform(post("/admin/fuentes/" + id + "/editar").session(sesion).with(csrf())
                        .param("nombre", "ABC España")
                        .param("feedUrl", "https://www.abc.es/rss/2.0/espana/"))
                .andExpect(redirectedUrl("/admin/fuentes?ok=editada"));

        assertThat(jdbc.queryForObject("select name from source where id = ?::uuid", String.class, id))
                .isEqualTo("ABC España");
        assertThat(jdbc.queryForObject("select feed_url from source where id = ?::uuid", String.class, id))
                .isEqualTo("https://www.abc.es/rss/2.0/espana/");
    }

    @Test
    @DisplayName("Cambiar la URL tira el ETag viejo: si no, el feed nuevo contesta 304 para siempre")
    void editarUrlLimpiaValidadores() throws Exception {
        String id = crearFuente("ABC validadores");
        jdbc.update("update source set etag = 'W/\"viejo\"', last_modified = 'ayer', failure_count = 3 "
                + "where id = ?::uuid", id);

        mvc.perform(post("/admin/fuentes/" + id + "/editar").session(sesion).with(csrf())
                        .param("nombre", "ABC")
                        .param("feedUrl", "https://www.abc.es/rss/2.0/otra/"))
                .andExpect(redirectedUrl("/admin/fuentes?ok=editada"));

        assertThat(jdbc.queryForObject("select etag from source where id = ?::uuid", String.class, id))
                .as("un If-None-Match del feed anterior no vale para el nuevo")
                .isNull();
        assertThat(jdbc.queryForObject(
                "select failure_count from source where id = ?::uuid", Integer.class, id)).isZero();
    }

    @Test
    @DisplayName("Editar con una URL que no es http/https la rechaza el dominio")
    void editarUrlInvalida() throws Exception {
        String id = crearFuente("ABC invalida");

        mvc.perform(post("/admin/fuentes/" + id + "/editar").session(sesion).with(csrf())
                        .param("nombre", "ABC")
                        .param("feedUrl", "ftp://no.es.un.feed"))
                .andExpect(redirectedUrl("/admin/fuentes?error=SOURCE_URL_INVALID"));

        assertThat(jdbc.queryForObject("select feed_url from source where id = ?::uuid", String.class, id))
                .as("no debe quedar tocada")
                .isEqualTo("https://ejemplo.com/abc-invalida/rss");
    }

    @Test
    @DisplayName("Eliminar una fuente sin piezas la borra de verdad")
    void eliminarSinPiezas() throws Exception {
        String id = crearFuente("ABC borrar");

        mvc.perform(post("/admin/fuentes/" + id + "/eliminar").session(sesion)
                        .with(csrf()))
                .andExpect(redirectedUrl("/admin/fuentes?ok=borrada"));

        assertThat(jdbc.queryForObject(
                "select count(*) from source where id = ?::uuid", Integer.class, id)).isZero();
    }

    @Test
    @DisplayName("Eliminar una fuente con noticias dentro la desactiva, no la borra")
    void eliminarConPiezasDesactiva() throws Exception {
        String id = crearFuente("Pais con piezas");
        // Una pieza de ingesta cuelga de esta fuente: borrarla perdería el origen.
        jdbc.update("""
                insert into ingest_item (id, source_id, external_id, content_hash, title, body,
                                         canonical_url, published_at, created_at)
                values (gen_random_uuid(), ?::uuid, 'ext-1', 'hash-1', 'Titular', 'cuerpo',
                        'https://ejemplo.com/1', now(), now())""", id);

        mvc.perform(post("/admin/fuentes/" + id + "/eliminar").session(sesion)
                        .with(csrf()))
                .andExpect(redirectedUrl("/admin/fuentes?ok=desactivada-con-piezas"));

        assertThat(jdbc.queryForObject(
                "select count(*) from source where id = ?::uuid", Integer.class, id))
                .as("la fuente sigue ahí")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select state from source where id = ?::uuid", String.class, id))
                .isEqualTo("DISABLED");
        assertThat(jdbc.queryForObject(
                "select count(*) from ingest_item where source_id = ?::uuid", Integer.class, id))
                .as("la noticia ya ingerida no se toca")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Eliminar con un id que no es uuid no revienta")
    void eliminarIdInvalido() throws Exception {
        mvc.perform(post("/admin/fuentes/no-es-un-uuid/eliminar").session(sesion).with(csrf()))
                .andExpect(redirectedUrl("/admin/fuentes?error=NO_EXISTE"));
    }

    @Test
    @DisplayName("Sin sesión no se puede borrar")
    void borrarSinSesion() throws Exception {
        String id = crearFuente("ABC sin sesion");
        mvc.perform(post("/admin/fuentes/" + id + "/eliminar").with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("select count(*) from source where id = ?::uuid", Integer.class, id))
                .isEqualTo(1);
    }
}
