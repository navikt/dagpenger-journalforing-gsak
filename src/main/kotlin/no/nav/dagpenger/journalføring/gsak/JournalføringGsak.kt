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
import no.nav.dagpenger.streams.kbranch
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

        val (needsNewGsakSak, hasExistingGsakSak) = inngåendeJournalposter
            .peek { key, value -> LOGGER.info("Processing ${value.javaClass} with key $key") }
            .filter { _, behov -> shouldBeProcessed(behov) }
            .kbranch(
                { _, behov -> behov.isNySoknad() },
                { _, behov -> behov.isGjenopptakSoknad() || behov.isEttersending() })

        needsNewGsakSak.mapValues(this::createSak)

        hasExistingGsakSak.mapValues(this::findSak)

        needsNewGsakSak.merge(hasExistingGsakSak)
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

    private fun createSak(behov: Behov): Behov {
        val sak = gsakClient.createSak(
                behov.getMottaker().getIdentifikator(),
                behov.getFagsakId(),
                behov.getBehovId())

        behov.setGsaksakId(sak.id.toString())
        return behov
    }

    private fun findSak(behov: Behov): Behov{
        val saker = gsakClient.findSak(
                behov.getMottaker().getIdentifikator(),
                behov.getBehovId())

        //TODO: Find correct sak
        behov.setGsaksakId(saker[0].id.toString())
        return behov
    }
}

fun shouldBeProcessed(behov: Behov): Boolean =
        !behov.getTrengerManuellBehandling() && behov.hasFagsakId() && !behov.hasGsakId()

