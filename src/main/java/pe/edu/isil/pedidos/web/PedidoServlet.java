package pe.edu.isil.pedidos.web;

import jakarta.ejb.EJB;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import pe.edu.isil.pedidos.domain.Pedido;
import pe.edu.isil.pedidos.service.PedidoException;
import pe.edu.isil.pedidos.service.PedidoNotFoundException;
import pe.edu.isil.pedidos.service.PedidoService;

/**
 * Servlet que maneja las solicitudes relacionadas con los pedidos.
 *
 * <p>Rutas soportadas:</p>
 * <ul>
 *   <li>GET  /pedidos            -&gt; lista pedidos y productos (vista JSP).</li>
 *   <li>POST /pedidos            -&gt; registra un nuevo pedido (formulario HTML).</li>
 *   <li>PUT  /pedidos/{id}       -&gt; actualiza un pedido (fetch, cuerpo JSON,
 *       HTTP real: {@link #doPut}).</li>
 *   <li>DELETE /pedidos/{id}     -&gt; elimina un pedido (fetch, HTTP real:
 *       {@link #doDelete}).</li>
 * </ul>
 *
 * <p>El Servlet solo coordina la solicitud HTTP (parsea parámetros/JSON,
 * traduce excepciones de negocio a códigos de estado). Toda la lógica de
 * negocio, stock y transacciones vive en {@link PedidoService}.</p>
 */
@WebServlet({"/pedidos", "/pedidos/*"})
public class PedidoServlet
    extends HttpServlet {

  @EJB
  private PedidoService pedidoService;

  // ---------------------------------------------------------------------
  // GET: listado (vista JSP)
  // ---------------------------------------------------------------------

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    cargarDatosVista(request);
    request.getRequestDispatcher("/WEB-INF/views/pedidos.jsp")
        .forward(request, response);
  }

  // ---------------------------------------------------------------------
  // POST: registrar pedido (formulario HTML clásico)
  // ---------------------------------------------------------------------

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    request.setCharacterEncoding(StandardCharsets.UTF_8.name());

    try {
      String cliente = request.getParameter("cliente");
      Long productoId = Long.valueOf(request.getParameter("productoId"));
      int cantidad = Integer.parseInt(request.getParameter("cantidad"));

      Pedido pedido = pedidoService.registrarPedido(cliente, productoId, cantidad);

      // Patrón PRG (Post/Redirect/Get) para evitar reenvíos de formularios
      response.sendRedirect(request.getContextPath() + "/pedidos?creado="
              + pedido.getId());
    } catch (NumberFormatException e) {
      mostrarErrorNegocio(request, response, "Producto o cantidad inválidos.");
    } catch (PedidoException e) {
      mostrarErrorNegocio(request, response, e.getMessage());
    } catch (RuntimeException e) {
      mostrarErrorGeneral(request, response);
    }
  }

  // ---------------------------------------------------------------------
  // PUT: actualizar pedido (reto avanzado, HTTP real desde el navegador)
  // ---------------------------------------------------------------------

  /**
   * Actualiza un pedido existente. Se invoca desde el navegador mediante
   * {@code fetch(url, { method: "PUT", body: JSON.stringify({...}) })}.
   * El id del pedido viaja en la ruta: {@code PUT /pedidos/{id}}.
   * El cuerpo de la solicitud es JSON:
   * {@code { "cliente": "...", "productoId": 1, "cantidad": 2 }}.
   *
   * @param request  Solicitud HTTP con method PUT.
   * @param response Respuesta HTTP en formato JSON.
   */
  @Override
  protected void doPut(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    request.setCharacterEncoding(StandardCharsets.UTF_8.name());

    Long pedidoId = extraerIdDeRuta(request);
    if (pedidoId == null) {
      responderError(response, HttpServletResponse.SC_BAD_REQUEST,
          "Debe indicar el id del pedido en la ruta: PUT /pedidos/{id}.");
      return;
    }

    try {
      JsonObject body = leerCuerpoJson(request);

      String cliente = body.containsKey("cliente") ? body.getString("cliente") : null;
      Long productoId = leerLong(body, "productoId");
      int cantidad = body.containsKey("cantidad") ? body.getInt("cantidad") : 0;

      Pedido pedido = pedidoService.actualizarPedido(pedidoId, cliente, productoId, cantidad);

      responderPedido(response, HttpServletResponse.SC_OK, pedido,
          "Pedido #" + pedido.getId() + " actualizado correctamente.");
    } catch (PedidoNotFoundException e) {
      responderError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
    } catch (PedidoException e) {
      responderError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
    } catch (RuntimeException e) {
      responderError(response, HttpServletResponse.SC_BAD_REQUEST,
          "Cuerpo de la solicitud inválido o incompleto.");
    }
  }

  // ---------------------------------------------------------------------
  // DELETE: eliminar pedido (reto avanzado, HTTP real desde el navegador)
  // ---------------------------------------------------------------------

  /**
   * Elimina un pedido existente y repone su stock. Se invoca desde el
   * navegador mediante {@code fetch(url, { method: "DELETE" })}.
   * El id del pedido viaja en la ruta: {@code DELETE /pedidos/{id}}.
   *
   * @param request  Solicitud HTTP con method DELETE.
   * @param response Respuesta HTTP en formato JSON.
   */
  @Override
  protected void doDelete(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    Long pedidoId = extraerIdDeRuta(request);
    if (pedidoId == null) {
      responderError(response, HttpServletResponse.SC_BAD_REQUEST,
          "Debe indicar el id del pedido en la ruta: DELETE /pedidos/{id}.");
      return;
    }

    try {
      pedidoService.eliminarPedido(pedidoId);
      responderMensaje(response, HttpServletResponse.SC_OK,
          "Pedido #" + pedidoId + " eliminado y stock repuesto correctamente.");
    } catch (PedidoNotFoundException e) {
      responderError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
    } catch (PedidoException e) {
      responderError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
    }
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private void cargarDatosVista(HttpServletRequest request) {
    request.setAttribute("productos", pedidoService.listarProductos());
    request.setAttribute("pedidos", pedidoService.listarPedidos());
  }

  /**
   * Extrae el id numérico de rutas del tipo {@code /pedidos/5} usando
   * {@link HttpServletRequest#getPathInfo()}. Devuelve {@code null} si no
   * hay id o no es numérico (el llamador responde 400 en ese caso).
   */
  private Long extraerIdDeRuta(HttpServletRequest request) {
    String pathInfo = request.getPathInfo(); // ej: "/5"
    if (pathInfo == null || pathInfo.isBlank() || "/".equals(pathInfo)) {
      return null;
    }
    String idTexto = pathInfo.substring(1);
    try {
      return Long.valueOf(idTexto);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private JsonObject leerCuerpoJson(HttpServletRequest request) throws IOException {
    try (JsonReader reader = Json.createReader(request.getInputStream())) {
      return reader.readObject();
    }
  }

  private Long leerLong(JsonObject body, String campo) {
    if (!body.containsKey(campo) || body.isNull(campo)) {
      return null;
    }
    return body.getJsonNumber(campo).longValueExact();
  }

  private void responderPedido(HttpServletResponse response, int status, Pedido pedido, String mensaje)
      throws IOException {
    JsonObject json = Json.createObjectBuilder()
        .add("ok", true)
        .add("mensaje", mensaje)
        .add("pedido", Json.createObjectBuilder()
            .add("id", pedido.getId())
            .add("cliente", pedido.getCliente())
            .add("productoId", pedido.getProducto().getId())
            .add("producto", pedido.getProducto().getNombre())
            .add("cantidad", pedido.getCantidad())
            .add("total", pedido.getTotal())
            .add("stockProducto", pedido.getProducto().getStock())
            .build())
        .build();
    escribirJson(response, status, json);
  }

  private void responderMensaje(HttpServletResponse response, int status, String mensaje)
      throws IOException {
    JsonObject json = Json.createObjectBuilder()
        .add("ok", true)
        .add("mensaje", mensaje)
        .build();
    escribirJson(response, status, json);
  }

  private void responderError(HttpServletResponse response, int status, String mensaje)
      throws IOException {
    JsonObject json = Json.createObjectBuilder()
        .add("ok", false)
        .add("error", mensaje)
        .build();
    escribirJson(response, status, json);
  }

  private void escribirJson(HttpServletResponse response, int status, JsonObject json) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write(json.toString());
  }

  private void mostrarErrorNegocio(HttpServletRequest request, HttpServletResponse response, String mensaje)
      throws ServletException, IOException {
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

    request.setAttribute("clienteIngresado", request.getParameter("cliente"));
    request.setAttribute("cantidadIngresada", request.getParameter("cantidad"));
    request.setAttribute("error", mensaje);

    cargarDatosVista(request);

    request.getRequestDispatcher("/WEB-INF/views/pedidos.jsp")
        .forward(request, response);
  }

  private void mostrarErrorGeneral(
      HttpServletRequest request,
      HttpServletResponse response)
      throws ServletException, IOException {

    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

    request.setAttribute("error", "Ocurrió un error interno al procesar la solicitud.");

    request.getRequestDispatcher("/WEB-INF/views/error.jsp")
        .forward(request, response);
  }
}
