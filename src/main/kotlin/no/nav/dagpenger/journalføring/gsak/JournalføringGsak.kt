package no.nav.dagpenger.journalføring.gsak

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import mu.KotlinLogging
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.avro.Journalpost
import no.nav.dagpenger.events.avro.JournalpostType
import no.nav.dagpenger.events.avro.JournalpostType.ETTERSENDING
import no.nav.dagpenger.events.avro.JournalpostType.GJENOPPTAK
import no.nav.dagpenger.events.avro.JournalpostType.MANUELL
import no.nav.dagpenger.events.avro.JournalpostType.NY
import no.nav.dagpenger.events.avro.JournalpostType.UKJENT
import no.nav.dagpenger.oidc.StsOidcClient
import no.nav.dagpenger.streams.KafkaCredential
import no.nav.dagpenger.streams.Service
import no.nav.dagpenger.streams.Topics.INNGÅENDE_JOURNALPOST
import no.nav.dagpenger.streams.configureAvroSerde
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
        val innkommendeJournalpost = INNGÅENDE_JOURNALPOST.copy(
                valueSerde = configureAvroSerde<Behov>(
                        mapOf(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to env.schemaRegistryUrl)
                )
        )

        val builder = StreamsBuilder()
        val inngåendeJournalposter = builder.consumeTopic(innkommendeJournalpost)

        inngåendeJournalposter
            .peek { key, value -> LOGGER.info("Processing ${value.javaClass} with key $key") }
            .filter { _, behov -> behov.getJournalpost().getFagsakId() != null }
            .filter { _, behov -> behov.getJournalpost().getGsaksakId() == null }
            .filter { _, behov -> filterJournalpostTypes(behov.getJournalpost().getJournalpostType()) }
            .mapValues(this::addGsakSakId)
            .peek { key, value -> LOGGER.info("Producing ${value.javaClass} with key $key") }
            .toTopic(innkommendeJournalpost)

        return KafkaStreams(builder.build(), this.getConfig())
    }

    override fun getConfig(): Properties {
        return streamConfig(
            appId = SERVICE_APP_ID,
            bootStapServerUrl = env.bootstrapServersUrl,
            credential = KafkaCredential(env.username, env.password)
        )
    }

    private fun filterJournalpostTypes(journalpostType: JournalpostType): Boolean {
        return when (journalpostType) {
            NY, GJENOPPTAK, ETTERSENDING -> true
            UKJENT, MANUELL -> false
        }
    }

    private fun addGsakSakId(behov: Behov): Behov {
        val journalpost = behov.getJournalpost()

        val sakId = when (journalpost.getJournalpostType()) {
            NY -> createSak(journalpost, behov.getBehovId())
            ETTERSENDING, GJENOPPTAK -> findSak(journalpost, behov.getBehovId())
            else -> throw UnexpectedJournaltypeException("Unexpected journalposttype ${journalpost.getJournalpostType()}")
        }

        journalpost.setGsaksakId(sakId)

        return behov
    }

    private fun createSak(journalpost: Journalpost, correlationId: String): String {
        val sak = gsakClient.createSak(
                journalpost.getSøker().getIdentifikator(),
                journalpost.getFagsakId(),
                correlationId)

        return sak.id.toString()
    }

    private fun findSak(journalpost: Journalpost, correlationId: String): String {
        val saker = gsakClient.findSak(
                journalpost.getSøker().getIdentifikator(),
                correlationId)

        //TODO: Find correct sak
        return saker[0].id.toString()
    }
}

class UnexpectedJournaltypeException(override val message: String) : RuntimeException(message)