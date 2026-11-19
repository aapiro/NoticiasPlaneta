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

import java.nio.file.Files;
import java.nio.file.Path;
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
@DisplayName("ADR-011 F1 · Aceptación: panel de capacidades")
class PanelCapacidadesAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-caps-test");
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
                    AccountState.ACTIVE, encoder.encode(PASSWORD), SECRET, Set.of("economia")));
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

        jdbc.update("update feature_flag set mode = 'OFF', updated_by = null, updated_at = now()");
    }

    private static int veces(String html, String aguja) {
        return html.split(java.util.regex.Pattern.quote(aguja), -1).length - 1;
    }

    @Test
    @DisplayName("Renderiza las seis capacidades, enciende una y lo deja persistido")
    void renderizaYEnciende() throws Exception {
        String antes = mvc.perform(get("/admin/capacidades").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Capacidades de IA")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ENRICH_TAGS")))
                .andReturn().getResponse().getContentAsString();

        Files.writeString(Path.of("/tmp/panel-capacidades-antes.html"), antes);

        assertThat(veces(antes, ">apagada<"))
                .as("las seis deben salir apagadas al principio")
                .isEqualTo(6);
        assertThat(veces(antes, ">activa<")).isZero();
        assertThat(antes).contains("SEARCH_SEMANTIC", "ENRICH_ENTITIES", "ENRICH_SUMMARY",
                "ARTICLE_RELATED", "PUBLISH_AUTO", "HYBRID");

        mvc.perform(post("/admin/capacidades").session(sesion).with(csrf())
                        .param("capability", "ENRICH_TAGS")
                        .param("mode", "EXTERNAL"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/capacidades?ok=ENRICH_TAGS"));

        assertThat(jdbc.queryForObject("select mode from feature_flag where capability = 'ENRICH_TAGS'", String.class))
                .isEqualTo("EXTERNAL");
        assertThat(jdbc.queryForObject("select updated_by from feature_flag where capability = 'ENRICH_TAGS'", String.class))
                .isEqualTo(EMAIL);

        String despues = mvc.perform(get("/admin/capacidades").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Files.writeString(Path.of("/tmp/panel-capacidades-despues.html"), despues);

        assertThat(veces(despues, ">activa<")).isEqualTo(1);
        assertThat(veces(despues, ">apagada<")).isEqualTo(5);
        assertThat(despues).contains("EXTERNAL");
    }

    @Test
    @DisplayName("Un modo inventado no se escribe")
    void modoInvalidoRechazado() throws Exception {
        mvc.perform(post("/admin/capacidades").session(sesion).with(csrf())
                        .param("capability", "ENRICH_TAGS")
                        .param("mode", "vaya-usted-a-saber"))
                .andExpect(redirectedUrl("/admin/capacidades?error=modo"));

        assertThat(jdbc.queryForObject("select mode from feature_flag where capability = 'ENRICH_TAGS'", String.class))
                .isEqualTo("OFF");
    }

    @Test
    @DisplayName("Sin sesión no se entra")
    void sinSesionNoEntra() throws Exception {
        mvc.perform(get("/admin/capacidades"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Sin token CSRF no se cambia nada")
    void sinCsrfRechaza() throws Exception {
        mvc.perform(post("/admin/capacidades").session(sesion)
                        .param("capability", "ENRICH_TAGS")
                        .param("mode", "EXTERNAL"))
                .andExpect(status().isForbidden());
    }
}
