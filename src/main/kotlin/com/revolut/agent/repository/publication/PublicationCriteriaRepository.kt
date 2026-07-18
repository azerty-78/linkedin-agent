package com.revolut.agent.repository.publication

import com.revolut.agent.model.publication.Publication
import com.revolut.agent.model.publication.PublicationStatus
import com.revolut.agent.model.publication.PublicationTriggerType
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@Repository
class PublicationCriteriaRepository(
	private val mongoTemplate: ReactiveMongoTemplate,
) {

	fun findFiltered(
		status: PublicationStatus?,
		triggerType: PublicationTriggerType?,
		from: Instant?,
		to: Instant?,
		skip: Long,
		limit: Long,
	): Flux<Publication> {
		val query = Query(buildCriteria(status, triggerType, from, to))
			.with(Sort.by(Sort.Direction.DESC, "triggeredAt"))
			.skip(skip)
			.limit(limit.toInt())
		return mongoTemplate.find(query, Publication::class.java)
	}

	fun countFiltered(
		status: PublicationStatus?,
		triggerType: PublicationTriggerType?,
		from: Instant?,
		to: Instant?,
	): Mono<Long> =
		mongoTemplate.count(Query(buildCriteria(status, triggerType, from, to)), Publication::class.java)

	fun findAllFiltered(
		status: PublicationStatus?,
		triggerType: PublicationTriggerType?,
		from: Instant?,
		to: Instant?,
	): Flux<Publication> {
		val query = Query(buildCriteria(status, triggerType, from, to))
			.with(Sort.by(Sort.Direction.DESC, "triggeredAt"))
		return mongoTemplate.find(query, Publication::class.java)
	}

	private fun buildCriteria(
		status: PublicationStatus?,
		triggerType: PublicationTriggerType?,
		from: Instant?,
		to: Instant?,
	): Criteria {
		val parts = mutableListOf<Criteria>()
		status?.let { parts += Criteria.where("status").`is`(it) }
		triggerType?.let { parts += Criteria.where("triggerType").`is`(it) }
		when {
			from != null && to != null ->
				parts += Criteria.where("triggeredAt").gte(from).lt(to)
			from != null ->
				parts += Criteria.where("triggeredAt").gte(from)
			to != null ->
				parts += Criteria.where("triggeredAt").lt(to)
		}
		return if (parts.isEmpty()) {
			Criteria()
		} else {
			Criteria().andOperator(*parts.toTypedArray())
		}
	}
}
