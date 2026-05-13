package dev.matvs.freepeepee

import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

object PostgisContainer : PostgreSQLContainer<PostgisContainer>(
    DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
) {
    init { withReuse(true) }
}

class PostgisInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(ctx: ConfigurableApplicationContext) {
        if (!PostgisContainer.isRunning) PostgisContainer.start()
        TestPropertyValues.of(
            "spring.datasource.url=${PostgisContainer.jdbcUrl}",
            "spring.datasource.username=${PostgisContainer.username}",
            "spring.datasource.password=${PostgisContainer.password}",
            "freepeepee.jwt.secret=test_secret_at_least_32_bytes_xxxxxxxxxxxxxxxx",
            "freepeepee.cors.allowed-origins=http://localhost:4200"
        ).applyTo(ctx.environment)
    }
}
