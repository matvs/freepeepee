package dev.matvs.freepeepee

import com.fasterxml.jackson.databind.ObjectMapper
import dev.matvs.freepeepee.web.dto.LoginRequest
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = [PostgisInitializer::class])
class AuthFlowIT(
    @Autowired private val mvc: MockMvc,
    @Autowired private val mapper: ObjectMapper
) : DescribeSpec({

    describe("POST /api/auth/login") {

        it("returns 200 + token for matvs / lap00p00") {
            val body = mapper.writeValueAsString(LoginRequest("matvs", "lap00p00", rememberMe = true))
            mvc.post("/api/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isOk() }
            }.andReturn().response.contentAsString.let { json ->
                val tree = mapper.readTree(json)
                tree["accessToken"].asText().shouldNotBeEmpty()
                tree["refreshToken"].asText().shouldNotBeEmpty()
            }
        }

        it("returns 401 for wrong password") {
            val body = mapper.writeValueAsString(LoginRequest("matvs", "wrong", rememberMe = false))
            mvc.post("/api/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isUnauthorized() } }
        }

        it("returns 401 for unknown user") {
            val body = mapper.writeValueAsString(LoginRequest("ghost", "anything", rememberMe = false))
            mvc.post("/api/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isUnauthorized() } }
        }
    }
})
