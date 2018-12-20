package no.nav.dagpenger.journalføring.gsak

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import mu.KotlinLogging
import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.common.embeddedutils.getAvailablePort
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.avro.Journalpost
import no.nav.dagpenger.events.avro.Mottaker
import no.nav.dagpenger.events.avro.Søknad
import no.nav.dagpenger.events.avro.Vedtakstype
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
import kotlin.random.Random
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

        val env = Environment(
            gsakSakUrl = "local",
            oicdStsUrl = "local",
            username = username,
            password = password,
            bootstrapServersUrl = embeddedEnvironment.brokersURL,
            schemaRegistryUrl = embeddedEnvironment.schemaRegistry!!.url,
            httpPort = getAvailablePort()
        )

        val gsak = JournalføringGsak(env, DummyGsakClient())

        val behovProducer = behovProducer(env)

        private fun behovProducer(env: Environment): KafkaProducer<String, Behov> {
            val producer: KafkaProducer<String, Behov> = KafkaProducer(Properties().apply {
                put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, env.schemaRegistryUrl)
                put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, env.bootstrapServersUrl)
                put(ProducerConfig.CLIENT_ID_CONFIG, "dummy-behov-producer-${Random.nextInt()}")
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

        @BeforeClass
        @JvmStatic
        fun setup() {
            embeddedEnvironment.start()
            gsak.start()
        }

        @AfterClass
        @JvmStatic
        fun teardown() {
            gsak.stop()
        }
    }

    @Test
    fun ` embedded kafka cluster is up and running `() {
        kotlin.test.assertEquals(embeddedEnvironment.serverPark.status, KafkaEnvironment.ServerParkStatus.Started)
    }

    @Test
    fun ` Processes the right behovs `() {

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

        val behovId = "1"
        innkommendeBehov.forEach { testdata ->
            val innkommendeBehov: Behov = Behov
                .newBuilder()
                .setBehovId(behovId)
                .setMottaker(Mottaker("12345678912"))
                .setBehandleneEnhet("0000")
                .setHenvendelsesType(
                    Søknad.newBuilder().setVedtakstype(Vedtakstype.NY_RETTIGHET).build()
                )
                .setFagsakId(if (testdata[0]) "fagsak" else null)
                .setGsaksakId(if (testdata[1]) "gsaksak" else null)
                .setJournalpost(
                    Journalpost
                        .newBuilder()
                        .setJournalpostId("12345")
                        .build()
                )
                .build()
            val record = behovProducer.send(ProducerRecord(INNGÅENDE_JOURNALPOST.name, innkommendeBehov)).get()
            LOGGER.info { "Produced -> ${record.topic()}  to offset ${record.offset()}" }
        }

        val behovConsumer: KafkaConsumer<String, Behov> = behovConsumer(env, "test-consumer-1")
        val behovsListe = behovConsumer.poll(Duration.ofSeconds(5))
            .filter { record -> record.value().getBehovId() == behovId }
            .toList()

        //Verify the number of produced messages
        assertEquals(innkommendeBehov.size + behovsToProcess, behovsListe.size)

        //Check if JournalføringGsak sets gsakSakId, by verifing the number of behovs without gsaksakid
        val withoutGsaksakId = behovsListe.filter { kanskjeBehandletBehov ->
            kanskjeBehandletBehov.value().getGsaksakId() == null
        }.size
        assertEquals(behovsToProcess + behovsMissingData, withoutGsaksakId)
    }

    @Test
    fun ` Finds and creates saker `() {

        //Test data: Vedtakstype
        val innkommendeBehov = listOf(
            Vedtakstype.NY_RETTIGHET,
            Vedtakstype.NY_RETTIGHET,
            Vedtakstype.GJENOPPTAK,
            Vedtakstype.GJENOPPTAK,
            Vedtakstype.NY_RETTIGHET,
            Vedtakstype.GJENOPPTAK
        )

        val behovsToProcess = innkommendeBehov.size

        val behovId = "2"
        innkommendeBehov.forEach { testdata ->
            val innkommendeBehov: Behov = Behov
                .newBuilder()
                .setBehovId(behovId)
                .setMottaker(Mottaker("12345678912"))
                .setBehandleneEnhet("0000")
                .setHenvendelsesType(
                    Søknad.newBuilder().setVedtakstype(testdata).build()
                )
                .setFagsakId("fagsak")
                .setJournalpost(
                    Journalpost
                        .newBuilder()
                        .setJournalpostId("12345")
                        .build()
                )
                .build()
            val record = behovProducer.send(ProducerRecord(INNGÅENDE_JOURNALPOST.name, innkommendeBehov)).get()
            LOGGER.info { "Produced -> ${record.topic()}  to offset ${record.offset()}" }
        }

        val behovConsumer: KafkaConsumer<String, Behov> = behovConsumer(env, "test-consumer-2")
        val behovsListe = behovConsumer.poll(Duration.ofSeconds(5))
            .filter { record -> record.value().getBehovId() == behovId }
            .toList()

        //Verify the number of produced messages
        assertEquals(innkommendeBehov.size + behovsToProcess, behovsListe.size)
    }

    class DummyGsakClient : GsakClient {
        override fun findSak(aktoerId: String, correlationId: String): List<GsakSak> {
            return listOf(GsakSak(tema = "DAG"))
        }

        override fun createSak(aktoerId: String, fagsakId: String, correlationId: String): GsakSak {
            return GsakSak(tema = "DAG")
        }
    }

    private fun behovConsumer(env: Environment, groupId: String): KafkaConsumer<String, Behov> {
        val consumer: KafkaConsumer<String, Behov> = KafkaConsumer(Properties().apply {
            put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, env.schemaRegistryUrl)
            put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
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