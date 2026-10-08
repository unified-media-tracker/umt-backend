package com.umt.core.common.exception

import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.util.UUID

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    private fun request(uri: String = "/api/core/media") = mockk<HttpServletRequest> {
        every { requestURI } returns uri
    }

    @Test
    fun `a missing required query param maps to 400, not the 500 catch-all`() {
        val ex = MissingServletRequestParameterException("mediaCategory", "MediaCategory")

        val result = handler.handleMissingParameter(ex, request())

        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertEquals(400, result.body?.status)
        assertEquals("/api/core/media", result.body?.path)
    }

    @Test
    fun `a malformed path value (bad UUID) maps to 400, not the 500 catch-all`() {
        val parameter = mockk<MethodParameter> { every { parameterName } returns "id" }
        val ex = MethodArgumentTypeMismatchException("not-a-uuid", UUID::class.java, "id", parameter, IllegalArgumentException("Invalid UUID string"))

        val result = handler.handleTypeMismatch(ex, request("/api/core/media/not-a-uuid"))

        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertEquals(400, result.body?.status)
    }

    @Test
    fun `a not-found lookup maps to 404`() {
        val result = handler.handleNotFoundException(NoSuchElementException("Media item x not found"), request())

        assertEquals(HttpStatus.NOT_FOUND, result.statusCode)
        assertEquals("Media item x not found", result.body?.message)
    }

    @Test
    fun `a bad argument maps to 400`() {
        val result = handler.handleBadRequestException(IllegalArgumentException("bad value"), request())

        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertEquals("bad value", result.body?.message)
    }

    @Test
    fun `anything else falls back to a generic 500`() {
        val result = handler.handleAllExceptions(RuntimeException("boom"), request())

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.statusCode)
        assertEquals("An unexpected error occurred", result.body?.message)
    }
}
