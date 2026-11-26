package com.noticiasplaneta.edicion.application.service;

import com.noticiasplaneta.edicion.application.port.in.EdicionAdminUseCase;
import com.noticiasplaneta.edicion.application.port.in.EjecucionDetalleView;
import com.noticiasplaneta.edicion.application.port.in.EjecucionView;
import com.noticiasplaneta.edicion.application.port.in.PiezaEjecucionView;
import com.noticiasplaneta.edicion.application.port.in.SeccionEstadoView;
import com.noticiasplaneta.edicion.application.port.in.TableroEdicionView;
import com.noticiasplaneta.edicion.application.port.out.EdicionStore;
import com.noticiasplaneta.edicion.domain.EdicionEstado;
import com.noticiasplaneta.ingest.application.port.in.IngestAllSourcesUseCase;
import com.noticiasplaneta.news.application.port.in.TransitionArticleUseCase;
import com.noticiasplaneta.news.application.port.in.TransitionCommand;
import com.noticiasplaneta.section.application.port.in.SectionAdminUseCase;
import com.noticiasplaneta.shared.domain.Actor;
import com.noticiasplaneta.shared.domain.AuditEntry;
import com.noticiasplaneta.shared.domain.AuditPort;
import com.noticiasplaneta.shared.domain.DomainException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * F7 · Estado editorial y control de la edición.
 *
 * <p>Cierra el círculo que abrió F1 sin depender de la terminal: ingesta, borradores desde
 * lo nuevo, selección por cupo de sección y publicación por la <strong>máquina de estados</strong>
 * de {@code news} (no por {@code update article set state=...}, que se salta el evento de
 * dominio y deja el outbox y el enriquecido sin enterarse).
 *
 * <p>Los borradores no se crean aquí: al ingerir, {@code IngestItemReceivedListener} ya deja
 * un borrador por item. Esta ejecución cuenta los que han nacido durante la ventana y
 * publica los que cumplen las reglas; duplicar esa conversión era trabajo ya hecho.
 *
 * <p>Es síncrono a propósito: la respuesta del POST es el informe. Convertirlo en cola es
 * trabajo de F6, no de un panel de estado.
 */
public class EdicionAdminService implements EdicionAdminUseCase {

    /** La hora «de hoy» del periódico, no la del servidor en UTC. */
    static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final List<String> CADENA_PUBLICACION =
            List.of("IN_REVIEW", "APPROVED", "SCHEDULED", "PUBLISHED");

    private final EdicionStore store;
    private final IngestAllSourcesUseCase ingesta;
    private final TransitionArticleUseCase transiciones;
    private final SectionAdminUseCase secciones;
    private final AuditPort audit;
    private final Clock clock;
    private final int ventanaHoras;
    private final int limite;
    private final int maxPorFuente;

    public EdicionAdminService(EdicionStore store,
                               IngestAllSourcesUseCase ingesta,
                               TransitionArticleUseCase transiciones,
                               SectionAdminUseCase secciones,
                               AuditPort audit,
                               Clock clock,
                               int ventanaHoras,
                               int limite,
                               int maxPorFuente) {
        this.store = store;
        this.ingesta = ingesta;
        this.transiciones = transiciones;
        this.secciones = secciones;
        this.audit = audit;
        this.clock = clock;
        this.ventanaHoras = ventanaHoras;
        this.limite = limite;
        this.maxPorFuente = maxPorFuente;
    }

    // ---------------------------------------------------------------- consulta

    @Override
    public TableroEdicionView tablero() {
        List<EdicionStore.EjecucionFila> filas = store.ejecuciones(10);
        List<EjecucionView> historico = filas.stream().map(this::ver).toList();

        Instant inicioDeHoy = LocalDate.now(clock.withZone(ZONA)).atStartOfDay(ZONA).toInstant();
        Map<String, Integer> hoy = store.piezasPublicadasPorSeccion(inicioDeHoy);

        List<SeccionEstadoView> estadoSecciones = new ArrayList<>();
        for (SectionAdminUseCase.SectionView s : secciones.sections()) {
            int publicadasHoy = hoy.getOrDefault(s.slug(), 0);
            int restante = Math.max(0, s.maxPiezas() - publicadasHoy);
            estadoSecciones.add(new SeccionEstadoView(s.slug(), s.nombre(), s.activa(),
                    s.autoPublica(), s.enfoque(), s.maxPiezas(), publicadasHoy, restante));
        }

        return new TableroEdicionView(
                historico.isEmpty() ? null : historico.get(0),
                estadoSecciones,
                historico,
                store.enCurso(),
                store.totalPublicadas());
    }

    @Override
    public EjecucionDetalleView detalle(String ejecucionId) {
        EdicionStore.EjecucionFila fila = buscar(ejecucionId);
        List<PiezaEjecucionView> piezas = store.piezas(fila.id()).stream()
                .map(p -> new PiezaEjecucionView(p.articleId(), p.sectionId(), p.titulo(),
                        "/admin/articulos/" + p.articleId()))
                .toList();
        return new EjecucionDetalleView(ver(fila), piezas);
    }

    @Override
    public String edicionMarkdown(String ejecucionId) {
        EjecucionDetalleView detalle = detalle(ejecucionId);
        EjecucionView ejecucion = detalle.ejecucion();
        StringBuilder md = new StringBuilder();
        md.append("# Noticias Planeta — edición del ").append(ejecucion.iniciada()).append("\n\n");
        md.append("Ejecución ").append(ejecucion.id(), 0, 8)
                .append(" · modo ").append(ejecucion.modo())
                .append(" · ").append(detalle.piezas().size()).append(" piezas")
                .append(" · fuentes ").append(ejecucion.fuentes())
                .append(" · publicadas ").append(ejecucion.publicadas())
                .append("\n\n");
        for (PiezaEjecucionView pieza : detalle.piezas()) {
            md.append("## ").append(pieza.sectionId()).append("\n\n")
                    .append("### ").append(pieza.titulo()).append("\n\n")
                    .append("[Abrir en el panel](/admin/articulos/").append(pieza.articleId()).append(")\n\n");
        }
        return md.toString();
    }

    // ---------------------------------------------------------------- ejecución

    @Override
    public EjecucionView ejecutar(String actorId, Actor actor, String modo) {
        String modoNormalizado = "borrador".equals(modo) ? "borrador" : "publicar";
        UUID id = UUID.randomUUID();
        Instant inicio = clock.instant();

        if (store.abrir(id, inicio, actorId, modoNormalizado).isEmpty()) {
            throw new DomainException("EDICION_EN_CURSO",
                    "Ya hay una edición en curso; espera a que termine antes de lanzar otra");
        }

        int fuentes = 0;
        int fuentesConError = 0;
        int creadas = 0;
        int creados = 0;
        int publicadas = 0;
        int errores = 0;
        List<String> incidencias = new ArrayList<>();
        List<EdicionStore.PiezaFila> piezas = new ArrayList<>();
        String estado;

        try {
            IngestAllSourcesUseCase.InformeFuentes informe = ingesta.runDetailed();
            fuentes = informe.fuentes().size();
            creadas = informe.creadas();
            for (IngestAllSourcesUseCase.FuenteIngesta fuente : informe.fuentes()) {
                if (fuente.error() != null) {
                    fuentesConError++;
                    errores++;
                    incidencias.add("fuente «%s»: %s".formatted(fuente.nombre(), fuente.error()));
                }
            }

            creados = store.borradoresCreadosDesde(inicio);

            Instant desde = inicio.minus(Duration.ofHours(ventanaHoras));
            List<EdicionStore.Candidato> seleccion = store.candidatos(desde, limite, maxPorFuente);
            for (EdicionStore.Candidato candidato : seleccion) {
                boolean dentro = false;
                if ("publicar".equals(modoNormalizado)) {
                    try {
                        publicarPorLaMaquinaDeEstados(candidato.articleId(), actor);
                        publicadas++;
                        dentro = true;
                    } catch (RuntimeException e) {
                        errores++;
                        incidencias.add("publicación «%s»: %s".formatted(
                                recortar(candidato.titulo()), e.getMessage()));
                    }
                } else {
                    dentro = true;
                }
                if (dentro) {
                    piezas.add(new EdicionStore.PiezaFila(
                            candidato.articleId(), candidato.sectionId(), candidato.titulo()));
                }
            }

            store.guardarPiezas(id, piezas);

            if (errores == 0) {
                estado = EdicionEstado.COMPLETADA.name();
            } else if (publicadas > 0 || !piezas.isEmpty() || creadas > 0) {
                estado = EdicionEstado.PARCIAL.name();
            } else {
                estado = EdicionEstado.FALLIDA.name();
            }
        } catch (RuntimeException e) {
            errores++;
            estado = EdicionEstado.FALLIDA.name();
            incidencias.add("ejecución abortada: " + e.getMessage());
        }

        if (piezas.isEmpty()) {
            incidencias.add(0, "sin piezas nuevas: no había borradores con foto dentro de la ventana de "
                    + ventanaHoras + " h");
        }

        String detalle = incidencias.isEmpty() ? null : String.join("\n", incidencias);
        store.cerrar(new EdicionStore.Cierre(id, clock.instant(), estado, fuentes, fuentesConError,
                creadas, creados, publicadas, errores, detalle));

        // Rastro de quién lanzó la edición. Las piezas dejan el suyo en cada transición.
        audit.record(new AuditEntry(actorId, actor.type().name(), "EDITION_RUN", "Edicion",
                id.toString(), null, estado, clock.instant()));

        return store.ejecucion(id).map(this::ver)
                .orElseThrow(() -> new DomainException("EDICION_PERDIDA",
                        "La ejecución se cerró pero no se pudo releer"));
    }

    private void publicarPorLaMaquinaDeEstados(String articleId, Actor actor) {
        for (String target : CADENA_PUBLICACION) {
            transiciones.transition(TransitionCommand.of(articleId, target, actor, null));
        }
    }

    // ---------------------------------------------------------------- formato

    private EjecucionView ver(EdicionStore.EjecucionFila f) {
        String duracion = "";
        if (f.fin() != null) {
            long ms = Math.max(0, Duration.between(f.inicio(), f.fin()).toMillis());
            duracion = ms < 60_000
                    ? String.format(Locale.ROOT, "%.1f s", ms / 1000.0).replace('.', ',')
                    : String.format(Locale.ROOT, "%d min %02d s", ms / 60_000, (ms % 60_000) / 1000);
        }
        return new EjecucionView(
                f.id().toString(),
                fecha(f.inicio()),
                f.fin() == null ? "" : fecha(f.fin()),
                duracion,
                f.actor(),
                f.modo(),
                f.estado(),
                f.fuentes(),
                f.fuentesConError(),
                f.creadas(),
                f.borradores(),
                f.publicadas(),
                f.errores(),
                f.detalle());
    }

    private String fecha(Instant instante) {
        return FECHA.format(instante.atZone(ZONA));
    }

    private EdicionStore.EjecucionFila buscar(String ejecucionId) {
        UUID id;
        try {
            id = UUID.fromString(ejecucionId);
        } catch (RuntimeException e) {
            throw new DomainException("EJECUCION_INVALIDA", "Identificador de ejecución no válido");
        }
        return store.ejecucion(id)
                .orElseThrow(() -> new DomainException("EJECUCION_NO_ENCONTRADA",
                        "No existe la ejecución " + ejecucionId));
    }

    private static String recortar(String texto) {
        if (texto == null) return "";
        return texto.length() <= 60 ? texto : texto.substring(0, 57) + "…";
    }
}
