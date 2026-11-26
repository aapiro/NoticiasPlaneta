package com.noticiasplaneta.generation.application;

import com.noticiasplaneta.ai.application.port.in.CapacidadesIaUseCase;
import com.noticiasplaneta.ai.application.port.in.PresupuestoIaUseCase;
import com.noticiasplaneta.generation.application.port.in.GenerarBorradorCommand;
import com.noticiasplaneta.generation.application.port.in.ResultadoGeneracion;
import com.noticiasplaneta.generation.application.port.out.GeneracionRepository;
import com.noticiasplaneta.generation.application.port.out.RedaccionPort;
import com.noticiasplaneta.generation.application.service.GenerarBorradorService;
import com.noticiasplaneta.generation.domain.BorradorSinVerificar;
import com.noticiasplaneta.generation.domain.ParrafoSinVerificar;
import com.noticiasplaneta.ingest.application.port.in.BuscarMaterialUseCase;
import com.noticiasplaneta.news.application.port.in.CreateDraftCommand;
import com.noticiasplaneta.news.application.port.in.CreateDraftFromItemUseCase;
import com.noticiasplaneta.news.application.port.in.CreateDraftResult;
import com.noticiasplaneta.news.application.port.in.PublicarSinRevisionUseCase;
import com.noticiasplaneta.news.application.port.in.VincularFuentesGeneradasUseCase;
import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.news.domain.Slug;
import com.noticiasplaneta.section.application.port.in.SectionAdminUseCase;
import com.noticiasplaneta.shared.domain.AuditEntry;
import com.noticiasplaneta.shared.domain.AuditPort;
import com.noticiasplaneta.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("F8 · Generador: puertas, presupuesto y trazabilidad")
class GenerarBorradorServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-26T07:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    private MaterialFake material;
    private SeccionesFake secciones;
    private RedaccionFake redaccion;
    private PresupuestoFake presupuesto;
    private CapacidadesFake capacidades;
    private BorradoresFake borradores;
    private VincularFake vincular;
    private PublicarFake publicar;
    private GeneracionesFake generaciones;
    private AuditoriaFake auditoria;
    private GenerarBorradorService servicio;

    @BeforeEach
    void setUp() {
        material = new MaterialFake();
        secciones = new SeccionesFake(seccion("economia", true, false, "NEUTRAL"));
        redaccion = new RedaccionFake();
        presupuesto = new PresupuestoFake(true);
        capacidades = new CapacidadesFake();
        borradores = new BorradoresFake();
        vincular = new VincularFake();
        publicar = new PublicarFake();
        generaciones = new GeneracionesFake();
        auditoria = new AuditoriaFake();
        servicio = new GenerarBorradorService(material, secciones, redaccion, presupuesto, capacidades,
                borradores, vincular, publicar, generaciones, auditoria, RELOJ);

        material.items = List.of(
                new BuscarMaterialUseCase.Material("i1", "Diario A", "https://a.example/sequia",
                        AHORA.minusSeconds(3600), "Sau al 18 %",
                        "La capacidad del embalse de Sau ha bajado al 18 % este mes, según la agencia "
                                + "catalana del agua, y afecta al abastecimiento de la comarca."),
                new BuscarMaterialUseCase.Material("i2", "Diario A", "https://a.example/sequia-2",
                        AHORA.minusSeconds(7200), "Restricciones en el Segre",
                        "Las restricciones en el Segre afectan al regadío desde el lunes, con recortes "
                                + "del 30 % en las dotaciones previstas para esta campaña."),
                new BuscarMaterialUseCase.Material("i3", "Diario B", "https://b.example/sequia",
                        AHORA.minusSeconds(1800), "El Govern descarta cortes",
                        "El Govern descartó cortes de agua por ahora, con las reservas de las cuencas "
                                + "internas al 19 %, y pidió prudencia a los ayuntamientos."),
                new BuscarMaterialUseCase.Material("i4", "Diario C", "https://c.example/sequia",
                        AHORA.minusSeconds(5400), "Los embalses siguen bajando",
                        "Los embalses de la cuenca del Ebro siguen bajando y el ministerio revisará las "
                                + "medidas el próximo mes, según fuentes del departamento."));
    }

    @Test
    @DisplayName("Con cobertura real crea un borrador en DRAFT, con fuentes y trazabilidad")
    void creaBorradorConFuentes() {
        ResultadoGeneracion r = servicio.generar(GenerarBorradorCommand.humano("sequía embalses", "economia", "u1"));

        assertThat(r.estado()).isEqualTo(ResultadoGeneracion.Estado.BORRADOR);
        assertThat(r.publicado()).isFalse();
        assertThat(r.titular()).isNotBlank();
        assertThat(r.cuerpo()).contains("Texto generado con IA a partir de");
        assertThat(r.fuentes()).isNotEmpty();
        assertThat(r.parrafos()).isNotEmpty();

        assertThat(borradores.creados).hasSize(1);
        assertThat(borradores.creados.get(0).origin()).isEqualTo(com.noticiasplaneta.news.domain.ArticleOrigin.AGENT);
        assertThat(borradores.creados.get(0).ingestItemId()).isNull();
        assertThat(presupuesto.reservas).isEqualTo(1);
        assertThat(publicar.llamadas).isZero();
        assertThat(vincular.urls).hasSize(r.fuentes().size());
        assertThat(generaciones.guardados).hasSize(1);
        assertThat(generaciones.guardados.get(0).promptVersion()).isEqualTo("generar-v1");
        assertThat(auditoria.entradas).hasSize(1);
        assertThat(auditoria.entradas.get(0).action()).isEqualTo("ARTICLE_GENERATED_DRAFT");
    }

    @Test
    @DisplayName("Con una sola fuente independiente aborta sin gastar presupuesto ni llamar al modelo")
    void abortaConUnaSolaFuente() {
        material.items = material.items.stream().filter(m -> m.url().contains("a.example")).toList();

        ResultadoGeneracion r = servicio.generar(GenerarBorradorCommand.humano("sequía embalses", "economia", "u1"));

        assertThat(r.estado()).isEqualTo(ResultadoGeneracion.Estado.ABORTADO);
        assertThat(r.motivo()).isEqualTo("menos_de_2_fuentes_independientes");
        assertThat(r.explicacion()).contains("fuente(s) independiente(s)");
        assertThat(r.articleId()).isNull();
        assertThat(redaccion.llamadas).isZero();
        assertThat(presupuesto.reservas).isZero();
        assertThat(borradores.creados).isEmpty();
    }

    @Test
    @DisplayName("Sin material no hay pieza y se dice")
    void abortaSinMaterial() {
        material.items = List.of();

        ResultadoGeneracion r = servicio.generar(GenerarBorradorCommand.humano("sequía embalses", "economia", "u1"));

        assertThat(r.estado()).isEqualTo(ResultadoGeneracion.Estado.ABORTADO);
        assertThat(r.motivo()).isEqualTo("sin_material");
        assertThat(r.explicacion()).contains("No hay ninguna pieza ingerida");
        assertThat(redaccion.llamadas).isZero();
    }

    @Test
    @DisplayName("Sin presupuesto no se llama al modelo")
    void abortaSinPresupuesto() {
        presupuesto.concedido = false;

        ResultadoGeneracion r = servicio.generar(GenerarBorradorCommand.humano("sequía embalses", "economia", "u1"));

        assertThat(r.motivo()).isEqualTo("presupuesto_agotado");
        assertThat(redaccion.llamadas).isZero();
        assertThat(borradores.creados).isEmpty();
    }

    @Test
    @DisplayName("Sin proveedor configurado lo dice con un motivo propio, no con un borrador vacío")
    void abortaSinProveedor() {
        redaccion.fallo = new DomainException("AI_NOT_CONFIGURED", "falta la clave");

        ResultadoGeneracion r = servicio.generar(GenerarBorradorCommand.humano("sequía embalses", "economia", "u1"));

        assertThat(r.motivo()).isEqualTo("ia_no_configurada");
        assertThat(r.explicacion()).contains("No hay proveedor de IA configurado");
        assertThat(borradores.creados).isEmpty();
    }

    @Test
    @DisplayName("Un dato inventado por el modelo no llega al borrador")
    void descartaDatosInventados() {
        redaccion.inventarCifra = true;

        ResultadoGeneracion r = servicio.generar(GenerarBorradorCommand.humano("sequía embalses", "economia", "u1"));

        assertThat(r.estado()).isEqualTo(ResultadoGeneracion.Estado.ABORTADO);
        assertThat(r.motivo()).isEqualTo("sin_material_verificable");
        assertThat(borradores.creados).isEmpty();
    }

    @Test
    @DisplayName("Un titular con cifra inventada aborta la pieza entera")
    void abortaConTitularInventado() {
        redaccion.titularInventado = true;

        ResultadoGeneracion r = servicio.generar(GenerarBorradorCommand.humano("sequía embalses", "economia", "u1"));

        assertThat(r.estado()).isEqualTo(ResultadoGeneracion.Estado.ABORTADO);
        assertThat(r.motivo()).isEqualTo("titular_con_dato_sin_fuente");
        assertThat(borradores.creados).isEmpty();
    }

    @Test
    @DisplayName("Sección inactiva: no recibe piezas nuevas")
    void abortaSeccionInactiva() {
        secciones.lista = List.of(seccion("economia", false, false, "NEUTRAL"));

        ResultadoGeneracion r = servicio.generar(GenerarBorradorCommand.humano("sequía embalses", "economia", "u1"));

        assertThat(r.motivo()).isEqualTo("seccion_inactiva");
        assertThat(material.consultas).isEmpty();
    }

    @Test
    @DisplayName("PUBLISH_AUTO + sección automática + humano delante: publica")
    void publicaConPublishAuto() {
        secciones.lista = List.of(seccion("economia", true, true, "NEUTRAL"));
        capacidades.mapa.put(GenerarBorradorService.CAPACIDAD_PUBLISH_AUTO, true);

        ResultadoGeneracion r = servicio.generar(GenerarBorradorCommand.humano("sequía embalses", "economia", "u1"));

        assertThat(r.estado()).isEqualTo(ResultadoGeneracion.Estado.PUBLICADO);
        assertThat(r.publicado()).isTrue();
        assertThat(publicar.llamadas).isEqualTo(1);
        assertThat(r.cuerpo()).contains("PUBLISH_AUTO");
        assertThat(auditoria.entradas.get(0).action()).isEqualTo("ARTICLE_AUTO_PUBLISHED");
    }

    @Test
    @DisplayName("Un agente nunca publica, aunque la sección sea automática")
    void agenteNoPublica() {
        secciones.lista = List.of(seccion("economia", true, true, "NEUTRAL"));
        capacidades.mapa.put(GenerarBorradorService.CAPACIDAD_PUBLISH_AUTO, true);

        ResultadoGeneracion r = servicio.generar(GenerarBorradorCommand.agente("sequía embalses", "economia", "mcp"));

        assertThat(r.estado()).isEqualTo(ResultadoGeneracion.Estado.BORRADOR);
        assertThat(publicar.llamadas).isZero();
    }

    @Test
    @DisplayName("Con la capacidad PUBLISH_AUTO apagada no publica aunque la sección sea automática")
    void capacidadApagadaNoPublica() {
        secciones.lista = List.of(seccion("economia", true, true, "NEUTRAL"));
        capacidades.mapa.put(GenerarBorradorService.CAPACIDAD_PUBLISH_AUTO, false);

        ResultadoGeneracion r = servicio.generar(GenerarBorradorCommand.humano("sequía embalses", "economia", "u1"));

        assertThat(r.estado()).isEqualTo(ResultadoGeneracion.Estado.BORRADOR);
        assertThat(publicar.llamadas).isZero();
    }

    @Test
    @DisplayName("Se pide el material con el tema y se le pasan el enfoque de la sección al modelo")
    void pasaElEnfoqueDeLaSeccion() {
        secciones.lista = List.of(seccion("economia", true, false, "IZQUIERDA"));

        servicio.generar(GenerarBorradorCommand.humano("sequía embalses", "economia", "u1"));

        assertThat(material.consultas).containsExactly("sequía embalses");
        assertThat(redaccion.ultimaPeticion.enfoque()).isEqualTo("IZQUIERDA");
        assertThat(redaccion.ultimaPeticion.instruccionEnfoque()).contains("derechos sociales")
                .contains("no inventes");
    }

    private static SectionAdminUseCase.SectionView seccion(String slug, boolean activa,
                                                           boolean autoPublica, String enfoque) {
        return new SectionAdminUseCase.SectionView(slug, "Economía", "descripción", activa, 10, 3,
                enfoque, autoPublica, 0);
    }

    // --- dobles -------------------------------------------------------------------

    static class MaterialFake implements BuscarMaterialUseCase {
        List<Material> items = new ArrayList<>();
        List<String> consultas = new ArrayList<>();

        @Override
        public List<Material> buscar(String consulta, int limite) {
            consultas.add(consulta);
            return items;
        }
    }

    static class SeccionesFake implements SectionAdminUseCase {
        List<SectionView> lista;

        SeccionesFake(SectionView... vistas) {
            lista = List.of(vistas);
        }

        @Override
        public List<SectionView> sections() {
            return lista;
        }

        @Override
        public List<String> biases() {
            return List.of("IZQUIERDA", "DERECHA", "CENTRO", "NEUTRAL");
        }

        @Override
        public boolean setActive(String slug, boolean activa) {
            return false;
        }

        @Override
        public boolean setBias(String slug, String bias) {
            return false;
        }

        @Override
        public boolean setAutoPublica(String slug, boolean autoPublica) {
            return false;
        }

        @Override
        public boolean setAutoLimite(String slug, int limiteDiario) {
            return false;
        }

        @Override
        public boolean update(String slug, boolean activa, String bias, boolean autoPublica) {
            return false;
        }
    }

    static class RedaccionFake implements RedaccionPort {
        int llamadas;
        RuntimeException fallo;
        boolean inventarCifra;
        boolean titularInventado;
        Peticion ultimaPeticion;

        @Override
        public Resultado redactar(Peticion peticion) {
            llamadas++;
            ultimaPeticion = peticion;
            if (fallo != null) {
                throw fallo;
            }
            List<ParrafoSinVerificar> parrafos = new ArrayList<>();
            for (Fuente fuente : peticion.fuentes()) {
                String texto = fuente.texto();
                if (inventarCifra) {
                    texto = texto + " Según el informe, la pérdida llega al 77 % del total.";
                }
                parrafos.add(new ParrafoSinVerificar(texto, List.of(fuente.ref())));
            }
            String titular = titularInventado
                    ? "La sequía se agrava: 77 % menos de agua embalsada"
                    : "La sequía deja Sau al 18 % de su capacidad";
            return new Resultado(new BorradorSinVerificar(titular, parrafos),
                    "deepseek-flash", "generar-v1", "deepseek", 1_500);
        }
    }

    static class PresupuestoFake implements PresupuestoIaUseCase {
        boolean concedido;
        int reservas;

        PresupuestoFake(boolean concedido) {
            this.concedido = concedido;
        }

        @Override
        public boolean reservar(long costeMicros) {
            reservas++;
            return concedido;
        }
    }

    static class CapacidadesFake implements CapacidadesIaUseCase {
        final java.util.Map<String, Boolean> mapa = new java.util.HashMap<>();

        @Override
        public boolean activa(String capacidad) {
            return mapa.getOrDefault(capacidad, false);
        }
    }

    static class BorradoresFake implements CreateDraftFromItemUseCase {
        final List<CreateDraftCommand> creados = new ArrayList<>();

        @Override
        public CreateDraftResult create(CreateDraftCommand command) {
            creados.add(command);
            return new CreateDraftResult(ArticleId.newId(), Slug.fromTitle(command.title()), true);
        }
    }

    static class VincularFake implements VincularFuentesGeneradasUseCase {
        List<String> urls = new ArrayList<>();

        @Override
        public int vincular(String articleId, List<String> urls) {
            this.urls = List.copyOf(urls);
            return (int) urls.stream().map(u -> u.replaceAll("https?://([^/]+).*", "$1")).distinct().count();
        }
    }

    static class PublicarFake implements PublicarSinRevisionUseCase {
        int llamadas;

        @Override
        public boolean publicar(String articleId, String motivo) {
            llamadas++;
            return true;
        }
    }

    static class GeneracionesFake implements GeneracionRepository {
        final List<Registro> guardados = new ArrayList<>();

        @Override
        public void save(Registro registro) {
            guardados.add(registro);
        }

        @Override
        public Optional<Registro> findByArticleId(String articleId) {
            return guardados.stream().filter(r -> r.articleId().equals(articleId)).findFirst();
        }
    }

    static class AuditoriaFake implements AuditPort {
        final List<AuditEntry> entradas = new ArrayList<>();

        @Override
        public void record(AuditEntry entry) {
            entradas.add(entry);
        }
    }
}
