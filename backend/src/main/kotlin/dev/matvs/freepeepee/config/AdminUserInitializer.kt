package dev.matvs.freepeepee.config

import dev.matvs.freepeepee.domain.AppUser
import dev.matvs.freepeepee.domain.Role
import dev.matvs.freepeepee.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Provisions the single admin account from ADMIN_USERNAME / ADMIN_PASSWORD on every boot.
 * Upserts so password rotation = change .env + restart. Fails fast if creds are missing.
 */
@Component
class AdminUserInitializer(
    private val users: UserRepository,
    private val encoder: PasswordEncoder,
    @Value("\${freepeepee.admin.username:}") private val username: String,
    @Value("\${freepeepee.admin.password:}") private val password: String
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        require(username.isNotBlank() && password.isNotBlank()) {
            "ADMIN_USERNAME and ADMIN_PASSWORD must both be set; refusing to start without an admin account."
        }
        val hash = encoder.encode(password)
        val existing = users.findByUsername(username)
        if (existing == null) {
            users.save(AppUser(username = username, passwordHash = hash, role = Role.ADMIN, isEnabled = true))
            log.info("Created admin user '{}'", username)
        } else {
            existing.passwordHash = hash
            existing.role = Role.ADMIN
            existing.isEnabled = true
            users.save(existing)
            log.info("Updated admin user '{}'", username)
        }
    }
}
