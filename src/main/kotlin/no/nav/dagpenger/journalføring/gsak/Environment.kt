package no.nav.dagpenger.journalføring.gsak

data class Environment(
    val gsakSakUrl: String = getEnvVar("DAGPENGER_OPPSLAG_API_URL"),
    val username: String = getEnvVar("SRVDAGPENGER_JOURNALFORING_GSAK_USERNAME"),
    val password: String = getEnvVar("SRVDAGPENGER_JOURNALFORING_GSAK_PASSWORD"),
    val oicdStsUrl: String = getEnvVar("OIDC_STS_ISSUERURL"),
    val bootstrapServersUrl: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
    val schemaRegistryUrl: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_URL", "localhost:8081"),
    val fasitEnvironmentName: String = getEnvVar("FASIT_ENVIRONMENT_NAME", "").filterNot { it in "p" }, //filter out productiony
    val httpPort: Int? = null
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
