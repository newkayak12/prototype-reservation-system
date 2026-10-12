package com.reservation.persistence.converter

import com.reservation.utilities.generator.uuid.UuidBinaryCodec
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.UserType
import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

/**
 * `@Id`/`@EmbeddedId` 구성요소에 쓰는 BINARY(16) 변환자
 * (ADR-028 결정 3, 폴백 B의 두 번째 경로).
 *
 * [UuidBinaryConverter](JPA `AttributeConverter`)는 일반 `@Column`에서는
 * 동작하지만, `@Id` 속성에는 **적용되지 않는다** — JPA 11.1.10이 권고하지
 * 않는 경로였고, 이 프로젝트에서 `BinaryPkHibernateVerificationTest`로
 * 직접 확인했다: `@MappedSuperclass`의 `@Id`에 건 `@Convert`(필드 레벨,
 * 클래스 레벨 `attributeName` 재선언 둘 다)는 Hibernate 6.6.13이 무시하고
 * 평문 36자 문자열을 그대로 insert해 `Data truncation`으로 실패한다.
 *
 * `org.hibernate.usertype.UserType`은 JPA `@Convert`를 거치지 않는
 * Hibernate 네이티브 타입 바인딩이라 이 제약 자체의 적용 대상이 아니다 —
 * `@Id`/`@EmbeddedId`에서도 동작한다(같은 테스트로 확인). 바이트 변환
 * 로직은 여기서도 직접 갖지 않고 [UuidBinaryCodec]에 위임한다 —
 * [UuidBinaryConverter]와 동일한 위임 구조를 유지한다.
 */
class UuidBinaryIdentifierType : UserType<String> {
    override fun getSqlType(): Int = Types.BINARY

    override fun returnedClass(): Class<String> = String::class.java

    override fun equals(
        x: String?,
        y: String?,
    ): Boolean = x == y

    override fun hashCode(x: String): Int = x.hashCode()

    override fun nullSafeGet(
        rs: ResultSet,
        position: Int,
        session: SharedSessionContractImplementor,
        owner: Any?,
    ): String? = rs.getBytes(position)?.let { UuidBinaryCodec.decode(it) }

    override fun nullSafeSet(
        st: PreparedStatement,
        value: String?,
        index: Int,
        session: SharedSessionContractImplementor,
    ) {
        if (value == null) {
            st.setNull(index, Types.BINARY)
        } else {
            st.setBytes(index, UuidBinaryCodec.encode(value))
        }
    }

    override fun deepCopy(value: String?): String? = value

    override fun isMutable(): Boolean = false

    override fun disassemble(value: String?): Serializable? = value as Serializable?

    override fun assemble(
        cached: Serializable,
        owner: Any?,
    ): String = cached as String
}
