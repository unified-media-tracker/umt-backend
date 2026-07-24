package com.umt.core

import org.hibernate.boot.model.TypeContributions
import org.hibernate.dialect.H2Dialect
import org.hibernate.service.ServiceRegistry
import org.hibernate.type.SqlTypes
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType

class CustomH2Dialect : H2Dialect() {
    override fun contributeTypes(typeContributions: TypeContributions, serviceRegistry: ServiceRegistry) {
        super.contributeTypes(typeContributions, serviceRegistry)
        typeContributions.typeConfiguration.jdbcTypeRegistry.addDescriptor(
            SqlTypes.NAMED_ENUM, VarcharJdbcType.INSTANCE
        )
    }
}
