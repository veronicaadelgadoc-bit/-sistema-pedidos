package pe.edu.isil.pedidos.service;

import jakarta.ejb.ApplicationException;

/**
 * Excepción personalizada para errores relacionados con pedidos.
 * Esta excepción es marcada con @ApplicationException(rollback = true) para indicar
 * que cualquier transacción en curso debe ser revertida si se lanza esta excepción.
 */
@ApplicationException(rollback = true)
public class PedidoException extends RuntimeException {

    /**
     * Crea una nueva instancia de PedidoException con un mensaje específico.
     *
     * @param message El mensaje de error que describe la causa de la excepción.
     */
    public PedidoException(String message) {
        super(message);
    }

}
