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
        String id = idDe("ABC");
        jdbc.update("update source set state = 'DISABLED', failure_count = 4 where id = ?::uuid", id);

        mvc.perform(post("/admin/fuentes/" + id + "/estado").session(sesion).with(csrf())
                        .param("accion", "activar"))
                .andExpect(redirectedUrl("/admin/fuentes?ok=activar"));

        assertThat(estadoDe("ABC")).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "select failure_count from source where name = 'ABC'", Integer.class))
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
}
