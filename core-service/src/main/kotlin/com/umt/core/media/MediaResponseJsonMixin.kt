package com.umt.core.media

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.umt.api.generated.model.MediaResponse
import org.springframework.boot.jackson.JsonMixin

// The generated base writes mediaCategory twice - as the type id and as the field; reuse the field.
@JsonMixin(MediaResponse::class)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "mediaCategory",
    visible = true,
)
abstract class MediaResponseJsonMixin
