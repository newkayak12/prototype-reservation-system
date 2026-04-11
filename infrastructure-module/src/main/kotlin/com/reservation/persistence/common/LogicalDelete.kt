package com.reservation.persistence.common

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import org.hibernate.annotations.Comment

@Embeddable
class LogicalDelete(
    isDeleted: Boolean = false,
) {
    @Column(name = "is_deleted", columnDefinition = "TINYINT(1) DEFAULT 0")
    @Comment("논리 삭제 여부")
    val isDeleted = isDeleted
}
