package com.umt.core.media

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReleaseStatusHistoryRepository : JpaRepository<ReleaseStatusHistory, UUID>
