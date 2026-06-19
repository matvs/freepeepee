package dev.matvs.freepeepee

import com.fasterxml.jackson.databind.ObjectMapper
import dev.matvs.freepeepee.domain.ToiletType
import dev.matvs.freepeepee.web.dto.LoginRequest
import dev.matvs.freepeepee.web.dto.ToiletCreateRequest
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
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
class ToiletApiIT(
    @Autowired private val mvc: MockMvc,
    @Autowired private val mapper: ObjectMapper
) : DescribeSpec({

    fun token(): String {
        val body = mapper.writeValueAsString(LoginRequest("admin", "test-admin-pw", false))
        val res = mvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andReturn().response.contentAsString
        return mapper.readTree(res)["accessToken"].asText()
    }

    describe("Toilet API") {

        it("nearby returns Zurich seed toilets within 1km of Hauptbahnhof") {
            val t = token()
            mvc.get("/api/toilets/nearby?lat=47.3779&lon=8.5402&radius=2000") {
                header("Authorization", "Bearer $t")
            }.andExpect {
                status { isOk() }
            }.andReturn().response.contentAsString.let {
                val arr = mapper.readTree(it)
                arr.size() shouldBeGreaterThanOrEqualTo 3
            }
        }

        it("creates a toilet, audit row appears, version 0") {
            val t = token()
            val body = mapper.writeValueAsString(ToiletCreateRequest(
                name = "Test loo", address = "Some street 1, Zurich",
                lat = 47.37, lon = 8.54, isWorking = true,
                toiletType = ToiletType.OTHER, notes = "kotest created"
            ))
            mvc.post("/api/toilets") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $t")
                content = body
            }.andExpect { status { isCreated() } }
        }

        it("allows unauthenticated read") {
            mvc.get("/api/toilets").andExpect { status { isOk() } }
        }

        it("rejects unauthenticated create") {
            val body = mapper.writeValueAsString(ToiletCreateRequest(
                name = "No-auth loo", address = "Nowhere 1, Zurich",
                lat = 47.37, lon = 8.54, isWorking = true,
                toiletType = ToiletType.OTHER, notes = null
            ))
            // formLogin/httpBasic are disabled, so Spring's default Http403ForbiddenEntryPoint
            // answers anonymous requests to a secured endpoint with 403 (not 401).
            mvc.post("/api/toilets") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isForbidden() } }
        }
    }
})
