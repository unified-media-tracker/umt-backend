package com.umt.shared.config

import org.mapstruct.MapperConfig
import org.mapstruct.ReportingPolicy

@MapperConfig(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface MapperConfig