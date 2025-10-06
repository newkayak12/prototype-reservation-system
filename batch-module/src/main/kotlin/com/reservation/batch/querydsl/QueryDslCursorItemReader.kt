package com.reservation.batch.querydsl

import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.springframework.batch.item.ExecutionContext
import org.springframework.batch.item.ItemStreamReader

class QueryDslCursorItemReader<T>(
    private val entityManagerFactory: EntityManagerFactory,
    private val queryFunction: (JPAQueryFactory, String?) -> JPAQuery<T>,
    private val idExtractor: (T) -> String,
    private val chunkSize: Int = 1000,
) : ItemStreamReader<T> {
    private lateinit var entityManager: EntityManager
    private var lastId: String? = null
    private var buffer: MutableList<T> = mutableListOf()
    private var currentIndex = 0

    override fun read(): T? {
        if (currentIndex >= buffer.size) {
            fetchNextChunk()
            currentIndex = 0
            if (buffer.isEmpty()) return null
        }
        return buffer[currentIndex++]
    }

    private fun fetchNextChunk() {
        val queryFactory = JPAQueryFactory(entityManager)
        buffer =
            queryFunction(queryFactory, lastId)
                .limit(chunkSize.toLong())
                .fetch()
                .toMutableList()

        if (buffer.isNotEmpty()) {
            lastId = idExtractor(buffer.last())
        }
    }

    override fun open(executionContext: ExecutionContext) {
        entityManager = entityManagerFactory.createEntityManager()
        lastId = executionContext.getString("lastId")
    }

    override fun update(executionContext: ExecutionContext) {
        lastId?.let { executionContext.putString("lastId", it) }
    }

    override fun close() {
        entityManager.close()
    }
}
