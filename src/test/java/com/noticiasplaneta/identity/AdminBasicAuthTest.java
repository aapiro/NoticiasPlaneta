package com.noticiasplaneta.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "admin.basic.user=portero",
        "admin.basic.password=clave-larga-de-prueba-1234"
})
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("ADR-010 · Basic auth delante del panel")
class AdminBasicAuthTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-basic-test");
        registry.add("security.api-key", () -> "clave-test");
        registry.add("admin.bootstrap.email", () -> "");
    }

    @Autowired
    MockMvc mvc;

    private static String basica(String usuario, String contrasena) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((usuario + ":" + contrasena).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Sin cabecera, el panel responde 401 y pide credenciales")
    void sinCabecera() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate",
                        org.hamcrest.Matchers.containsString("Basic realm=")));
    }

    @Test
    @DisplayName("El formulario de login tampoco se ve sin la cabecera")
    void loginProtegido() throws Exception {
        mvc.perform(get("/admin/login")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Contraseña incorrecta: 401")
    void contrasenaMala() throws Exception {
        mvc.perform(get("/admin").header("Authorization", basica("portero", "otra-cosa")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Usuario incorrecto: 401")
    void usuarioMalo() throws Exception {
        mvc.perform(get("/admin").header("Authorization", basica("intruso", "clave-larga-de-prueba-1234")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Esquema que no es Basic: 401")
    void esquemaRaro() throws Exception {
        mvc.perform(get("/admin").header("Authorization", "Bearer algo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Con las credenciales correctas se pasa al login del panel")
    void credencialesCorrectas() throws Exception {
        // 302 al login: el Basic deja pasar y entra la capa de sesión + TOTP.
        mvc.perform(get("/admin")
                        .header("Authorization", basica("portero", "clave-larga-de-prueba-1234")))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("El portal público no se ve afectado")
    void portadaPublica() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/feed.xml")).andExpect(status().isOk());
        mvc.perform(get("/robots.txt")).andExpect(status().isOk());
    }
}
