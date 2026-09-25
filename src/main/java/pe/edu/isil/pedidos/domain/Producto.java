package pe.edu.isil.pedidos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Entidad que representa un producto disponible para la venta.
 */
@Entity
@Table(name = "producto")
public class Producto {

  @Id
  @GeneratedValue(
      strategy = GenerationType.IDENTITY
  )
  private Long id;

  @Column(
      nullable = false,
      length = 100
  )
  private String nombre;

  @Column(
      nullable = false,
      precision = 12,
      scale = 2
  )
  private BigDecimal precio;

  @Column(nullable = false)
  private int stock;

  protected Producto() {
    // Constructor requerido por JPA
  }

  public Producto(String nombre, BigDecimal precio, int stock) {
    this.nombre = nombre;
    this.precio = precio;
    this.stock = stock;
  }

  public Long getId() {
    return id;
  }

  public String getNombre() {
    return nombre;
  }

  public BigDecimal getPrecio() {
    return precio;
  }

  public int getStock() {
    return stock;
  }

  public void descontarStock(int cantidad) {
    if (cantidad <= 0) {
      throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
    }

    if (cantidad > stock) {
      throw new IllegalStateException("Stock insuficiente. Disponible: " + stock);
    }

    stock -= cantidad;
  }

  /**
   * Devuelve unidades al stock del producto. Se usa al editar o eliminar un
   * pedido, para compensar las unidades que ese pedido tenía reservadas
   * antes de aplicar el nuevo estado.
   *
   * @param cantidad Cantidad de unidades a reponer.
   */
  public void reponerStock(int cantidad) {
    if (cantidad <= 0) {
      throw new IllegalArgumentException("La cantidad a reponer debe ser mayor que cero.");
    }

    stock += cantidad;
  }

}
