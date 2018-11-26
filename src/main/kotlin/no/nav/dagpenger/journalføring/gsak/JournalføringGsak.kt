package no.nav.dagpenger.journalføring.gsak


import mu.KotlinLogging
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.avro.Journalpost
import no.nav.dagpenger.events.hasFagsakId
import no.nav.dagpenger.events.hasGsakId
import no.nav.dagpenger.events.isEttersending
import no.nav.dagpenger.events.isGjenopptakSoknad
import no.nav.dagpenger.events.isNySoknad
import no.nav.dagpenger.oidc.StsOidcClient
import no.nav.dagpenger.streams.KafkaCredential
import no.nav.dagpenger.streams.Service
import no.nav.dagpenger.streams.Topics.INNGÅENDE_JOURNALPOST
import no.nav.dagpenger.streams.consumeTopic
import no.nav.dagpenger.streams.streamConfig
import no.nav.dagpenger.streams.toTopic
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import java.util.Properties

private val LOGGER = KotlinLogging.logger {}

class JournalføringGsak(val env: Environment, val gsakClient: GsakClient) : Service() {
    override val SERVICE_APP_ID = "journalføring-gsak"

    override val HTTP_PORT: Int = env.httpPort ?: super.HTTP_PORT

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val env = Environment()
            val service = JournalføringGsak(
                    env, GsakHttpClient(env.gsakSakUrl, StsOidcClient(env.oicdStsUrl, env.username, env.password)))
            service.start()
        }
    }

    override fun setupStreams(): KafkaStreams {
        LOGGER.info { "Initiating start of $SERVICE_APP_ID" }

        val builder = StreamsBuilder()
        val inngåendeJournalposter = builder.consumeTopic(INNGÅENDE_JOURNALPOST, env.schemaRegistryUrl)

        inngåendeJournalposter
            .peek { key, value -> LOGGER.info("Processing ${value.javaClass} with key $key") }
            .filter { _, behov -> shouldBeProcessed(behov) }
            .mapValues(this::addGsakSakId)
            .peek { key, value -> LOGGER.info("Producing ${value.javaClass} with key $key") }
            .toTopic(INNGÅENDE_JOURNALPOST, env.schemaRegistryUrl)

        return KafkaStreams(builder.build(), this.getConfig())
    }

    override fun getConfig(): Properties {
        return streamConfig(
            appId = SERVICE_APP_ID,
            bootStapServerUrl = env.bootstrapServersUrl,
            credential = KafkaCredential(env.username, env.password)
        )
    }

    private fun addGsakSakId(behov: Behov): Behov {
        val sakId = when {
            behov.isNySoknad() -> createSak(behov, behov.getBehovId())
            behov.isEttersending() || behov.isGjenopptakSoknad() -> findSak(behov, behov.getBehovId())
            else -> throw UnexpectedHenvendelsesTypeException("Unexpected henvendelsestype")
        }

        behov.setGsaksakId(sakId)

        return behov
    }

    private fun createSak(behov: Behov, correlationId: String): String {
        val sak = gsakClient.createSak(
                behov.getMottaker().getIdentifikator(),
                behov.getFagsakId(),
                correlationId)

        return sak.id.toString()
    }

    private fun findSak(behov: Behov, correlationId: String): String {
        val saker = gsakClient.findSak(
                behov.getMottaker().getIdentifikator(),
                correlationId)

        //TODO: Find correct sak
        return saker[0].id.toString()
    }
}

fun shouldBeProcessed(behov: Behov): Boolean =
        !behov.getTrengerManuellBehandling() && behov.hasFagsakId() && !behov.hasGsakId()

class UnexpectedHenvendelsesTypeException (override val message: String) : RuntimeException(message)