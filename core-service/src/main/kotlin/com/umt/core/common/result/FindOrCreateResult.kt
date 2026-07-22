package com.umt.core.common.result

import org.springframework.http.HttpStatus

data class FindOrCreateResult<T>(
    val entity: T,
    val created: Boolean
) {

    fun toHttpStatus(): HttpStatus =
        if (created) HttpStatus.CREATED else HttpStatus.OK
}