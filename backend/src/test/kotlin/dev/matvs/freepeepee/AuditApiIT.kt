package dev.matvs.freepeepee

import com.fasterxml.jackson.databind.ObjectMapper
import dev.matvs.freepeepee.domain.ToiletType
import dev.matvs.freepeepee.web.dto.LoginRequest
import dev.matvs.freepeepee.web.dto.ToiletCreateRequest
import io.kotest.core.spec.style.DescribeSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = [PostgisInitializer::class])
class AuditApiIT(
    @Autowired private val mvc: MockMvc,
    @Autowired private val mapper: ObjectMapper
) : DescribeSpec({

    fun token(): String {
        val res = mvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(LoginRequest("admin", "test-admin-pw", false))
        }.andReturn().response.contentAsString
        return mapper.readTree(res)["accessToken"].asText()
    }

    describe("GET /api/audit") {
        it("rejects anonymous access") {
            // formLogin/httpBasic disabled -> Spring's Http403ForbiddenEntryPoint answers 403.
            mvc.get("/api/audit").andExpect { status { isForbidden() } }
        }

        it("returns audit rows for an admin after a write") {
            val t = token()
            mvc.post("/api/toilets") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $t")
                content = mapper.writeValueAsString(ToiletCreateRequest(
                    name = "Audited loo", address = "Audit St 1, Zurich",
                    lat = 47.37, lon = 8.54, isWorking = true,
                    toiletType = ToiletType.OTHER, notes = null
                ))
            }.andExpect { status { isCreated() } }

            mvc.get("/api/audit?page=0&size=10") {
                header("Authorization", "Bearer $t")
            }.andExpect {
                status { isOk() }
                jsonPath("$.content") { isArray() }
                jsonPath("$.content[0].operation") { exists() }
            }
        }
    }
})
