package com.noticiasplaneta.identity.application.port.in;

import com.noticiasplaneta.identity.domain.AccountState;
import com.noticiasplaneta.identity.domain.Role;

import java.util.List;
import java.util.UUID;

/**
 * Gestión de quién puede entrar al backoffice.
 *
 * <p>Solo el rol {@code ADMIN} —la raíz— puede usar este caso de uso. Un
 * {@code AGENT} administra contenido, fuentes y métricas, pero no toca las
 * cuentas: si pudiera, podría desactivar a la raíz y quedarse con el panel.
 *
 * <p>La jerarquía es de un solo sentido: la raíz puede apagar al agente y
 * volver a encenderlo; el agente no puede ni ver esta pantalla.
 */
public interface AccountAdminUseCase {

    List<Cuenta> cuentas(String solicitanteEmail);

    /** Activa o desactiva una cuenta. */
    Resultado cambiarEstado(String solicitanteEmail, UUID objetivoId, boolean activar);

    record Cuenta(UUID id, String email, String nombre, Role role, AccountState state,
                  boolean esLaRaiz, boolean soyYo) {

        public boolean activa() {
            return state == AccountState.ACTIVE;
        }
    }

    enum Resultado {
        HECHO,
        NO_EXISTE,
        /** La raíz no puede apagarse: dejaría el panel sin llave. */
        ES_LA_RAIZ,
        SIN_PERMISO
    }
}
