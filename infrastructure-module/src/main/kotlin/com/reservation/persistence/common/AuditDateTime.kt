package com.reservation.persistence.common

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import org.hibernate.annotations.Comment
import java.time.LocalDateTime

@Embeddable
class AuditDateTime {
    @Column(name = "created_datetime", columnDefinition = "DATETIME")
    @Comment("생성 날짜-시간")
    lateinit var createdDatetime: LocalDateTime
        protected set

    @Column(name = "updated_datetime", columnDefinition = "DATETIME")
    @Comment("수정 날짜-시간")
    lateinit var updatedDatetime: LocalDateTime
        protected set

    @PrePersist
    fun beforeCreate() {
        createdDatetime = LocalDateTime.now()
        updatedDatetime = LocalDateTime.now()
    }

    @PreUpdate
    fun beforeUpdate() {
        updatedDatetime = LocalDateTime.now()
    }
}
