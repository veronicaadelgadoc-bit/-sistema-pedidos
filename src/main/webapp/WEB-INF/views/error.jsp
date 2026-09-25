<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!doctype html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <title>Error</title>
</head>
<body>
    <h1>
        No se pudo completar la operación
    </h1>
    <div class="error">
        <c:out value="${error}" default="Ocurrió un error inesperado."/>
    </div>

    <c:url var="pedidosUrl" value="/pedidos"/>

    <a href="${pedidosUrl}"> Volver a pedidos</a>
</body>
</html>