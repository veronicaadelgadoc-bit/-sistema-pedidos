package pe.edu.isil.pedidos.service;

import jakarta.ejb.ApplicationException;

/**
 * Excepción específica para cuando se busca, edita o elimina un pedido cuyo
 * ID no existe. Se distingue de {@link PedidoException} para que el Servlet
 * pueda responder 404 (recurso inexistente) en vez de 400 (datos inválidos).
 */
@ApplicationException(rollback = true)
public class PedidoNotFoundException extends PedidoException {

  public PedidoNotFoundException(String message) {
    super(message);
  }

}
