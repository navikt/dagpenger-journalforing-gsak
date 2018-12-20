package no.nav.dagpenger.journalføring.gsak

import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.avro.Søknad
import org.junit.Test
import kotlin.test.assertFalse

class JournalføringGsakTest {

    private fun basicBehovBuilder(): Behov.Builder {
        return Behov
            .newBuilder()
            .setBehovId("000")
            .setHenvendelsesType(Søknad())
    }

    @Test
    fun `Do not process behov needing manuell behandling`() {

        val behovError = basicBehovBuilder()
            .setFagsakId("123")
            .setTrengerManuellBehandling(true)
            .build()

        assertFalse(shouldBeProcessed(behovError))
    }

    @Test
    fun `Process behov with fagsak and without gsaksak  `() {

        val behovSuccess = basicBehovBuilder()
            .setFagsakId("123")
            .build()

        assert(shouldBeProcessed(behovSuccess))
    }

    @Test
    fun `Do not reprocess behov `() {
        val behovDuplicate = basicBehovBuilder()
            .setFagsakId("123")
            .setGsaksakId("456")
            .build()

        val behovDuplicateWithoutFagsak = basicBehovBuilder()
            .setGsaksakId("456")
            .build()

        assertFalse(shouldBeProcessed(behovDuplicate))
        assertFalse(shouldBeProcessed(behovDuplicateWithoutFagsak))
    }

    @Test
    fun `Do not process behov with missing fagsak `() {
        val behovMissingBoth = basicBehovBuilder().build()

        assertFalse(shouldBeProcessed(behovMissingBoth))
    }
}