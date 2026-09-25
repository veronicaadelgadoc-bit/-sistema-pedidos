# Sistema de Pedidos — Edición y Eliminación (PA1)

Ampliación individual del proyecto base **sistema-pedidos** (Servlet + JSP +
EJB + JPA + H2 sobre WildFly) para incorporar edición y eliminación de
pedidos, con la solución avanzada de **HTTP real (PUT/DELETE)** desde el
navegador.

Curso: Desarrollo de Aplicaciones Empresariales Avanzado — 2026-20.
Estudiante: Veronica Delgado.

## Tecnologías

- Java 21
- Maven
- Jakarta EE 11 (Servlet, JSP/JSTL, EJB, JPA, JSON-P)
- Hibernate ORM (provisto por WildFly)
- H2 en memoria (`ExampleDS`)
- WildFly 41 (WildFly Maven Plugin)

## Arquitectura

```text
Navegador (JSP + fetch)
    |
    | HTTP: GET/POST /pedidos    PUT/DELETE /pedidos/{id}
    v
PedidoServlet            <-- Web Tier (coordina HTTP, no contiene reglas de negocio)
    |
    | @EJB
    v
PedidoService (@Stateless)  <-- Business Tier (reglas de negocio, transacciones)
    |
    | JPA / EntityManager
    v
Pedido / Producto (JPA)    <-- Dominio / persistencia
    |
    v
java:jboss/datasources/ExampleDS
    |
    v
H2 (EIS / Datos)
```

La JSP (`/WEB-INF/views/pedidos.jsp`) es la única vista. Se renderiza vía
`forward` desde `PedidoServlet#doGet()`, con JSTL + EL, y contiene el
JavaScript que dispara las solicitudes PUT/DELETE.

## Funcionalidades implementadas

| Operación   | Método HTTP | Endpoint          | Handler                                    |
|-------------|-------------|-------------------|---------------------------------------------|
| Listar      | GET         | `/pedidos`        | `PedidoServlet#doGet()`                     |
| Registrar   | POST        | `/pedidos`        | `PedidoServlet#doPost()` (formulario HTML)  |
| **Editar**  | **PUT**     | `/pedidos/{id}`   | `PedidoServlet#doPut()` (fetch, JSON)       |
| **Eliminar**| **DELETE**  | `/pedidos/{id}`   | `PedidoServlet#doDelete()` (fetch)          |

`doPut`/`doDelete` se invocan realmente con `fetch(url, { method: "PUT" })` y
`fetch(url, { method: "DELETE" })` desde `pedidos.jsp` — no son solo métodos
declarados: el navegador envía los verbos HTTP reales, verificable en
DevTools → Network.

### Reglas de negocio (`PedidoService`)

- `actualizarPedido(id, cliente, productoId, cantidad)`:
  1. Repone al producto original el stock reservado por el pedido.
  2. Descuenta del producto final (el mismo u otro nuevo) la nueva cantidad,
     validando stock disponible.
  3. Recalcula el total y aplica los cambios sobre la entidad `Pedido`
     mediante `Pedido#editar(...)` (sin exponer setters sueltos).
  4. Todo ocurre en una única transacción `REQUIRED`. Si la validación falla,
     se lanza `PedidoException`/`PedidoNotFoundException`
     (`@ApplicationException(rollback = true)`), lo que revierte **toda** la
     transacción — la reposición de stock del paso 1 tampoco se persiste, por
     lo que no quedan cambios parciales.
- `eliminarPedido(id)`: repone el stock del producto asociado y luego elimina
  el pedido, en la misma transacción.
- ID inexistente → `PedidoNotFoundException` → Servlet responde **404**.
- Datos inválidos / stock insuficiente → `PedidoException` → Servlet responde
  **400**.

## Requisitos

- JDK 21
- Maven 3.9+
- Acceso a Internet la primera vez (para provisionar WildFly)
- Puerto 8081 disponible

## Ejecutar

```bash
mvn clean wildfly:run
```

Abrir:

```text
http://localhost:8081/sistema-pedidos/pedidos
```

Para detener: `Ctrl + C`.

## Cómo probar (los 5 casos de la evaluación)

1. **Editar solo el cliente**: clic en "Editar" sobre un pedido, cambiar solo
   el campo Cliente y "Guardar (PUT)". Verificar que producto, cantidad,
   total y stock no cambian.
2. **Editar cantidad, mismo producto**: cambiar la cantidad y guardar.
   Verificar que el stock del producto se ajusta correctamente (no se
   descuenta la cantidad nueva completa, se compensa la reservada antes).
3. **Cambiar el producto del pedido**: en el formulario de edición, elegir
   otro producto. Verificar que el stock del producto original se repone y el
   del nuevo se descuenta.
4. **Edición inválida**: intentar una cantidad mayor al stock disponible.
   Debe verse el mensaje de error y ni el pedido ni el stock deben cambiar.
   Probar también editando un ID inexistente (por ejemplo, con DevTools o
   Postman contra `/pedidos/9999`) y verificar `404`.
5. **Eliminar pedido**: clic en "Eliminar", confirmar. Verificar que el stock
   se repone antes de que el pedido desaparezca del listado.

En **DevTools → Network**, cada edición muestra una solicitud `PUT
/sistema-pedidos/pedidos/{id}` y cada eliminación una `DELETE
/sistema-pedidos/pedidos/{id}`, con el código de respuesta (`200`, `400` o
`404`) y el cuerpo JSON de la respuesta.

## Flujo técnico de una edición (PUT)

1. El navegador ejecuta `fetch(".../pedidos/5", { method: "PUT", body: JSON })`.
2. `PedidoServlet#doPut()` recibe la solicitud, extrae el ID de la ruta
   (`getPathInfo()`) y parsea el cuerpo JSON con `jakarta.json`.
3. Delega en `PedidoService#actualizarPedido(...)`, que ejecuta la regla de
   negocio dentro de una transacción JTA.
4. `EntityManager` actualiza `Pedido` y `Producto` vía JPA/Hibernate contra
   H2. Al finalizar la transacción se hace commit (o rollback si hubo error).
5. El Servlet responde JSON (`200` con el pedido actualizado, `400`/`404` con
   el mensaje de error).
6. El JavaScript de `pedidos.jsp` lee la respuesta, guarda el mensaje y
   recarga la página (`GET /pedidos`), que vuelve a pasar por
   `PedidoServlet#doGet()` → `pedidos.jsp` con los datos actualizados.

La eliminación (`DELETE`) sigue el mismo recorrido, delegando en
`PedidoService#eliminarPedido(...)`.

## forward vs. redirect

- `doGet()` usa **forward** (`RequestDispatcher#forward`) hacia la JSP: la
  petición se procesa en el servidor y el navegador nunca ve la URL de la
  JSP ni hace una nueva solicitud; el HTML resultante se genera del lado del
  servidor y se envía como respuesta a la solicitud original. Por eso el
  navegador "recibe HTML" aunque la JSP jamás se ejecuta ahí.
- `doPost()` (registrar) usa **redirect** (`sendRedirect`) siguiendo el
  patrón PRG (Post/Redirect/Get): el servidor responde con `302` y una nueva
  URL; el navegador hace una segunda solicitud GET a esa URL. Esto evita que,
  al recargar la página, el formulario se reenvíe.

## Base de datos

`persistence.xml` usa `drop-and-create`: los datos (incluidos los 3 productos
iniciales — Laptop, Monitor, Teclado) se reinician en cada despliegue.

## Estructura

```text
src/main/java/pe/edu/isil/pedidos/
├── domain/
│   ├── Pedido.java            (+ editar(...))
│   └── Producto.java          (+ reponerStock(...))
├── service/
│   ├── PedidoException.java
│   ├── PedidoNotFoundException.java  (nuevo — 404)
│   └── PedidoService.java     (+ buscarPedido, actualizarPedido, eliminarPedido)
└── web/
    └── PedidoServlet.java     (+ doPut, doDelete; mapeo /pedidos/*)

src/main/webapp/WEB-INF/views/
└── pedidos.jsp                (acciones Editar/Eliminar + fetch PUT/DELETE)
```

## Repositorio

URL del repositorio individual: _(completar con tu URL de GitHub/GitLab una
vez subido)_.
