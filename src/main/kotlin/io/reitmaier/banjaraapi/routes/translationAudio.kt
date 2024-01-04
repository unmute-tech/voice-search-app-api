package io.reitmaier.banjaraapi.routes

import com.github.michaelbull.logging.InlineLogger
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.binding.binding
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.reitmaier.banjaraapi.*
import io.reitmaier.banjaraapi.repo.*
import io.reitmaier.banjaraapi.views.*
import kotlinx.coroutines.launch
import java.util.*

fun Application.translationAudioRoutes(
  banjaraRepo: BanjaraRepo,
  log: InlineLogger = InlineLogger(),
) = routing {
  route("/translationAudio/") {
   route("{$TRANSLATION_AUDIO_ID_PARAMETER}/")  {
     post("translate/") {
       call.parameters.readTranslationAudioId()
         .andThen {
           banjaraRepo.translate(it)
         }
       .fold(
         failure = { call.respondDomainMessage(it) },
         success = {
           call.respondRedirect("/query/${it.id}", false)
           call.respond(HttpStatusCode.OK, "") }
       )
     }
   }
  }
}
const val TRANSLATION_AUDIO_ID_PARAMETER = "translationAudioId"

private fun Parameters.readTranslationAudioId(): Result<TranslationAudioId, DomainMessage> {
  return get(TRANSLATION_AUDIO_ID_PARAMETER)
    .toResultOr { TranslationAudioIdInvalid }
    .andThen {
      log.debug { "Read $TRANSLATION_AUDIO_ID_PARAMETER = $it" }
      runCatching {
        val translationAudioId = TranslationAudioId(it.toLong())
        log.debug { "Parsed $TRANSLATION_AUDIO_ID_PARAMETER = $translationAudioId" }
        translationAudioId
      }.mapError { TranslationAudioIdInvalid }
    }
}
