package com.revolut.agent.repository.linkedin

import com.revolut.agent.model.linkedin.LinkedInConnection
import org.springframework.data.mongodb.repository.ReactiveMongoRepository

interface LinkedInConnectionRepository : ReactiveMongoRepository<LinkedInConnection, String>
