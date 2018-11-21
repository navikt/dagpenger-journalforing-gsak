package no.nav.dagpenger.journalføring.gsak

interface GsakClient {
    fun findSak(aktoerId: String, correlationId: String): List<GsakSak>

    fun createSak(aktoerId: String, fagsakId: String, correlationId: String): GsakSak
}