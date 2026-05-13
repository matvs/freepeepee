package dev.matvs.freepeepee

import dev.matvs.freepeepee.domain.AuditEntry
import dev.matvs.freepeepee.repository.AuditRepository
import dev.matvs.freepeepee.security.AuditContext
import dev.matvs.freepeepee.service.AuditService
import dev.matvs.freepeepee.service.ToiletSnapshot
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.UUID

class AuditServiceTest : DescribeSpec({

    describe("AuditService.recordUpdate") {

        it("produces one row per changed field, none for unchanged fields") {
            val repo = mockk<AuditRepository>(relaxed = true)
            val ctx = mockk<AuditContext>()
            every { ctx.actorId() } returns UUID.randomUUID()
            every { ctx.actorName() } returns "matvs"
            every { ctx.ip() } returns "127.0.0.1"
            every { ctx.userAgent() } returns "kotest"
            every { ctx.requestId() } returns UUID.randomUUID()

            val svc = AuditService(repo, ctx)

            val before = ToiletSnapshot("Old", "Addr", 47.0, 8.0, null, true, "OTHER", null)
            val after  = ToiletSnapshot("New", "Addr", 47.0, 8.0, "1234", true, "OTHER", null)
            // changed : name, pinCode -> 2 rows

            val captured = mutableListOf<AuditEntry>()
            val s = slot<AuditEntry>()
            every { repo.save(capture(s)) } answers { captured.add(s.captured); s.captured }

            svc.recordUpdate("Toilet", UUID.randomUUID(), before, after)

            verify(exactly = 2) { repo.save(any()) }
            captured.map { it.fieldName }.toSet() shouldBe setOf("name", "pinCode")
        }
    }
})
