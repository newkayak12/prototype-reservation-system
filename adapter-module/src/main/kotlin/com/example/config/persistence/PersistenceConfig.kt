package com.example.config.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@Configuration
@EnableJpaAuditing
internal class PersistenceConfig {

    @Bean
    fun queryFactory(entityManager: EntityManager): JPAQueryFactory = JPAQueryFactory(entityManager)
}