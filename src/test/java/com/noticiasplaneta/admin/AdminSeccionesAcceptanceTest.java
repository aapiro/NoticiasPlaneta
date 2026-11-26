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
@DisplayName("F3 + ADR-012 · Aceptación: secciones y enfoque editorial")
class AdminSeccionesAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-sec-test");
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
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    private MockHttpSession sesion;

    @BeforeEach
    void entrar() throws Exception {
        if (accounts.findByEmail(EMAIL).isEmpty()) {
            accounts.save(new Account(UUID.randomUUID(), EMAIL, Role.CHIEF.name(), Role.CHIEF,
                    AccountState.ACTIVE, encoder.encode(PASSWORD), SECRET, Set.of("politica")));
        }
        String totp = Totp.code(new TotpSecret(SECRET), Instant.now());
        var result = mvc.perform(post("/admin/login").with(csrf())
                        .param("email", EMAIL)
                        .param("password", PASSWORD)
                        .param("totp", totp))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        sesion = (MockHttpSession) result.getRequest().getSession(false);

        jdbc.update("update section_config set activa = true, enfoque = 'NEUTRAL', auto_publica = false");
    }

    @Test
    @DisplayName("La semilla trae Tecnología y Política y el panel las renderiza")
    void renderizaSecciones() throws Exception {
        String html = mvc.perform(get("/admin/secciones").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Tecnología", "Política", "NEUTRAL");
        assertThat(jdbc.queryForObject("select count(*) from section_config", Integer.class))
                .isGreaterThanOrEqualTo(7);
    }

    @Test
    @DisplayName("Cambiar el enfoque de una sección lo persiste")
    void cambiaEnfoque() throws Exception {
        mvc.perform(post("/admin/secciones").session(sesion).with(csrf())
                        .param("slug", "tecnologia")
                        .param("activa", "1")
                        .param("enfoque", "IZQUIERDA"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/secciones?ok=tecnologia"));

        assertThat(jdbc.queryForObject(
                "select enfoque from section_config where slug = 'tecnologia'", String.class))
                .isEqualTo("IZQUIERDA");
    }

    @Test
    @DisplayName("Desmarcar el checkbox desactiva la sección")
    void desactivaSeccion() throws Exception {
        mvc.perform(post("/admin/secciones").session(sesion).with(csrf())
                        .param("slug", "deportes")
                        .param("enfoque", "NEUTRAL"))
                .andExpect(status().is3xxRedirection());

        assertThat(jdbc.queryForObject(
                "select activa from section_config where slug = 'deportes'", Boolean.class))
                .isFalse();
    }

    @Test
    @DisplayName("Una sección desconocida no rompe: avisa")
    void seccionDesconocida() throws Exception {
        mvc.perform(post("/admin/secciones").session(sesion).with(csrf())
                        .param("slug", "inexistente")
                        .param("enfoque", "NEUTRAL"))
                .andExpect(redirectedUrl("/admin/secciones?error=seccion"));
    }

    @Test
    @DisplayName("Sin sesión no se entra al panel de secciones")
    void sinSesion() throws Exception {
        mvc.perform(get("/admin/secciones")).andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Sin CSRF no se cambia nada")
    void sinCsrf() throws Exception {
        mvc.perform(post("/admin/secciones").session(sesion)
                        .param("slug", "tecnologia")
                        .param("enfoque", "DERECHA"))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject(
                "select enfoque from section_config where slug = 'tecnologia'", String.class))
                .isEqualTo("NEUTRAL");
    }
}
