package dev.matvs.freepeepee

import dev.matvs.freepeepee.domain.Role
import dev.matvs.freepeepee.repository.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration

@SpringBootTest
@ContextConfiguration(initializers = [PostgisInitializer::class])
class AdminBootstrapIT(
    @Autowired private val users: UserRepository
) : DescribeSpec({
    describe("AdminUserInitializer") {
        it("provisions the env-configured admin as ADMIN and enabled") {
            val admin = users.findByUsername("admin")
            admin.shouldNotBeNull()
            admin.role shouldBe Role.ADMIN
            admin.isEnabled shouldBe true
        }
    }
})
