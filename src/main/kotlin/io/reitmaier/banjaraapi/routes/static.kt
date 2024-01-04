package io.reitmaier.banjaraapi.routes

import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.http.content.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import java.io.File


fun Application.staticRoutes() {

  install(StatusPages) {
    exception<Throwable> { call, cause ->
      call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
    }
  }
  routing {
    // Static Stuff
    staticResources("/static", "static")
    staticFiles("/data", File("data"))
  }
}
