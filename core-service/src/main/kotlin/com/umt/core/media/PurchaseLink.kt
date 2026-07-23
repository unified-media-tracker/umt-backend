package com.umt.core.media

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class AvailabilityStatus { AVAILABLE, PREORDER, UNAVAILABLE }

@Entity
@Table(name = PurchaseLink.TABLE_NAME)
class PurchaseLink(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @field:Column(name = ID_COLUMN)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = MEDIA_ITEM_ID_COLUMN, nullable = false)
    var mediaItem: MediaItem,

    @field:Column(name = PLATFORM_NAME_COLUMN, nullable = false, length = 50)
    var platformName: String,

    @field:Column(name = AFFILIATE_URL_COLUMN, nullable = false)
    var affiliateUrl: String,

    @field:Column(name = PRICE_COLUMN)
    var price: BigDecimal? = null,

    @JdbcTypeCode(SqlTypes.CHAR)
    @field:Column(name = CURRENCY_COLUMN, length = 3)
    var currency: String? = null,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @field:Column(name = AVAILABILITY_STATUS_COLUMN, nullable = false)
    var availabilityStatus: AvailabilityStatus,

    @field:Column(name = LAST_CHECKED_AT_COLUMN, nullable = false)
    var lastCheckedAt: Instant,
) {
    companion object {
        const val TABLE_NAME = "purchase_link"
        const val ID_COLUMN = "id"
        const val MEDIA_ITEM_ID_COLUMN = "media_item_id"
        const val PLATFORM_NAME_COLUMN = "platform_name"
        const val AFFILIATE_URL_COLUMN = "affiliate_url"
        const val PRICE_COLUMN = "price"
        const val CURRENCY_COLUMN = "currency"
        const val AVAILABILITY_STATUS_COLUMN = "availability_status"
        const val LAST_CHECKED_AT_COLUMN = "last_checked_at"
    }
}