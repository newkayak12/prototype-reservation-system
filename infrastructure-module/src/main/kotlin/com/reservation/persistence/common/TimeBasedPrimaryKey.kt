package com.reservation.persistence.common

import com.reservation.persistence.converter.UuidBinaryIdentifierType
import com.reservation.utilities.generator.uuid.UuidGenerator
import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import org.hibernate.annotations.Comment
import org.hibernate.annotations.Type
import org.hibernate.proxy.HibernateProxy
import org.springframework.data.domain.Persistable
import java.io.Serializable
import java.util.Objects

@MappedSuperclass
abstract class TimeBasedPrimaryKey : Persistable<String> {
    // ADR-028 결정 3, 폴백 B: JPA `@Convert`는 Hibernate 6.6.13에서 `@Id`에
    // 적용되지 않는다(BinaryPkHibernateVerificationTest로 확인 — 평문 문자열이
    // 그대로 insert돼 `Data truncation`으로 실패). `@Convert`를 거치지 않는
    // Hibernate 네이티브 UserType([UuidBinaryIdentifierType])으로 전환했다.
    @Id
    @Type(UuidBinaryIdentifierType::class)
    @Column(name = "id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    @Comment("식별키")
    val identifier: String = UuidGenerator.generate()

    @Transient
    private var isNewEntity: Boolean = true

    override fun getId(): String? = this.identifier

    override fun isNew(): Boolean = this.isNewEntity

    fun isPersisted(): Boolean = !this.isNewEntity

    override fun hashCode(): Int = Objects.hashCode(identifier)

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }

        if (other !is HibernateProxy && this::class != other::class) {
            return false
        }

        return identifier == getIdentifier(other)
    }

    private fun getIdentifier(obj: Any): Serializable {
        return if (obj is HibernateProxy) {
            obj.hibernateLazyInitializer.identifier as Serializable
        } else {
            (obj as TimeBasedPrimaryKey).identifier
        }
    }

    @PostPersist
    @PostLoad
    protected fun load() {
        isNewEntity = false
    }
}
