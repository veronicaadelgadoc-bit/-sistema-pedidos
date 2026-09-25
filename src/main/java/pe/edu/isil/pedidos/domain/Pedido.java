package pe.edu.isil.pedidos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad que representa un pedido realizado por un cliente.
 */
@Entity
@Table(name = "pedido")
public class Pedido {

  @Id
  @GeneratedValue(
      strategy = GenerationType.IDENTITY
  )
  private Long id;

  @Column(
      nullable = false,
      length = 120
  )
  private String cliente;

  @ManyToOne(
      fetch = FetchType.EAGER,
      optional = false
  )
  @JoinColumn(
      name = "producto_id",
      nullable = false
  )
  private Producto producto;

  @Column(nullable = false)
  private int cantidad;

  @Column(
      nullable = false,
      precision = 12,
      scale = 2
  )
  private BigDecimal total;

  @Column(nullable = false)
  private LocalDateTime fecha;

  protected Pedido() {
  }

  public Pedido(String cliente, Producto producto, int cantidad, BigDecimal total) {
    this.cliente = cliente;
    this.producto = producto;
    this.cantidad = cantidad;
    this.total = total;
    this.fecha = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public String getCliente() {
    return cliente;
  }

  public Producto getProducto() {
    return producto;
  }

  public int getCantidad() {
    return cantidad;
  }

  public BigDecimal getTotal() {
    return total;
  }

  public LocalDateTime getFecha() {
    return fecha;
  }

  /**
   * Aplica los nuevos datos de una edición sobre este pedido, manteniendo la
   * encapsulación: quien llama nunca asigna los campos directamente, solo
   * entrega el nuevo estado ya validado por PedidoService.
   *
   * @param cliente  Nuevo nombre del cliente.
   * @param producto Producto asociado al pedido (puede ser el mismo u otro).
   * @param cantidad Nueva cantidad solicitada.
   * @param total    Nuevo total recalculado en función del producto y la cantidad.
   */
  public void editar(String cliente, Producto producto, int cantidad, BigDecimal total) {
    this.cliente = cliente.trim();
    this.producto = producto;
    this.cantidad = cantidad;
    this.total = total;
  }

}
