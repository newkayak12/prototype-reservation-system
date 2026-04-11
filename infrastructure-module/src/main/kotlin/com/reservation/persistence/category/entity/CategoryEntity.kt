package com.reservation.persistence.category.entity

import com.reservation.enumeration.CategoryType
import com.reservation.persistence.common.AuditDateTime
import com.reservation.persistence.common.LogicalDelete
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.Comment

@Table(
    catalog = "prototype_reservation",
    name = "category",
    indexes = [
        Index(name = "index_category_type", columnList = "category_type, id"),
    ],
)
@Entity
class CategoryEntity(
    title: String,
    categoryType: CategoryType,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @Comment("카테고리 식별 키")
    val id: Long = 0

    @Column(name = "title", nullable = false)
    @Comment("카테고리명")
    var title: String = title
        protected set

    @Column(name = "category_type", nullable = false)
    @Enumerated(EnumType.STRING)
    val categoryType: CategoryType = categoryType

    @Embedded
    var logicalDelete: LogicalDelete = LogicalDelete()
        protected set

    @Embedded
    var auditDateTime: AuditDateTime = AuditDateTime()
        protected set

    fun delete() {
        logicalDelete = LogicalDelete(true)
    }

    fun changeTitle(title: String) {
        this.title = title
    }
}
