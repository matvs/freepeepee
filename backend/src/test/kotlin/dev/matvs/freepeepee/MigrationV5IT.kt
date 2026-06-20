package dev.matvs.freepeepee

import dev.matvs.freepeepee.repository.ToiletRepository
import dev.matvs.freepeepee.repository.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@ContextConfiguration(initializers = [PostgisInitializer::class])
class MigrationV5IT(
    @Autowired private val users: UserRepository,
    @Autowired private val toilets: ToiletRepository,
    @Autowired private val dataSource: DataSource
) : DescribeSpec({
    describe("V5 migration") {
        it("removes the seeded matvs user") {
            users.findByUsername("matvs").shouldBeNull()
        }
        it("preserves the seeded toilet rows") {
            toilets.findAll().size shouldBeGreaterThanOrEqualTo 7
        }
        it("allows deleting a user that has audit rows (append-only audit_entry has no blocking FK)") {
            val jdbc = JdbcTemplate(dataSource)
            val uid = UUID.randomUUID()
            jdbc.update("INSERT INTO app_user(id, username, password_hash, role) VALUES (?, 'tmpuser_v5', 'x', 'ADMIN')", uid)
            jdbc.update(
                "INSERT INTO audit_entry(actor_id, actor_name, entity_type, operation) VALUES (?, 'tmpuser_v5', 'AppUser', 'LOGIN')",
                uid
            )
            // With the old ON DELETE SET NULL FK this cascaded an UPDATE onto the append-only
            // audit_entry and threw "audit_entry is append-only"; it must now succeed.
            jdbc.update("DELETE FROM app_user WHERE id = ?", uid)
            // Key on the unique per-run actor_id (audit_entry is append-only, so rows from
            // earlier reused-container runs would otherwise inflate a name-based count).
            val remaining = jdbc.queryForObject(
                "SELECT count(*) FROM audit_entry WHERE actor_id = ?", Int::class.java, uid
            )
            remaining shouldBe 1
        }
    }
})
