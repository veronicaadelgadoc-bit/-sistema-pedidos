package pe.edu.isil.pedidos.service;

import pe.edu.isil.pedidos.domain.Pedido;
import pe.edu.isil.pedidos.domain.Producto;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;

/**
 * Servicio EJB que maneja la lógica de negocio relacionada con los pedidos.
 */
@Stateless
public class PedidoService {

  @PersistenceContext(unitName = "PedidosPU")
  private EntityManager entityManager;

  /**
   * Registra un nuevo pedido en el sistema.
   *
   * @param cliente    Nombre del cliente que realiza el pedido.
   * @param productoId ID del producto que se desea comprar.
   * @param cantidad   Cantidad de productos a comprar.
   * @return El pedido registrado.
   * @throws IllegalArgumentException Si alguno de los parámetros es inválido o si el producto no existe.
   */
  @TransactionAttribute(TransactionAttributeType.REQUIRED)
  public Pedido registrarPedido(String cliente, Long productoId, int cantidad) {
    validarDatos(cliente, productoId, cantidad);

    Producto producto = entityManager.find(Producto.class, productoId);
    if (producto == null) {
      throw new PedidoException("El producto no existe.");
    }

    try {
      // REGLA DE NEGOCIO
      producto.descontarStock(cantidad);
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw new PedidoException(e.getMessage());
    }

    BigDecimal total = producto.getPrecio().multiply(BigDecimal.valueOf(cantidad));
    Pedido pedido = new Pedido(cliente.trim(), producto, cantidad, total);
    entityManager.persist(pedido);
    return pedido;
  }

  /**
   * Busca un pedido por su ID.
   *
   * @param pedidoId ID del pedido.
   * @return El pedido encontrado.
   * @throws PedidoNotFoundException Si no existe un pedido con ese ID.
   */
  @TransactionAttribute(TransactionAttributeType.SUPPORTS)
  public Pedido buscarPedido(Long pedidoId) {
    Pedido pedido = entityManager.find(Pedido.class, pedidoId);
    if (pedido == null) {
      throw new PedidoNotFoundException("El pedido #" + pedidoId + " no existe.");
    }
    return pedido;
  }

  /**
   * Actualiza cliente, producto y/o cantidad de un pedido existente,
   * manteniendo la consistencia del stock:
   * <ol>
   *   <li>Repone al producto original las unidades que el pedido tenía reservadas.</li>
   *   <li>Valida y descuenta del producto final (nuevo o el mismo) la nueva cantidad.</li>
   *   <li>Recalcula el total y aplica los cambios sobre la entidad Pedido.</li>
   * </ol>
   * Toda la operación corre en una única transacción: si cualquier validación
   * falla, se lanza una excepción de aplicación marcada con rollback = true,
   * por lo que ningún cambio (ni de stock ni del pedido) queda persistido.
   *
   * @param pedidoId   ID del pedido a editar.
   * @param cliente    Nuevo nombre del cliente.
   * @param productoId ID del producto (puede ser el mismo u otro distinto).
   * @param cantidad   Nueva cantidad solicitada.
   * @return El pedido actualizado.
   * @throws PedidoNotFoundException Si el pedido o el producto no existen.
   * @throws PedidoException         Si los datos son inválidos o no hay stock suficiente.
   */
  @TransactionAttribute(TransactionAttributeType.REQUIRED)
  public Pedido actualizarPedido(Long pedidoId, String cliente, Long productoId, int cantidad) {
    validarDatos(cliente, productoId, cantidad);

    Pedido pedido = buscarPedido(pedidoId);

    Producto productoNuevo = entityManager.find(Producto.class, productoId);
    if (productoNuevo == null) {
      throw new PedidoException("El producto seleccionado no existe.");
    }

    Producto productoOriginal = pedido.getProducto();
    int cantidadOriginal = pedido.getCantidad();

    // 1) Se repone el stock que este pedido tenía reservado. Si el producto
    //    no cambia, la reposición y el nuevo descuento afectan al mismo
    //    objeto, por lo que el ajuste neto queda correcto.
    productoOriginal.reponerStock(cantidadOriginal);

    try {
      // 2) Se valida y descuenta el stock del producto final con la nueva cantidad.
      productoNuevo.descontarStock(cantidad);
    } catch (IllegalArgumentException | IllegalStateException e) {
      // Al lanzar una PedidoException (@ApplicationException(rollback = true))
      // toda la transacción se revierte: la reposición anterior tampoco se
      // persiste y ni el stock ni el pedido quedan con cambios parciales.
      throw new PedidoException(e.getMessage());
    }

    BigDecimal total = productoNuevo.getPrecio().multiply(BigDecimal.valueOf(cantidad));
    pedido.editar(cliente, productoNuevo, cantidad, total);

    return pedido;
  }

  /**
   * Elimina un pedido existente y repone el stock del producto asociado
   * antes de retirar el registro, dentro de una única transacción.
   *
   * @param pedidoId ID del pedido a eliminar.
   * @throws PedidoNotFoundException Si el pedido no existe.
   */
  @TransactionAttribute(TransactionAttributeType.REQUIRED)
  public void eliminarPedido(Long pedidoId) {
    Pedido pedido = buscarPedido(pedidoId);

    // Se repone el stock ANTES de retirar el pedido, tal como exige la
    // consigna (CASO 5).
    pedido.getProducto().reponerStock(pedido.getCantidad());

    entityManager.remove(pedido);
  }

  /**
   * Lista todos los productos disponibles en el sistema.
   *
   * @return Lista de productos.
   */
  @TransactionAttribute(TransactionAttributeType.REQUIRED)
  public List<Producto> listarProductos() {
    inicializarProductosSiEsNecesario();
    return entityManager
        .createQuery(
            """
            select p
            from Producto p
            order by p.id
            """,
            Producto.class
        )
        .getResultList();
  }

  /**
   * Lista todos los pedidos realizados en el sistema.
   *
   * @return Lista de pedidos.
   */
  @TransactionAttribute(TransactionAttributeType.SUPPORTS)
  public List<Pedido> listarPedidos() {
    return entityManager
        .createQuery(
            """
            select p
            from Pedido p
            join fetch p.producto
            order by p.id desc
            """,
            Pedido.class
        )
        .getResultList();
  }

  /**
   * Valida los datos de entrada para registrar un pedido.
   *
   * @param cliente    Nombre del cliente.
   * @param productoId ID del producto.
   * @param cantidad   Cantidad de productos.
   * @throws PedidoException Si alguno de los datos es inválido.
   */
  private void validarDatos(String cliente, Long productoId, int cantidad) {
    if (cliente == null || cliente.isBlank()) {
      throw new PedidoException("El cliente es obligatorio.");
    }
    if (productoId == null) {
      throw new PedidoException("Debe seleccionar un producto.");
    }
    if (cantidad <= 0) {
      throw new PedidoException("La cantidad debe ser mayor que cero.");
    }
  }

  /**
   * Inicializa algunos productos de ejemplo si no existen en la base de datos.
   */
  private void inicializarProductosSiEsNecesario() {
    Long cantidad =
        entityManager
            .createQuery(
                """
                select count(p)
                from Producto p
                """,
                Long.class
            )
            .getSingleResult();

    if (cantidad == 0) {
      entityManager.persist(
          new Producto(
              "Laptop",
              new BigDecimal("2500.00"),
              5
          )
      );

      entityManager.persist(
          new Producto(
              "Monitor",
              new BigDecimal("850.00"),
              8
          )
      );

      entityManager.persist(
          new Producto(
              "Teclado",
              new BigDecimal("120.00"),
              15
          )
      );
    }
  }

}
