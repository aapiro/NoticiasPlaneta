package com.noticiasplaneta.generation.application.service;

import com.noticiasplaneta.ai.application.port.in.CapacidadesIaUseCase;
import com.noticiasplaneta.ai.application.port.in.PresupuestoIaUseCase;
import com.noticiasplaneta.generation.application.port.in.GenerarBorradorCommand;
import com.noticiasplaneta.generation.application.port.in.GenerarBorradorUseCase;
import com.noticiasplaneta.generation.application.port.in.ResultadoGeneracion;
import com.noticiasplaneta.generation.application.port.out.GeneracionRepository;
import com.noticiasplaneta.generation.application.port.out.RedaccionPort;
import com.noticiasplaneta.generation.domain.BorradorVerificado;
import com.noticiasplaneta.generation.domain.Descarte;
import com.noticiasplaneta.generation.domain.Dominio;
import com.noticiasplaneta.generation.domain.Fragmento;
import com.noticiasplaneta.generation.domain.InstruccionEnfoque;
import com.noticiasplaneta.generation.domain.ParrafoVerificado;
import com.noticiasplaneta.generation.domain.Tema;
import com.noticiasplaneta.generation.domain.Verificador;
import com.noticiasplaneta.ingest.application.port.in.BuscarMaterialUseCase;
import com.noticiasplaneta.news.application.port.in.CreateDraftCommand;
import com.noticiasplaneta.news.application.port.in.CreateDraftFromItemUseCase;
import com.noticiasplaneta.news.application.port.in.CreateDraftResult;
import com.noticiasplaneta.news.application.port.in.PublicarSinRevisionUseCase;
import com.noticiasplaneta.news.application.port.in.VincularFuentesGeneradasUseCase;
import com.noticiasplaneta.section.application.port.in.SectionAdminUseCase;
import com.noticiasplaneta.shared.domain.AuditEntry;
import com.noticiasplaneta.shared.domain.AuditPort;
import com.noticiasplaneta.shared.domain.DomainException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * F8 — de un tema a un borrador rastreable, o a una explicación de por qué no hay pieza.
 *
 * <p>El orden importa: primero se recopila y se comprueba que hay contraste suficiente,
 * después se gasta presupuesto y se llama al modelo, y al final se verifica de forma
 * determinista (cifras y citas contra los fragmentos citados). Un dato sin fuente no se
 * suaviza: se cae el párrafo.
 */
public class GenerarBorradorService implements GenerarBorradorUseCase {

    /** Estimación conservadora por generación (≈ 6 fragmentos, 800 tokens de salida). */
    public static final long COSTE_ESTIMADO_MICROS = 20_000;
    public static final int MAX_FRAGMENTOS = 6;
    public static final int MAX_POR_DOMINIO = 2;
    public static final int MIN_DOMINIOS = 2;
    public static final int MINIMO_CARACTERES = 250;
    public static final int MAX_CARACTERES_FRAGMENTO = 1_200;
    public static final String CAPACIDAD_PUBLISH_AUTO = "PUBLISH_AUTO";

    private final BuscarMaterialUseCase material;
    private final SectionAdminUseCase secciones;
    private final RedaccionPort redaccion;
    private final PresupuestoIaUseCase presupuesto;
    private final CapacidadesIaUseCase capacidades;
    private final CreateDraftFromItemUseCase crearBorrador;
    private final VincularFuentesGeneradasUseCase vincularFuentes;
    private final PublicarSinRevisionUseCase publicar;
    private final GeneracionRepository generaciones;
    private final AuditPort auditoria;
    private final Clock reloj;

    public GenerarBorradorService(BuscarMaterialUseCase material,
                                  SectionAdminUseCase secciones,
                                  RedaccionPort redaccion,
                                  PresupuestoIaUseCase presupuesto,
                                  CapacidadesIaUseCase capacidades,
                                  CreateDraftFromItemUseCase crearBorrador,
                                  VincularFuentesGeneradasUseCase vincularFuentes,
                                  PublicarSinRevisionUseCase publicar,
                                  GeneracionRepository generaciones,
                                  AuditPort auditoria,
                                  Clock reloj) {
        this.material = material;
        this.secciones = secciones;
        this.redaccion = redaccion;
        this.presupuesto = presupuesto;
        this.capacidades = capacidades;
        this.crearBorrador = crearBorrador;
        this.vincularFuentes = vincularFuentes;
        this.publicar = publicar;
        this.generaciones = generaciones;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Override
    public ResultadoGeneracion generar(GenerarBorradorCommand comando) {
        Tema tema = Tema.of(comando.tema());
        SectionAdminUseCase.SectionView seccion = seccion(comando.seccion());

        if (!seccion.activa()) {
            return ResultadoGeneracion.abortado(tema.valor(), seccion.slug(), "seccion_inactiva",
                    "La sección «" + seccion.nombre() + "» está desactivada: no recibe piezas nuevas.",
                    0, 0);
        }

        List<BuscarMaterialUseCase.Material> encontrados =
                material.buscar(tema.valor(), MAX_FRAGMENTOS * 4);
        List<Fragmento> fragmentos = seleccionar(encontrados);
        int dominios = dominios(fragmentos);

        if (encontrados.isEmpty()) {
            return ResultadoGeneracion.abortado(tema.valor(), seccion.slug(), "sin_material",
                    "No hay ninguna pieza ingerida en los últimos 30 días que hable de «"
                            + tema.valor() + "». Sin material no se redacta: inventarlo sería el fallo.",
                    0, 0);
        }
        if (dominios < MIN_DOMINIOS) {
            return ResultadoGeneracion.abortado(tema.valor(), seccion.slug(),
                    "menos_de_2_fuentes_independientes",
                    "Se han encontrado " + encontrados.size() + " fragmento(s), pero solo "
                            + dominios + " fuente(s) independiente(s) sobre «" + tema.valor()
                            + "». Con menos de " + MIN_DOMINIOS + " medios distintos no hay contraste "
                            + "suficiente: no se genera pieza.",
                    encontrados.size(), dominios);
        }

        if (!presupuesto.reservar(COSTE_ESTIMADO_MICROS)) {
            return ResultadoGeneracion.abortado(tema.valor(), seccion.slug(), "presupuesto_agotado",
                    "El presupuesto diario de IA está agotado. No se ha llamado al modelo y no hay pieza.",
                    encontrados.size(), dominios);
        }

        RedaccionPort.Resultado redactado;
        try {
            redactado = redaccion.redactar(peticion(tema, seccion, fragmentos));
        } catch (DomainException e) {
            String motivo = "AI_NOT_CONFIGURED".equals(e.code()) ? "ia_no_configurada" : "ia_error";
            return ResultadoGeneracion.abortado(tema.valor(), seccion.slug(), motivo,
                    explicacionDeIa(motivo, e.getMessage()), encontrados.size(), dominios);
        } catch (RuntimeException e) {
            return ResultadoGeneracion.abortado(tema.valor(), seccion.slug(), "ia_error",
                    explicacionDeIa("ia_error", e.getClass().getSimpleName()), encontrados.size(), dominios);
        }

        BorradorVerificado verificado = Verificador.verificar(redactado.borrador(), fragmentos);
        if (verificado.parrafos().isEmpty() || verificado.caracteres() < MINIMO_CARACTERES) {
            return ResultadoGeneracion.abortado(tema.valor(), seccion.slug(), "sin_material_verificable",
                    explicacionVerificacion("sin_material_verificable", verificado),
                    encontrados.size(), dominios);
        }
        if (verificado.titular().isBlank()) {
            String motivo = verificado.descartes().stream()
                    .map(Descarte::motivo)
                    .filter(m -> m.startsWith("titular"))
                    .filter(m -> !m.equals("titular_vacio"))
                    .findFirst()
                    .orElse("sin_material_verificable");
            return ResultadoGeneracion.abortado(tema.valor(), seccion.slug(), motivo,
                    explicacionVerificacion(motivo, verificado), encontrados.size(), dominios);
        }

        List<Fragmento> citados = fragmentos.stream()
                .filter(f -> verificado.citas().contains(f.ref()))
                .toList();
        boolean autoPublicar = comando.permitirAutoPublicacion()
                && seccion.autoPublica()
                && capacidades.activa(CAPACIDAD_PUBLISH_AUTO);
        String cuerpo = componer(verificado, citados.size(), autoPublicar);

        CreateDraftResult creado = crearBorrador.create(CreateDraftCommand.byAgent(
                null, seccion.slug(), verificado.titular(), cuerpo, citados.get(0).url()));
        String articleId = creado.articleIdText();

        int independientes = vincularFuentes.vincular(articleId,
                citados.stream().map(Fragmento::url).toList());

        generaciones.save(new GeneracionRepository.Registro(
                articleId, tema.valor(), seccion.slug(), seccion.enfoque(),
                redactado.promptVersion(), redactado.modelo(), redactado.proveedor(),
                redactado.costeMicros(), independientes, fragmentos,
                verificado.parrafos(), verificado.descartes()));

        boolean publicado = autoPublicar && publicar.publicar(articleId,
                "PUBLISH_AUTO: borrador generado desde el tema «" + tema.valor()
                        + "» en la sección " + seccion.slug());

        auditoria.record(new AuditEntry(comando.actorId(), comando.tipoActor(),
                publicado ? "ARTICLE_AUTO_PUBLISHED" : "ARTICLE_GENERATED_DRAFT",
                "Article", articleId, null,
                publicado ? "PUBLISHED" : "DRAFT", reloj.instant()));

        return new ResultadoGeneracion(
                publicado ? ResultadoGeneracion.Estado.PUBLICADO : ResultadoGeneracion.Estado.BORRADOR,
                null,
                publicado
                        ? "Borrador generado y publicado por PUBLISH_AUTO en la sección " + seccion.slug()
                        : "Borrador generado con " + citados.size() + " fuente(s). Pendiente de revisión humana.",
                articleId, creado.slugText(), seccion.slug(), verificado.titular(), cuerpo,
                seccion.enfoque(), redactado.promptVersion(), redactado.modelo(),
                redactado.proveedor(), redactado.costeMicros(), independientes, encontrados.size(),
                publicado,
                fuentes(verificado, fragmentos),
                parrafos(verificado),
                descartes(verificado));
    }

    private SectionAdminUseCase.SectionView seccion(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new DomainException("SECTION_NOT_FOUND", "Falta la sección de destino");
        }
        return secciones.sections().stream()
                .filter(s -> s.slug().equalsIgnoreCase(slug.trim()))
                .findFirst()
                .orElseThrow(() -> new DomainException("SECTION_NOT_FOUND",
                        "No existe la sección " + slug));
    }

    /**
     * Elige lo que verá el modelo: reciente primero, como mucho dos fragmentos por medio
     * (un medio no puede copar la pieza) y sin repetir titular dentro del mismo medio.
     */
    private List<Fragmento> seleccionar(List<BuscarMaterialUseCase.Material> encontrados) {
        List<BuscarMaterialUseCase.Material> orden = encontrados.stream()
                .filter(m -> m.url() != null && !m.url().isBlank())
                .filter(m -> m.texto() != null && !m.texto().isBlank())
                .sorted(Comparator.comparing(BuscarMaterialUseCase.Material::fecha,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Map<String, Integer> porDominio = new HashMap<>();
        Set<String> vistos = new HashSet<>();
        List<Fragmento> salida = new ArrayList<>();
        for (BuscarMaterialUseCase.Material m : orden) {
            String dominio = Dominio.raiz(m.url());
            if (dominio.isBlank()) {
                continue;
            }
            String huella = dominio + "|" + Verificador.normalizar(m.titular()).toLowerCase(Locale.ROOT);
            if (!vistos.add(huella)) {
                continue;
            }
            int cuantos = porDominio.merge(dominio, 1, Integer::sum);
            if (cuantos > MAX_POR_DOMINIO) {
                continue;
            }
            salida.add(new Fragmento(salida.size() + 1, m.medio(), m.url(), m.fecha(),
                    m.titular(), recorta(m.texto(), MAX_CARACTERES_FRAGMENTO)));
            if (salida.size() == MAX_FRAGMENTOS) {
                break;
            }
        }
        return List.copyOf(salida);
    }

    private static int dominios(List<Fragmento> fragmentos) {
        return (int) fragmentos.stream().map(Fragmento::dominio).filter(d -> !d.isBlank()).distinct().count();
    }

    private static RedaccionPort.Peticion peticion(Tema tema, SectionAdminUseCase.SectionView seccion,
                                                   List<Fragmento> fragmentos) {
        List<RedaccionPort.Fuente> fuentes = fragmentos.stream()
                .map(f -> new RedaccionPort.Fuente(f.ref(), f.medio(), f.url(),
                        f.fecha() == null ? "sin fecha" : f.fecha().toString(), f.titular(), f.texto()))
                .toList();
        return new RedaccionPort.Peticion(tema.valor(), seccion.slug(), seccion.enfoque(),
                InstruccionEnfoque.para(seccion.enfoque()) + " " + InstruccionEnfoque.limite(), fuentes);
    }

    private static List<ResultadoGeneracion.Fuente> fuentes(BorradorVerificado verificado,
                                                            List<Fragmento> fragmentos) {
        List<Integer> citas = verificado.citas();
        return fragmentos.stream()
                .filter(f -> citas.contains(f.ref()))
                .map(f -> new ResultadoGeneracion.Fuente(f.ref(), f.medio(), f.url(),
                        f.fecha() == null ? null : f.fecha().toString()))
                .toList();
    }

    private static List<ResultadoGeneracion.Parrafo> parrafos(BorradorVerificado verificado) {
        return verificado.parrafos().stream()
                .map(p -> new ResultadoGeneracion.Parrafo(p.texto(), p.citas()))
                .toList();
    }

    private static List<ResultadoGeneracion.Descarte> descartes(BorradorVerificado verificado) {
        return verificado.descartes().stream()
                .map(d -> new ResultadoGeneracion.Descarte(d.motivo(), d.detalle()))
                .toList();
    }

    private static String componer(BorradorVerificado verificado, int fuentes, boolean autoPublicado) {
        StringBuilder sb = new StringBuilder();
        for (ParrafoVerificado parrafo : verificado.parrafos()) {
            sb.append(parrafo.texto()).append("\n\n");
        }
        sb.append("Texto generado con IA a partir de ").append(fuentes)
                .append(fuentes == 1 ? " fuente. " : " fuentes. ");
        sb.append(autoPublicado
                ? "Publicado sin revisión humana (PUBLISH_AUTO)."
                : "Pendiente de revisión editorial.");
        return sb.toString().trim();
    }

    private static String explicacionDeIa(String motivo, String detalle) {
        if ("ia_no_configurada".equals(motivo)) {
            return "No hay proveedor de IA configurado (falta la clave de DeepSeek). "
                    + "Sin modelo no hay borrador y no se inventa uno.";
        }
        return "El proveedor de IA ha fallado (" + detalle + "). No se ha generado pieza.";
    }

    private static String explicacionVerificacion(String motivo, BorradorVerificado verificado) {
        String cifras = verificado.descartes().stream()
                .filter(d -> d.motivo().equals("dato_sin_fuente") || d.motivo().equals("cita_textual_sin_fuente"))
                .count() > 0
                ? " Se han descartado párrafos por datos o citas que no aparecen en sus fuentes."
                : "";
        return switch (motivo) {
            case "titular_con_dato_sin_fuente" -> "El titular del modelo incluía una cifra que no está en "
                    + "las fuentes. Se aborta: un titular inventado no se recorta, se descarta.";
            case "titular_con_cita_sin_fuente" -> "El titular del modelo atribuía una cita que no está en "
                    + "las fuentes. Se aborta la pieza.";
            default -> "Lo que ha devuelto el modelo no se sostiene con las fuentes: "
                    + verificado.descartes().size() + " descarte(s), "
                    + verificado.parrafos().size() + " párrafo(s) aceptado(s)." + cifras;
        };
    }

    private static String recorta(String texto, int max) {
        String limpio = Verificador.normalizar(texto);
        return limpio.length() <= max ? limpio : limpio.substring(0, max);
    }
}
