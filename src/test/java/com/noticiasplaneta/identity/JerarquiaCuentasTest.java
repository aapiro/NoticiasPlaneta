package com.noticiasplaneta.identity;

import com.noticiasplaneta.identity.application.port.in.AccountAdminUseCase;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Jerarquía · la raíz manda sobre el agente y no al revés")
class JerarquiaCuentasTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-jerarquia-test");
        registry.add("security.api-key", () -> "clave-test");
        // Las cuentas las siembra el test, no el bootstrap.
        registry.add("admin.bootstrap.email", () -> "");
        registry.add("admin.bootstrap.agent.email", () -> "");
    }

    private static final String PASS_RAIZ = "raiz-contrasena-larga-1234";
    private static final String PASS_AGENTE = "agente-contrasena-larga-5678";
    private static final String SECRETO_RAIZ = "JBSWY3DPEHPK3PXP";
    private static final String SECRETO_AGENTE = "KRSXG5CTMVRXEZLU";
    private static final String EMAIL_RAIZ = "raiz@noticiasplaneta.com";
    private static final String EMAIL_AGENTE = "agente@noticiasplaneta.com";

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accounts;

    @Autowired
    AccountAdminUseCase cuentas;

    @Autowired
    PasswordEncoder encoder;

    private UUID idAgente;

    @BeforeEach
    void sembrar() {
        var raiz = accounts.findByEmail(EMAIL_RAIZ).orElseGet(() -> accounts.save(
                new Account(UUID.randomUUID(), EMAIL_RAIZ, "Raíz", Role.ADMIN,
                        AccountState.ACTIVE, encoder.encode(PASS_RAIZ), SECRETO_RAIZ, Set.of())));

        var agente = accounts.findByEmail(EMAIL_AGENTE).orElseGet(() -> accounts.save(
                new Account(UUID.randomUUID(), EMAIL_AGENTE, "Agente de IA", Role.AGENT,
                        AccountState.ACTIVE, encoder.encode(PASS_AGENTE), SECRETO_AGENTE, Set.of())));
        idAgente = agente.id();

        // El agente puede haber quedado apagado por un test anterior.
        accounts.save(new Account(agente.id(), agente.email(), agente.displayName(),
                agente.role(), AccountState.ACTIVE, agente.passwordHash(), agente.totpSecret(),
                agente.sections()));
        accounts.save(new Account(raiz.id(), raiz.email(), raiz.displayName(),
                raiz.role(), AccountState.ACTIVE, raiz.passwordHash(), raiz.totpSecret(),
                raiz.sections()));
    }

    private MockHttpSession entrar(String email, String password, String secreto) throws Exception {
        String totp = Totp.code(new TotpSecret(secreto), Instant.now());
        var result = mvc.perform(post("/admin/login").with(csrf())
                        .param("email", email)
                        .param("password", password)
                        .param("totp", totp))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    @DisplayName("La raíz ve la pantalla de cuentas y las dos están en ella")
    void laRaizVeLaPantalla() throws Exception {
        var sesion = entrar(EMAIL_RAIZ, PASS_RAIZ, SECRETO_RAIZ);
        assertThat(sesion).as("la raíz tiene que poder entrar").isNotNull();

        mvc.perform(get("/admin/cuentas").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(EMAIL_RAIZ)))
                .andExpect(content().string(containsString(EMAIL_AGENTE)));
    }

    @Test
    @DisplayName("El agente administra contenido pero no ve la gestión de cuentas")
    void elAgenteNoVeLasCuentas() throws Exception {
        var sesion = entrar(EMAIL_AGENTE, PASS_AGENTE, SECRETO_AGENTE);
        assertThat(sesion).as("el agente tiene que poder entrar al panel").isNotNull();

        // Administra: la bandeja y las fuentes le están abiertas.
        mvc.perform(get("/admin").session(sesion)).andExpect(status().isOk());
        mvc.perform(get("/admin/fuentes").session(sesion)).andExpect(status().isOk());

        // Pero no manda sobre quién entra.
        mvc.perform(get("/admin/cuentas").session(sesion)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El chip de Cuentas no se le ofrece al agente")
    void elChipNoSaleParaElAgente() throws Exception {
        var sesionAgente = entrar(EMAIL_AGENTE, PASS_AGENTE, SECRETO_AGENTE);
        String htmlAgente = mvc.perform(get("/admin").session(sesionAgente))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(htmlAgente).doesNotContain("href=\"/admin/cuentas\"");

        var sesionRaiz = entrar(EMAIL_RAIZ, PASS_RAIZ, SECRETO_RAIZ);
        String htmlRaiz = mvc.perform(get("/admin").session(sesionRaiz))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(htmlRaiz).contains("href=\"/admin/cuentas\"");
    }

    @Test
    @DisplayName("La raíz apaga al agente y entonces el agente ya no entra")
    void laRaizApagaAlAgente() throws Exception {
        var sesion = entrar(EMAIL_RAIZ, PASS_RAIZ, SECRETO_RAIZ);

        mvc.perform(post("/admin/cuentas/" + idAgente + "/estado").session(sesion).with(csrf())
                        .param("activar", "false"))
                .andExpect(status().is3xxRedirection());

        assertThat(accounts.findAll().stream()
                .filter(a -> a.id().equals(idAgente))
                .findFirst().orElseThrow().state())
                .as("el agente queda desactivado")
                .isEqualTo(AccountState.DISABLED);

        // Y con la cuenta apagada, el login no abre sesión.
        String totp = Totp.code(new TotpSecret(SECRETO_AGENTE), Instant.now());
        mvc.perform(post("/admin/login").with(csrf())
                        .param("email", EMAIL_AGENTE)
                        .param("password", PASS_AGENTE)
                        .param("totp", totp))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getRequest().getSession(false))
                        .as("una cuenta desactivada no puede abrir sesión").isNull());
    }

    @Test
    @DisplayName("La raíz se vuelve a encender al agente después de apagarlo")
    void laRaizVuelveAEncenderAlAgente() {
        assertThat(cuentas.cambiarEstado(EMAIL_RAIZ, idAgente, false))
                .isEqualTo(AccountAdminUseCase.Resultado.HECHO);
        assertThat(cuentas.cambiarEstado(EMAIL_RAIZ, idAgente, true))
                .isEqualTo(AccountAdminUseCase.Resultado.HECHO);

        assertThat(accounts.findAll().stream()
                .filter(a -> a.id().equals(idAgente))
                .findFirst().orElseThrow().state())
                .isEqualTo(AccountState.ACTIVE);
    }

    @Test
    @DisplayName("La raíz no se puede apagar a sí misma: el panel se quedaría sin llave")
    void laRaizNoSeApaga() {
        var raiz = accounts.findByEmail(EMAIL_RAIZ).orElseThrow();

        assertThat(cuentas.cambiarEstado(EMAIL_RAIZ, raiz.id(), false))
                .isEqualTo(AccountAdminUseCase.Resultado.ES_LA_RAIZ);
        assertThat(accounts.findByEmail(EMAIL_RAIZ).orElseThrow().state())
                .as("sigue activa")
                .isEqualTo(AccountState.ACTIVE);
    }

    @Test
    @DisplayName("El agente no puede tocar cuentas aunque llame al servicio directamente")
    void elAgenteNoPuedePorServicio() {
        // Defensa en profundidad: sin pasar por la URL.
        assertThat(cuentas.cambiarEstado(EMAIL_AGENTE, idAgente, false))
                .isEqualTo(AccountAdminUseCase.Resultado.SIN_PERMISO);

        var raiz = accounts.findByEmail(EMAIL_RAIZ).orElseThrow();
        assertThat(cuentas.cambiarEstado(EMAIL_AGENTE, raiz.id(), false))
                .as("el agente no apaga a la raíz ni por servicio")
                .isEqualTo(AccountAdminUseCase.Resultado.SIN_PERMISO);
        assertThat(accounts.findByEmail(EMAIL_RAIZ).orElseThrow().state())
                .isEqualTo(AccountState.ACTIVE);
    }

    @Test
    @DisplayName("Un id que no es UUID no tumba la petición con un 500")
    void idInvalidoNoRompe() throws Exception {
        var sesion = entrar(EMAIL_RAIZ, PASS_RAIZ, SECRETO_RAIZ);
        mvc.perform(post("/admin/cuentas/no-es-un-uuid/estado").session(sesion).with(csrf())
                        .param("activar", "false"))
                .andExpect(status().is3xxRedirection());
    }
}
