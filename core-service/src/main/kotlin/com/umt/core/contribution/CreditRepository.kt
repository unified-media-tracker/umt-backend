package com.umt.core.contribution

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CreditRepository : JpaRepository<Credit, UUID>
