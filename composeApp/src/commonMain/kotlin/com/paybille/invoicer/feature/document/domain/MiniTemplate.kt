package com.paybille.invoicer.feature.document.domain

/**
 * Un Mustache mínimo para la plantilla de la factura. Lo justo para que una plantilla guardada
 * como texto (`invoiceconfig.TemplateHtml`) se pueda escribir sin tocar la app:
 *
 * - `{{campo}}` y `{{a.b}}` — valor escapado para HTML.
 * - `{{{campo}}}` — valor tal cual (el SVG de la firma, el logo en `data:`).
 * - `{{#campo}}…{{/campo}}` — si es lista, repite con cada elemento; si es verdadero (texto no
 *   vacío, `true`, un mapa), pinta una vez. `{{^campo}}…{{/campo}}` es lo contrario.
 *
 * Los valores son `Map<String, Any?>`, `List<*>`, `String`, `Boolean` o números. Una clave
 * que no existe se busca en el contexto de fuera (como en Mustache) y, si tampoco, es vacía.
 */
object MiniTemplate {

    fun render(template: String, data: Map<String, Any?>): String {
        val nodes = parse(template)
        return buildString { renderNodes(nodes, listOf(data), this) }
    }

    private sealed interface Node
    private data class Text(val text: String) : Node
    private data class Var(val path: String, val raw: Boolean) : Node
    private data class Section(val path: String, val inverted: Boolean, val children: List<Node>) : Node

    private fun parse(template: String): List<Node> {
        val root = mutableListOf<Node>()
        // Pila de secciones abiertas: (nombre, invertida, hijos).
        val stack = ArrayDeque<Triple<String, Boolean, MutableList<Node>>>()
        fun current(): MutableList<Node> = stack.lastOrNull()?.third ?: root

        var i = 0
        while (i < template.length) {
            val open = template.indexOf("{{", i)
            if (open < 0) {
                current() += Text(template.substring(i))
                break
            }
            if (open > i) current() += Text(template.substring(i, open))
            val triple = template.startsWith("{{{", open)
            val close = template.indexOf(if (triple) "}}}" else "}}", open)
            if (close < 0) {
                // Llave sin cerrar: se deja como texto.
                current() += Text(template.substring(open))
                break
            }
            val tag = template.substring(open + if (triple) 3 else 2, close).trim()
            i = close + if (triple) 3 else 2
            when {
                triple -> current() += Var(tag, raw = true)
                tag.startsWith("#") || tag.startsWith("^") -> stack.addLast(Triple(tag.drop(1).trim(), tag[0] == '^', mutableListOf()))
                tag.startsWith("/") -> {
                    val name = tag.drop(1).trim()
                    val section = stack.removeLastOrNull()
                    if (section == null || section.first != name) {
                        // Cierre que no corresponde: se ignora en vez de romper la factura.
                        section?.let { stack.addLast(it) }
                    } else {
                        current() += Section(section.first, section.second, section.third)
                    }
                }
                tag.startsWith("!") -> Unit // comentario
                tag.startsWith("&") -> current() += Var(tag.drop(1).trim(), raw = true)
                else -> current() += Var(tag, raw = false)
            }
        }
        // Secciones sin cerrar: se cierran al final.
        while (stack.isNotEmpty()) {
            val section = stack.removeLast()
            current() += Section(section.first, section.second, section.third)
        }
        return root
    }

    private fun renderNodes(nodes: List<Node>, scopes: List<Any?>, out: StringBuilder) {
        for (node in nodes) {
            when (node) {
                is Text -> out.append(node.text)
                is Var -> {
                    val value = lookup(node.path, scopes)
                    val text = stringOf(value)
                    out.append(if (node.raw) text else escapeHtml(text))
                }
                is Section -> {
                    val value = lookup(node.path, scopes)
                    if (node.inverted) {
                        if (!truthy(value)) renderNodes(node.children, scopes, out)
                    } else when (value) {
                        is List<*> -> value.forEach { item -> renderNodes(node.children, scopes + listOf(item), out) }
                        is Map<*, *> -> if (value.isNotEmpty()) renderNodes(node.children, scopes + listOf(value), out)
                        else -> if (truthy(value)) renderNodes(node.children, scopes, out)
                    }
                }
            }
        }
    }

    private fun lookup(path: String, scopes: List<Any?>): Any? {
        if (path == ".") return scopes.lastOrNull()
        val parts = path.split('.')
        for (scope in scopes.asReversed()) {
            if (scope !is Map<*, *> || !scope.containsKey(parts[0])) continue
            var value: Any? = scope[parts[0]]
            for (part in parts.drop(1)) value = (value as? Map<*, *>)?.get(part)
            return value
        }
        return null
    }

    private fun truthy(value: Any?): Boolean = when (value) {
        null -> false
        is Boolean -> value
        is String -> value.isNotEmpty()
        is List<*> -> value.isNotEmpty()
        is Map<*, *> -> value.isNotEmpty()
        is Number -> value.toDouble() != 0.0
        else -> true
    }

    private fun stringOf(value: Any?): String = when (value) {
        null -> ""
        is Double -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        else -> value.toString()
    }

    fun escapeHtml(text: String): String = buildString(text.length) {
        for (c in text) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}
