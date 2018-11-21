package no.nav.dagpenger.journalføring.gsak

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomakehurst.wiremock.matching.EqualToJsonPattern
import no.nav.dagpenger.oidc.OidcClient
import no.nav.dagpenger.oidc.OidcToken
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals

class GsakHttpClientTest {

    @Rule
    @JvmField
    var wireMockRule = WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort())

    class DummyOidcClient : OidcClient {
        override fun oidcToken(): OidcToken = OidcToken(UUID.randomUUID().toString(), "openid", 3000)
    }

    @Test
    fun `Find sak`() {

        val aktoerId = "12345678912"
        val tema = "DAG"

        stubFor(
            WireMock.get(WireMock.urlEqualTo("/v1/saker?aktoerId=$aktoerId&tema=$tema"))
                .willReturn(
                    WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            [{
                                "id": 12345,
                                "tema": "DAG",
                                "applikasjon": "IT01",
                                "aktoerId": "10038999999",
                                "fagsakNr": "string"
                            }]
                        """.trimIndent()
                        )
                )
        )

        val response =
                GsakHttpClient(wireMockRule.url(""), DummyOidcClient()).findSak(
                        aktoerId, "123")

        assertEquals(12345, response[0].id)
    }

    @Test(expected = GsakException::class)
    fun `Find sak request throws exception on error`() {

        val aktoerId = "12345678912"
        val tema = "DAG"

        stubFor(
            WireMock.get(WireMock.urlEqualTo("/v1/saker?aktoerId=$aktoerId&tema=$tema"))
                .willReturn(
                    WireMock.serverError()
                )
        )

        val response =
                GsakHttpClient(wireMockRule.url(""), DummyOidcClient()).findSak(
                        aktoerId, "456")
    }

    @Test
    fun `Create sak`() {

        val aktoerId = "12345678912"
        val tema = "DAG"
        val fagsakId = "fagsak"

        stubFor(
            WireMock.post(WireMock.urlEqualTo("/v1/saker"))
                .withRequestBody(
                    EqualToJsonPattern(
                        """
                         {
                            "tema": "$tema",
                            "aktoerId": "$aktoerId",
                            "fagsakNr": "$fagsakId"
                        }
                    """.trimIndent(), true, true
                )
            )
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                            """
                        {
                            "id": 12345,
                            "tema": "$tema",
                            "applikasjon": "IT01",
                            "aktoerId": "$aktoerId",
                            "fagsakNr": "$fagsakId"
                        }
                    """.trimIndent()
                                    )
                    )
        )

        val response =
                GsakHttpClient(wireMockRule.url(""), DummyOidcClient()).createSak(
                        aktoerId, fagsakId, "123")

        assertEquals(12345, response.id)
        assertEquals(fagsakId, response.fagsakNr)
    }

    @Test(expected = GsakException::class)
    fun `Createsak request throws exception on error`() {

        val aktoerId = "12345678912"
        val tema = "DAG"
        val fagsakId = "fagsak"

        stubFor(
                WireMock.post(WireMock.urlEqualTo("/v1/saker"))
                        .willReturn(
                                WireMock.serverError()
                        )
        )

        val response =
                GsakHttpClient(wireMockRule.url(""), DummyOidcClient()).createSak(
                        aktoerId, fagsakId, "456")
    }
}