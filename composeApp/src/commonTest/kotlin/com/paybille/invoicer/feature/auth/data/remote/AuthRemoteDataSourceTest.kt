package com.paybille.invoicer.feature.auth.data.remote

import com.paybille.invoicer.core.network.ApiConfig
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.core.network.PayBilleApi
import com.paybille.invoicer.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contrato con la API de PayBille. Las respuestas son copias de lo que devuelve
 * `PayBille_API` (controllers/users.js + formatResponseMiddleware): si el backend cambia
 * de forma, estas pruebas lo dicen antes que el usuario.
 */
class AuthRemoteDataSourceTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun source(
        storedToken: String? = null,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): AuthRemoteDataSource {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        val client = createHttpClient(tokenProvider = { storedToken }, engine = engine)
        return AuthRemoteDataSource(PayBilleApi(client, ApiConfig(baseUrl = BASE, apiKey = "clave")))
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.jsonBody(): JsonObject =
        Json.parseToJsonElement((body as TextContent).text).jsonObject

    @Test
    fun loginCorrectoDevuelveTokenYUsuarioSinContrasena() = runTest {
        val remote = source {
            json(
                """{"data":{"user":{"id":7,"Username":"ana","Password":"secreta","IdPerson":3,""" +
                    """"IdRol":1,"IdMarket":12,"Torning":5},"token":"jwt-abc"},"meta":null}""",
            )
        }

        val result = remote.login("ana", "secreta", idMarket = null)

        val auth = assertIs<RemoteLogin.Authenticated>(result)
        assertEquals("jwt-abc", auth.token)
        assertEquals(12, auth.user.idMarket)
        assertEquals("5", auth.user.torning) // número en la respuesta, string en la tabla

        val request = requests.single()
        // Igual que `get()` del POS: POST con ?page&pageSize aunque el login no pagine.
        assertEquals("/ventex/api/users/login", request.url.encodedPath)
        val body = request.jsonBody()
        assertEquals("clave", body["key"]?.jsonPrimitive?.content)
        assertTrue(body["isGet"]!!.jsonPrimitive.boolean)
        assertFalse("IdMarket" in body, "La primera fase no manda tienda")
        assertFalse("RapidLogin" in body, "Nunca se toca la preferencia de login rápido del POS")
    }

    @Test
    fun credencialesMalasLleganComoStringConHttp200() = runTest {
        val remote = source { json("""{"data":"Incorrect username or password","meta":null}""") }

        assertEquals(RemoteLogin.InvalidCredentials, remote.login("ana", "mala", idMarket = null))
    }

    @Test
    fun variasTiendasPidenElegirYLaSegundaFaseMandaIdMarket() = runTest {
        val remote = source { request ->
            val body = request.jsonBody()
            if ("IdMarket" in body) {
                json("""{"data":{"user":{"id":7,"IdPerson":3,"IdRol":1,"IdMarket":20},"token":"jwt-20"}}""")
            } else {
                json(
                    """{"data":{"requiresMarket":true,"user":{"id":7,"Username":"ana"},"markets":[""" +
                        """{"id":12,"Name":"Colmado Ana","Address":"Calle 1","Image":null,"TimeZone":null},""" +
                        """{"id":20,"Name":"Ferretería Ana"}]}}""",
                )
            }
        }

        val first = assertIs<RemoteLogin.MarketRequired>(remote.login("ana", "x", idMarket = null))
        assertEquals(listOf(12, 20), first.markets.map { it.id })

        val second = assertIs<RemoteLogin.Authenticated>(remote.login("ana", "x", idMarket = 20))
        assertEquals("jwt-20", second.token)
        assertEquals(20, requests.last().jsonBody()["IdMarket"]!!.jsonPrimitive.int)
    }

    @Test
    fun tiendaNoPermitidaEsErrorDelServidorConSuMensaje() = runTest {
        val remote = source { json("""{"data":{"error":"No tienes acceso a esta tienda"}}""", HttpStatusCode.Forbidden) }

        val error = assertFailsWith<ApiException> { remote.login("ana", "x", idMarket = 99) }
        assertEquals(ApiException.Kind.Server, error.kind)
        assertEquals(403, error.status)
        assertEquals("No tienes acceso a esta tienda", error.message)
    }

    @Test
    fun sinRedEsErrorDeConectividad() = runTest {
        val remote = source { throw kotlinx.io.IOException("sin red") }

        val error = assertFailsWith<ApiException> { remote.login("ana", "x", idMarket = null) }
        assertTrue(error.isConnectivity)
    }

    @Test
    fun tiendaNormalizaTaxValueQueLlegaComoString() = runTest {
        val remote = source(storedToken = "guardado") {
            json("""{"data":{"id":12,"Name":"Colmado Ana","taxValue":"0.18","taxLabel":"ITBIS","Active":1}}""")
        }

        val market = remote.market(12, token = "explicito")

        assertEquals(0.18, market.taxValue)
        val request = requests.single()
        assertEquals("$BASE/generic/markets/12", request.url.toString())
        // El token explícito (cascada del login) gana al guardado, y va SIN "Bearer ".
        assertEquals("explicito", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun peticionNormalUsaElTokenGuardadoSinBearer() = runTest {
        val remote = source(storedToken = "guardado") { json("""{"data":{"id":3,"FirstName":"Ana"}}""") }

        remote.person(3, token = null)

        assertEquals("guardado", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun ajustesFiltranPorParamsYToleranBooleanosNumericos() = runTest {
        val remote = source {
            json("""{"data":[{"id":4,"IdMarket":"12","Tax":"18.00","LogoInBill":1}],"meta":null}""")
        }

        val settings = remote.settings(12, token = null)!!

        assertEquals(18.0, settings.tax)
        assertEquals(true, settings.logoInBill)
        val request = requests.single()
        assertEquals("$BASE/generic/get/Settings?page=1&pageSize=10", request.url.toString())
        assertEquals(12, request.jsonBody()["params"]!!.jsonObject["IdMarket"]!!.jsonPrimitive.int)
    }

    @Test
    fun tiendaSinAjustesDevuelveNull() = runTest {
        val remote = source { json("""{"data":[],"meta":null}""") }

        assertNull(remote.settings(12, token = null))
    }

    @Test
    fun misTiendasLeeLasActivasYLaTiendaDelUsuario() = runTest {
        // controllers/marketByUser.js → mine. SIN sobre `{ data }`: server.js solo envuelve los GET
        // y los POST con `isGet`, y esta ruta no lo manda.
        val remote = source(storedToken = "guardado") {
            json(
                """{"markets":[{"id":12,"Name":"Colmado Ana","Address":"Calle 1","Image":null,""" +
                    """"TimeZone":"America/Santo_Domingo"},{"id":15,"Name":"Sucursal Norte","Address":null,""" +
                    """"Image":null,"TimeZone":null}],"IdMarket":"12","IndRapidLogin":true}""",
            )
        }

        val mine = remote.myMarkets()

        assertEquals(listOf(12, 15), mine.markets.map { it.id })
        assertEquals("Sucursal Norte", mine.markets[1].name)
        assertEquals(12, mine.idMarket)
        val request = requests.single()
        assertEquals("$BASE/marketbyuser/mine", request.url.toString())
        assertEquals("guardado", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun cambiarDeTiendaMandaSoloIdMarketYDevuelveTokenNuevo() = runTest {
        val remote = source(storedToken = "viejo") {
            // Sin sobre, igual que `mine`.
            json(
                """{"user":{"id":7,"Username":"ana","Password":"secreta","IdPerson":3,"IdRol":1,""" +
                    """"IdMarket":15,"Torning":null},"token":"jwt-nuevo"}""",
            )
        }

        val auth = remote.switchMarket(15)

        assertEquals("jwt-nuevo", auth.token)
        assertEquals(15, auth.user.idMarket)
        val request = requests.single()
        assertEquals("$BASE/marketbyuser/switch", request.url.toString())
        // El cambio se autoriza con el token de la tienda actual.
        assertEquals("viejo", request.headers[HttpHeaders.Authorization])
        val body = request.jsonBody()
        assertEquals(15, body["IdMarket"]!!.jsonPrimitive.int)
        // RapidLogin es la preferencia del POS: si viaja, el backend la sobrescribe.
        assertFalse("RapidLogin" in body)
    }

    @Test
    fun cambiarATiendaAjenaEsErrorDelServidorConSuMensaje() = runTest {
        val remote = source(storedToken = "viejo") {
            json("""{"error":"No tienes acceso a esta tienda"}""", HttpStatusCode.Forbidden)
        }

        val error = assertFailsWith<ApiException> { remote.switchMarket(99) }

        assertEquals(403, error.status)
        assertEquals("No tienes acceso a esta tienda", error.message)
        assertFalse(error.isConnectivity)
    }

    private companion object {
        const val BASE = "https://api.test/ventex/api"
    }
}
