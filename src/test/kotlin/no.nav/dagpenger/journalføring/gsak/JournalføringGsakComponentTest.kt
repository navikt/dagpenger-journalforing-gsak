package no.nav.dagpenger.journalføring.gsak

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import mu.KotlinLogging
import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.common.embeddedutils.getAvailablePort
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.avro.Journalpost
import no.nav.dagpenger.events.avro.JournalpostType
import no.nav.dagpenger.events.avro.Søker
import no.nav.dagpenger.streams.Topics
import no.nav.dagpenger.streams.Topics.INNGÅENDE_JOURNALPOST
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.time.Duration
import java.util.Properties
import kotlin.test.assertEquals

class JournalføringGsakComponentTest {

    private val LOGGER = KotlinLogging.logger {}

    companion object {
        private const val username = "srvkafkaclient"
        private const val password = "kafkaclient"

        val embeddedEnvironment = KafkaEnvironment(
            users = listOf(JAASCredential(username, password)),
            autoStart = false,
            withSchemaRegistry = true,
            withSecurity = true,
            topics = listOf(Topics.INNGÅENDE_JOURNALPOST.name)
        )

        @BeforeClass
        @JvmStatic
        fun setup() {
            embeddedEnvironment.start()
        }

        @AfterClass
        @JvmStatic
        fun teardown() {
            embeddedEnvironment.tearDown()
        }
    }

    @Test
    fun ` embedded kafka cluster is up and running `() {
        kotlin.test.assertEquals(embeddedEnvironment.serverPark.status, KafkaEnvironment.ServerParkStatus.Started)
    }

    @Test
    fun ` Component test of JournalføringGsak`() {

        //Test data: [hasFagsakId, hasGsaksakId]
        val innkommendeBehov = listOf(
            listOf(true, true),
            listOf(true, false),
            listOf(true, true),
            listOf(true, true),
            listOf(true, false),
            listOf(true, true),
            listOf(true, false),
            listOf(true, true),
            listOf(false, false),
            listOf(true, true)
        )

        val behovsToProcess = innkommendeBehov.filter { it[0] && !it[1] }.size
        val behovsMissingData = innkommendeBehov.filter { !it[0] && !it[1] }.size

        // given an environment
        val env = Environment(
            gsakSakUrl = "local",
            oicdStsUrl = "local",
            username = username,
            password = password,
            bootstrapServersUrl = embeddedEnvironment.brokersURL,
            schemaRegistryUrl = embeddedEnvironment.schemaRegistry!!.url,
            httpPort = getAvailablePort()
        )

        val ruting = JournalføringGsak(env, DummyGsakClient())

        //produce behov...

        val behovProducer = behovProducer(env)

        ruting.start()

        innkommendeBehov.forEach { testdata ->
            val innkommendeBehov: Behov = Behov
                .newBuilder()
                .setBehovId("123")
                .setJournalpost(
                    Journalpost
                        .newBuilder()
                        .setJournalpostId("12345")
                        .setSøker(Søker("12345678912"))
                        .setJournalpostType(JournalpostType.NY)
                        .setBehandleneEnhet("0000")
                        .setFagsakId(if (testdata[0]) "fagsak" else null)
                        .setGsaksakId(if (testdata[1]) "gsaksak" else null)
                        .build()
                )
                .build()
            val record = behovProducer.send(ProducerRecord(INNGÅENDE_JOURNALPOST.name, innkommendeBehov)).get()
            LOGGER.info { "Produced -> ${record.topic()}  to offset ${record.offset()}" }
        }

        val behovConsumer: KafkaConsumer<String, Behov> = behovConsumer(env)
        val behovsListe = behovConsumer.poll(Duration.ofSeconds(5)).toList()

        ruting.stop()

        //Verify the number of produced messages
        assertEquals(innkommendeBehov.size + behovsToProcess, behovsListe.size)

        //Check if JournalføringGsak sets gsakSakId, by verifing the number of behovs without gsaksakid
        val withoutGsaksakId = behovsListe.filter { kanskjeBehandletBehov ->
            kanskjeBehandletBehov.value().getJournalpost().getGsaksakId() == null
        }.size
        assertEquals(behovsToProcess + behovsMissingData, withoutGsaksakId)
    }

    class DummyGsakClient : GsakClient {
        override fun findSak(aktoerId: String, correlationId: String): List<GsakSak> {
            return listOf(GsakSak(tema = "DAG"))
        }

        override fun createSak(aktoerId: String, fagsakId: String, correlationId: String): GsakSak {
            return GsakSak(tema = "DAG")
        }
    }

    private fun behovProducer(env: Environment): KafkaProducer<String, Behov> {
        val producer: KafkaProducer<String, Behov> = KafkaProducer(Properties().apply {
            put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, env.schemaRegistryUrl)
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, env.bootstrapServersUrl)
            put(ProducerConfig.CLIENT_ID_CONFIG, "dummy-behov-producer")
            put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                Topics.INNGÅENDE_JOURNALPOST.keySerde.serializer().javaClass.name
            )
            put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                Topics.INNGÅENDE_JOURNALPOST.valueSerde.serializer().javaClass.name
            )
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(
                SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${env.username}\" password=\"${env.password}\";"
            )
        })

        return producer
    }

    private fun behovConsumer(env: Environment): KafkaConsumer<String, Behov> {
        val consumer: KafkaConsumer<String, Behov> = KafkaConsumer(Properties().apply {
            put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, env.schemaRegistryUrl)
            put(ConsumerConfig.GROUP_ID_CONFIG, "test-dagpenger-gsak-consumer")
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, env.bootstrapServersUrl)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                INNGÅENDE_JOURNALPOST.keySerde.deserializer().javaClass.name
            )
            put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                INNGÅENDE_JOURNALPOST.valueSerde.deserializer().javaClass.name
            )
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(
                SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${env.username}\" password=\"${env.password}\";"
            )
        })

        consumer.subscribe(listOf(INNGÅENDE_JOURNALPOST.name))
        return consumer
    }
}