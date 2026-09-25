<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>

<!doctype html>
<html lang="es">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Sistema de Pedidos - ISIL</title>
    <c:url var="cssUrl" value="/assets/css/app.css"/>
    <link rel="stylesheet" href="${cssUrl}">
</head>

<body>
<h1>Sistema de Pedidos</h1>

<p class="nota">
    Flujo registrar: Navegador &rarr; PedidoServlet (POST) &rarr; PedidoService (EJB) &rarr; JPA &rarr; H2<br>
    Flujo editar/eliminar: Navegador (fetch PUT/DELETE) &rarr; PedidoServlet.doPut()/doDelete() &rarr; PedidoService &rarr; JPA &rarr; H2
</p>

<c:if test="${not empty error}">
    <div class="error">
        <c:out value="${error}"/>
    </div>
</c:if>

<c:if test="${not empty param.creado}">
    <div class="mensaje">
        Pedido #
        <c:out value="${param.creado}"/>
        registrado correctamente.
    </div>
</c:if>

<!-- Mensaje de éxito/error mostrado tras una edición o eliminación por fetch (PUT/DELETE) -->
<div id="mensajeAccion" class="mensaje" hidden></div>

<h2>Registrar pedido</h2>

<c:url var="pedidosUrl" value="/pedidos"/>

<form method="post"
      action="${pedidosUrl}"
      class="form-grid">

    <label>
        Cliente
        <input
            name="cliente"
            required
            maxlength="120"
            placeholder="Ej. Ana Torres"
            value="${clienteIngresado}">
    </label>
    <label>
        Producto
        <select name="productoId" required>
            <c:forEach var="producto" items="${productos}">
                <option value="${producto.id}">
                    <c:out value="${producto.nombre}"/>
                    - S/
                    <fmt:formatNumber
                            value="${producto.precio}"
                            minFractionDigits="2"
                            maxFractionDigits="2"/>
                    - stock:
                    <c:out value="${producto.stock}"/>
                </option>
            </c:forEach>
        </select>
    </label>

    <label>
        Cantidad
        <input
            name="cantidad"
            type="number"
            min="1"
            value="${empty cantidadIngresada ? 1 : cantidadIngresada}"
            required>
    </label>

    <button type="submit">
        Registrar
    </button>
</form>

<h2>Pedidos registrados</h2>

<table>
    <thead>
        <tr>
            <th>ID</th>
            <th>Cliente</th>
            <th>Producto</th>
            <th>Cantidad</th>
            <th>Total</th>
            <th>Fecha</th>
            <th>Acciones</th>
        </tr>
    </thead>
    <tbody id="tablaPedidos">
        <c:forEach var="pedido" items="${pedidos}">
            <tr data-pedido-id="${pedido.id}">
                <td>
                    <c:out value="${pedido.id}"/>
                </td>
                <td>
                    <c:out value="${pedido.cliente}"/>
                </td>
                <td>
                    <c:out value="${pedido.producto.nombre}"/>
                </td>
                <td>
                    <c:out value="${pedido.cantidad}"/>
                </td>
                <td>
                    S/
                    <fmt:formatNumber
                            value="${pedido.total}"
                            minFractionDigits="2"
                            maxFractionDigits="2"/>
                </td>
                <td>
                    <c:out value="${pedido.fecha}"/>
                </td>
                <td class="acciones">
                    <button
                        type="button"
                        class="btn-editar"
                        data-id="${pedido.id}"
                        data-cliente="${pedido.cliente}"
                        data-producto-id="${pedido.producto.id}"
                        data-cantidad="${pedido.cantidad}">
                        Editar
                    </button>
                    <button
                        type="button"
                        class="btn-eliminar"
                        data-id="${pedido.id}">
                        Eliminar
                    </button>
                </td>
            </tr>
        </c:forEach>

        <c:if test="${empty pedidos}">
            <tr>
                <td colspan="7">
                    Aún no hay pedidos.
                </td>
            </tr>
        </c:if>
    </tbody>
</table>

<!-- Formulario de edición. Se muestra/oculta con JS; se envía con
     fetch(..., { method: "PUT" }) hacia /pedidos/{id} en formato JSON. -->
<dialog id="dialogoEditar">
    <form id="formEditar" method="dialog" class="form-grid">
        <h2>Editar pedido #<span id="editarId"></span></h2>

        <label>
            Cliente
            <input name="cliente" id="editarCliente" required maxlength="120">
        </label>

        <label>
            Producto
            <select name="productoId" id="editarProductoId" required>
                <c:forEach var="producto" items="${productos}">
                    <option value="${producto.id}">
                        <c:out value="${producto.nombre}"/>
                        - S/
                        <fmt:formatNumber
                                value="${producto.precio}"
                                minFractionDigits="2"
                                maxFractionDigits="2"/>
                        - stock:
                        <c:out value="${producto.stock}"/>
                    </option>
                </c:forEach>
            </select>
        </label>

        <label>
            Cantidad
            <input name="cantidad" id="editarCantidad" type="number" min="1" required>
        </label>

        <div class="dialogo-acciones">
            <button type="button" id="btnGuardarEdicion">Guardar (PUT)</button>
            <button type="button" id="btnCancelarEdicion">Cancelar</button>
        </div>
    </form>
</dialog>

<script>
(function () {
    "use strict";

    var baseUrl = "${pedidosUrl}"; // /sistema-pedidos/pedidos
    var dialogoEditar = document.getElementById("dialogoEditar");
    var mensajeAccion = document.getElementById("mensajeAccion");

    function mostrarMensaje(texto, esError) {
        // Se guarda en sessionStorage para sobrevivir al reload() de la página.
        sessionStorage.setItem("pedidosMensaje", JSON.stringify({texto: texto, error: !!esError}));
    }

    function recuperarMensaje() {
        var raw = sessionStorage.getItem("pedidosMensaje");
        if (!raw) {
            return;
        }
        sessionStorage.removeItem("pedidosMensaje");
        try {
            var datos = JSON.parse(raw);
            mensajeAccion.textContent = datos.texto;
            mensajeAccion.className = datos.error ? "error" : "mensaje";
            mensajeAccion.hidden = false;
        } catch (e) {
            // Se ignora un mensaje corrupto.
        }
    }

    recuperarMensaje();

    // --- Abrir edición ---
    document.querySelectorAll(".btn-editar").forEach(function (boton) {
        boton.addEventListener("click", function () {
            document.getElementById("editarId").textContent = boton.dataset.id;
            document.getElementById("editarCliente").value = boton.dataset.cliente;
            document.getElementById("editarProductoId").value = boton.dataset.productoId;
            document.getElementById("editarCantidad").value = boton.dataset.cantidad;
            dialogoEditar.dataset.pedidoId = boton.dataset.id;
            dialogoEditar.showModal();
        });
    });

    document.getElementById("btnCancelarEdicion").addEventListener("click", function () {
        dialogoEditar.close();
    });

    // --- Guardar edición: PUT /pedidos/{id} con cuerpo JSON ---
    document.getElementById("btnGuardarEdicion").addEventListener("click", function () {
        var id = dialogoEditar.dataset.pedidoId;
        var cuerpo = {
            cliente: document.getElementById("editarCliente").value,
            productoId: Number(document.getElementById("editarProductoId").value),
            cantidad: Number(document.getElementById("editarCantidad").value)
        };

        fetch(baseUrl + "/" + id, {
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(cuerpo)
        })
            .then(function (resp) {
                return resp.json().then(function (data) {
                    return {status: resp.status, data: data};
                });
            })
            .then(function (resultado) {
                dialogoEditar.close();
                if (resultado.data.ok) {
                    mostrarMensaje(resultado.data.mensaje, false);
                } else {
                    mostrarMensaje("Error " + resultado.status + ": " + resultado.data.error, true);
                }
                window.location.reload();
            })
            .catch(function () {
                mostrarMensaje("No se pudo conectar con el servidor.", true);
                window.location.reload();
            });
    });

    // --- Eliminar: DELETE /pedidos/{id} ---
    document.querySelectorAll(".btn-eliminar").forEach(function (boton) {
        boton.addEventListener("click", function () {
            var id = boton.dataset.id;
            if (!window.confirm("¿Eliminar el pedido #" + id + "? Se repondrá el stock del producto.")) {
                return;
            }

            fetch(baseUrl + "/" + id, {method: "DELETE"})
                .then(function (resp) {
                    return resp.json().then(function (data) {
                        return {status: resp.status, data: data};
                    });
                })
                .then(function (resultado) {
                    if (resultado.data.ok) {
                        mostrarMensaje(resultado.data.mensaje, false);
                    } else {
                        mostrarMensaje("Error " + resultado.status + ": " + resultado.data.error, true);
                    }
                    window.location.reload();
                })
                .catch(function () {
                    mostrarMensaje("No se pudo conectar con el servidor.", true);
                    window.location.reload();
                });
        });
    });
})();
</script>

</body>
</html>
