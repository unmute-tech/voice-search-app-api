package io.reitmaier.banjaraapi.routes

import com.github.michaelbull.logging.InlineLogger
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.reitmaier.banjaraapi.DomainMessage

val log = InlineLogger()
suspend fun ApplicationCall.respondDomainMessage(domainMessage: DomainMessage) {
    log.debug { "Responding with Error: $domainMessage" }
    respond(domainMessage.statusCode, domainMessage.message)
}
