package dev.matvs.freepeepee.web

import dev.matvs.freepeepee.repository.AuditRepository
import dev.matvs.freepeepee.web.dto.AuditDto
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/audit")
class AuditController(private val repo: AuditRepository) {

    /** Newest-first audit entries. Authorization enforced by SecurityConfig (ROLE_ADMIN). */
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ): Page<AuditDto> =
        repo.findAllByOrderByOccurredAtDesc(PageRequest.of(page, size.coerceIn(1, 200)))
            .map(AuditDto::from)
}
