package com.paybille.invoicer.core.network

/**
 * Único tipo de error que sale de `core/network`. El mensaje ya está en español y listo
 * para enseñarlo: quien llama decide DÓNDE mostrarlo (el POS lo abría desde el
 * interceptor, y un aviso lanzado desde ahí no sabe si la pantalla sigue viva).
 */
class ApiException(
    message: String,
    val kind: Kind,
    val status: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {

    enum class Kind {
        /** Sin red, DNS o servidor caído. Para offline first es "sigue con lo local". */
        Network,
        Timeout,

        /** El servidor respondió, pero con error (4xx/5xx o un mensaje de rechazo). */
        Server,

        /** Respuesta con una forma que no esperábamos. */
        Unexpected,
    }

    val isConnectivity: Boolean get() = kind == Kind.Network || kind == Kind.Timeout
}
