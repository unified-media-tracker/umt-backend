package com.umt.core.media.movie

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MovieDetailsRepository : JpaRepository<MovieDetails, UUID>