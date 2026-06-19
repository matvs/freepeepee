package dev.matvs.freepeepee.web.error

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val errors = e.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "validation_failed", "fields" to errors))
    }

    @ExceptionHandler(Exception::class)
    fun handleAny(e: Exception): ResponseEntity<Map<String, String>> {
        log.error("Unhandled exception", e)
        return ResponseEntity.internalServerError().body(mapOf("error" to "internal_error"))
    }
}
