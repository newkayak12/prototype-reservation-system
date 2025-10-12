package com.reservation.batch.querydsl

import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.batch.item.ExecutionContext
import org.springframework.batch.item.ItemStreamReader

open class QueryDslCursorItemReader<T>(
    private val entityManager: EntityManager,
    private val queryFunction: (JPAQueryFactory, String?) -> JPAQuery<T>,
    private val idExtractor: (T) -> String,
    private val chunkSize: Int = 1000,
) : ItemStreamReader<T> {
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
        lastId = executionContext.getString("lastId", null)
    }

    override fun update(executionContext: ExecutionContext) {
        lastId?.let { executionContext.putString("lastId", it) }
    }

    override fun close() {
        entityManager.close()
    }
}
