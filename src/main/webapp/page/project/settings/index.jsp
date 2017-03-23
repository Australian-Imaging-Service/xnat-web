<%@ page session="true" contentType="text/html" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="pg" tagdir="/WEB-INF/tags/page" %>

<pg:wrapper>
    <pg:xnat>

        <c:set var="incl" value="content.jsp"/>

        <c:if test="${not empty param.view}">
            <c:set var="incl" value="/page/${param.view}/content.jsp"/>
        </c:if>

        <jsp:include page="${incl}"/>

        <jsp:include page="content.jsp"/>

    </pg:xnat>
</pg:wrapper>
