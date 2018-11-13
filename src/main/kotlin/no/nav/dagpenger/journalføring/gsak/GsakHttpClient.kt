package no.nav.dagpenger.journalføring.gsak

import com.github.kittinunf.fuel.gson.responseObject
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import com.google.gson.Gson
import no.nav.dagpenger.oidc.OidcClient

const val TEMA_DAGPENGER = "DAG"

class GsakHttpClient(private val gsakUrl: String, private val oidcClient: OidcClient) {

    fun findSak(aktoerId: String, correlationId: String): GsakSak {
        val url = "${gsakUrl}v1/saker"
        val params = listOf("aktoerId" to aktoerId, "tema" to TEMA_DAGPENGER)
        val (_, response, result) = with(url.httpGet(params)) {
            header("Authorization" to oidcClient.oidcToken().access_token.toBearerToken())
            header("X-Correlation-ID" to correlationId)
            responseObject<GsakSak>()
        }

        return when (result) {
            is Result.Failure -> throw GsakException(
                    response.statusCode, response.responseMessage, result.getException())
            is Result.Success -> result.get()
        }
    }

    fun createSak(aktoerId: String, fagsakId: String, correlationId: String): GsakSak {
        val url = "${gsakUrl}v1/saker"
        val request = GsakSak(tema = TEMA_DAGPENGER, aktoerId = aktoerId, fagsakNr = fagsakId)
        val json = Gson().toJson(request).toString()
        val (_, response, result) = with(url.httpPost().body(json)) {
            header("Authorization" to oidcClient.oidcToken().access_token.toBearerToken())
            header("X-Correlation-ID" to correlationId)
            responseObject<GsakSak>()
        }

        return when (result) {
            is Result.Failure -> throw GsakException(
                    response.statusCode, response.responseMessage, result.getException())
            is Result.Success -> result.get()
        }
    }
}

fun String.toBearerToken() = "Bearer $this"

data class GsakSak(
    val id: Int? = null,
    val tema: String,
    val applikasjon: String? = null,
    val aktoerId: String? = null,
    val orgnr: String? = null,
    val fagsakNr: String? = null,
    val opprettetAv: String? = null,
    val opprettetTidspunkt: String? = null
)

class GsakException(val statusCode: Int, override val message: String, override val cause: Throwable) : RuntimeException(message, cause)
