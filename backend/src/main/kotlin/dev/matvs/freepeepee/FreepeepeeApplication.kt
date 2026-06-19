package dev.matvs.freepeepee

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.time.OffsetDateTime
import java.util.Optional

@SpringBootApplication
@EnableJpaAuditing(dateTimeProviderRef = "auditDateTimeProvider")
class FreepeepeeApplication {
    @Bean
    fun auditDateTimeProvider(): DateTimeProvider = DateTimeProvider { Optional.of(OffsetDateTime.now()) }
}

fun main(args: Array<String>) {
    runApplication<FreepeepeeApplication>(*args)
}
