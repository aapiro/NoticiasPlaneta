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

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F8 — la página del generador existe, se renderiza y explica el aborto sin material.
 *
 * <p>Sin proveedor de IA en esta máquina no se puede probar la generación completa desde
 * el panel; lo que se prueba aquí es la entrada (formulario, sección, CSRF) y que un tema
 * sin cobertura no rompe: devuelve el motivo en pantalla.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("F8 · Aceptación del panel: generador de borradores")
class AdminGeneradorAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-gen-test");
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
    }

    @Test
    @DisplayName("El formulario se renderiza con las secciones del portal")
    void renderizaFormulario() throws Exception {
        String html = mvc.perform(get("/admin/generador").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Generador de borradores", "Tema o titular", "Economía");
    }

    @Test
    @DisplayName("Un tema sin cobertura no rompe: la página explica que no hay material")
    void sinMaterialLoExplica() throws Exception {
        String html = mvc.perform(post("/admin/generador").session(sesion).with(csrf())
                        .param("tema", "fusión nuclear de helio en Marte")
                        .param("seccion", "economia"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("sin_material", "No hay ninguna pieza ingerida");
    }

    @Test
    @DisplayName("Sin sesión no se entra al generador")
    void sinSesionNoEntra() throws Exception {
        mvc.perform(get("/admin/generador"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/admin/login"));
    }
}
