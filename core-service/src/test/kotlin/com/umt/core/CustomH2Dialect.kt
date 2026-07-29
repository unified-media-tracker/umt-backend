package com.umt.core

import org.hibernate.boot.model.TypeContributions
import org.hibernate.dialect.H2Dialect
import org.hibernate.service.ServiceRegistry
import org.hibernate.type.SqlTypes
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry
import org.hibernate.type.descriptor.jdbc.JdbcType

class CustomH2Dialect : H2Dialect() {
    override fun contributeTypes(typeContributions: TypeContributions, serviceRegistry: ServiceRegistry) {
        super.contributeTypes(typeContributions, serviceRegistry)
        typeContributions.contributeJdbcType(VarcharJdbcType.INSTANCE)
    }

    override fun resolveSqlTypeDescriptor(
        columnTypeName: String?,
        jdbcTypeCode: Int,
        precision: Int,
        scale: Int,
        jdbcTypeRegistry: JdbcTypeRegistry
    ): JdbcType {
        if (jdbcTypeCode == SqlTypes.NAMED_ENUM) {
            return VarcharJdbcType.INSTANCE
        }
        return super.resolveSqlTypeDescriptor(columnTypeName, jdbcTypeCode, precision, scale, jdbcTypeRegistry)
    }
}
