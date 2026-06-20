package dev.matvs.freepeepee

import dev.matvs.freepeepee.repository.ToiletRepository
import dev.matvs.freepeepee.repository.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.nulls.shouldBeNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration

@SpringBootTest
@ContextConfiguration(initializers = [PostgisInitializer::class])
class MigrationV5IT(
    @Autowired private val users: UserRepository,
    @Autowired private val toilets: ToiletRepository
) : DescribeSpec({
    describe("V5 migration") {
        it("removes the seeded matvs user") {
            users.findByUsername("matvs").shouldBeNull()
        }
        it("preserves the seeded toilet rows") {
            toilets.findAll().size shouldBeGreaterThanOrEqualTo 7
        }
    }
})
