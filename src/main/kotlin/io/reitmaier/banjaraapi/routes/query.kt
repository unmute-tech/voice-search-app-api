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
import java.io.File
import java.io.IOException
import java.util.*

fun Application.queryRoutes(
  queryDir: File,
  translationDir: File,
  commentsDir: File,
  queryPersistence: QueryPersistence,
  banjaraRepo: BanjaraRepo,
  speechService: SpeechService,
  log: InlineLogger = InlineLogger(),
) = routing {
  route("/query/") {
    get {
      queryPersistence.getQueries().andThen {
        it.map {
          banjaraRepo.getHydratedQueryById(it.id)
        }.combine()
      }
        .fold(
        success = {queries ->
          call.respondHtmlTemplate(Layout()) {
            content {
              insert(QueryListViewTemplate(queries)) {}
            }
          }
        },
        failure = {call.respondDomainMessage(it)}
      )
    }
    post {
      val multipartData = call.receiveMultipart().readAllParts()

      val fileItem =
        multipartData.filterIsInstance<PartData.FileItem>().firstOrNull() ?: return@post call.respondDomainMessage(
          RequestFileMissing
        )
      val displayName = fileItem.originalFileName ?: return@post call.respondDomainMessage(FileNameMissing)
        .also { fileItem.dispose() }

      log.debug { "Receiving: $displayName" }

      val extension = displayName.substringAfterLast(".", "wav")

      val formItems = multipartData.filterIsInstance<PartData.FormItem>()

      val queryId =
        QueryId.from(formItems.firstOrNull { it.name == "id" }?.value).get()
          ?: return@post call.respondDomainMessage(QueryIdInvalid).also {
            fileItem.dispose()
          }

      val fileName = "${UUID.randomUUID()}.tmp"

      val tmpFile = queryDir.resolve(fileName)

      log.debug { "Writing to file: ${tmpFile.absolutePath}" }

      runCatching {
        fileItem.streamProvider().use { its ->
          // copy the stream to the file with buffering
          tmpFile.outputStream().buffered().use {
            // note that this is blocking
            its.copyTo(it)
          }
        }
        fileItem.dispose()
      }
        .mapError { IOError }
        .andThen { _ ->
          val exists = queryPersistence.exists(queryId).get() != null
          if(exists) {
            Err(QueryAlreadyExists(queryId))
          } else {
            Ok(queryId)
          }
        }
        .andThen { _ ->
          runCatching {
            val audioFile = queryDir.resolve("${queryId.id}.$extension")
            val res = tmpFile.renameTo(audioFile)
            if(!res)
              throw IOException("Rename failed")
            audioFile
          }.mapError {
            IOError
          }
        }.andThen {
          queryPersistence.insert(queryId, it.path)
        }.fold(
          success = {call.respond(it.id.toString())},
          failure = {
            tmpFile.delete()
            call.respondDomainMessage(it)
          }
        )
    }
   route("{$QUERY_ID_PARAMETER}/")  {
     get {
       call.parameters.readQueryId()
         .andThen {
           banjaraRepo.getHydratedQueryById(it)
         }
         .orElse {
           runCatching {
             call.parameters[QUERY_ID_PARAMETER]!!.toInt()
           }.andThen { SampleId.from(it) }
             .mapError { QueryIdInvalid }
             .andThen { banjaraRepo.getHydratedQueryBySampleId(it) }
         }
         .fold(
           success = {queryWithResults ->
             call.respondHtmlTemplate(Layout()) {
               content {
                 insert(QueryResultTemplate(queryWithResults)) {}
               }
             }
           },
         failure = {call.respondDomainMessage(it)}
       )
     }
     post("rating/") {
       binding {
         val queryId = call.parameters.readQueryId().bind()
         val speechResult = runCatching { call.receive<SpeechResult>() }
           .mapError { SpeechResultInvalid }
           .bind()
         banjaraRepo.rateResults(queryId, speechResult)
       }.fold(
         failure = { call.respondDomainMessage(it) },
         success = { call.respond(HttpStatusCode.OK, "") }
       )
     }
     post("include/") {
       binding {
         val queryId = call.parameters.readQueryId().bind()
         log.debug { queryId }
         val receivedParams = call.receiveParameters()
         log.debug { receivedParams }

         val include = runCatching {
           val includeValue = receivedParams["include"] ?: ""
           log.debug { "Parsing $includeValue" }
           Include.byNameIgnoreCaseDefaultUnknown(includeValue)
         }.mapError { IncludeParameterInvalid }
           .bind()
         log.debug { include }
         queryPersistence.updateInclude(queryId, include)
         include
       }.fold(
         failure = { call.respondDomainMessage(it) },
         success = { call.respond(HttpStatusCode.OK, "$it") }
       )
     }

     post("translation/") {
       binding {
         val queryId = call.parameters.readQueryId().bind()
         log.debug { queryId }
         val receivedParams = call.receiveParameters()
         log.debug { receivedParams }
         val lang = Language.byValueIgnoreCase(receivedParams["lang"]).bind()
         val text = receivedParams["translation"] ?: ""
         val translation = when(lang) {
           Language.MARATHI ->
             queryPersistence.updateTranslation(queryId, TranslationMarathi(text))
           Language.ENGLISH ->
             queryPersistence.updateTranslation(queryId, TranslationEnglish(text))
           Language.HINDI ->
           queryPersistence.updateTranslation(queryId, TranslationHindi(text))
           Language.UNKNOWN -> return@binding Err(LanguageInvalid)
         }

         log.debug { translation }
         translation
       }.fold(
         failure = { call.respondDomainMessage(it) },
         success = { call.respond(HttpStatusCode.OK, "$it") }
       )
     }

     post("translationAudio") {
       val multipartData = call.receiveMultipart().readAllParts()

       val fileItem =
         multipartData.filterIsInstance<PartData.FileItem>().firstOrNull() ?: return@post call.respondDomainMessage(
           RequestFileMissing
         )
       val displayName = if(!fileItem.originalFileName.isNullOrBlank()) {
         fileItem.originalFileName!!
       } else {
         return@post call.respondDomainMessage(FileNameMissing).also { fileItem.dispose() }
       }

       log.debug { "Receiving: $displayName" }

       val extension = displayName.substringAfterLast(".", "wav")

       val formItems = multipartData.filterIsInstance<PartData.FormItem>()
       log.debug { formItems.joinToString { "${it.javaClass.name}: ${it.name} -> ${it.value}" }}

       val language =
         Language.byValueIgnoreCase(formItems.firstOrNull { it.name == "lang"}?.value).get()
           ?: return@post call.respondDomainMessage(LanguageInvalid).also {
             fileItem.dispose()
           }

       val queryId = call.parameters.readQueryId()
           .orElse {
           runCatching {
             call.parameters[QUERY_ID_PARAMETER]!!.toInt()
           }.andThen { SampleId.from(it) }
             .mapError { QueryIdInvalid }
             .andThen { banjaraRepo.getHydratedQueryBySampleId(it) }
             .map { it.query.id }
         }
         .get() ?: return@post call.respondDomainMessage(QueryIdInvalid)
         .also { fileItem.dispose() }

       val fileName = "${queryId.id}_${language.value}_${System.currentTimeMillis()}.$extension"

       val tmpFile = translationDir.resolve(fileName)

       log.debug { "Writing to file: ${tmpFile.absolutePath}" }


       runCatching {
         fileItem.streamProvider().use { its ->
           // copy the stream to the file with buffering
           tmpFile.outputStream().buffered().use {
             // note that this is blocking
             its.copyTo(it)
           }
         }
         fileItem.dispose()
         tmpFile
       }
         .mapError { IOError }
         .andThen { banjaraRepo.insertTranslationAudio(queryId,language,it.path)  }
         .map {
           launch { banjaraRepo.transcribeAudio(queryId,it,language,tmpFile) }
           it
         }
         .fold(
           success = {
             call.respondRedirect("/query/${queryId.id}", false)
           },
           failure = {
             tmpFile.delete()
             call.respondDomainMessage(it)
           }
         )
     }
     post("textComment/") {
       binding {
         val queryId = call.parameters.readQueryId().bind()
         log.debug { queryId }
         val receivedParams = call.receiveParameters()
         log.debug { receivedParams }
         val textComment = receivedParams["textComment"] ?: ""
         queryPersistence.updateTextComment(queryId, textComment)
         log.debug { textComment }
         textComment
       }.fold(
         failure = { call.respondDomainMessage(it) },
         success = { call.respond(HttpStatusCode.OK, "$it") }
       )
     }
     post("results/") {
       binding {
         val queryId = call.parameters.readQueryId().bind()
         val speechResults = runCatching {  call.receive<List<SpeechResult>>() }
           .mapError { SpeechResultsInvalid }
           .bind()
         banjaraRepo.addResults(queryId, speechResults)
       }.andThen {
         it.combine()
       }.fold(
         failure = { call.respondDomainMessage(it) },
         success = { call.respond(it.map { pair -> "${pair.first} -> ${pair.second}"  })}
       )
     }
     post("comment/") {
       val queryId = call.parameters.readQueryId().andThen {
         queryPersistence.exists(it)
       }.get() ?: return@post call.respondDomainMessage(QueryIdInvalid)

       val multipartData = call.receiveMultipart().readAllParts()

       val fileItem = multipartData.filterIsInstance<PartData.FileItem>().firstOrNull()
         ?: return@post call.respondDomainMessage(RequestFileMissing)

       val displayName = fileItem.originalFileName ?: return@post call.respondDomainMessage(FileNameMissing)
         .also { fileItem.dispose() }

       log.debug { "Receiving: $displayName" }

       val extension = displayName.substringAfterLast(".", "wav")

       val commentFile = commentsDir.resolve("${queryId.id}-${System.currentTimeMillis()}.$extension")

       log.debug { "Writing to file: ${commentFile.absolutePath}" }

       runCatching {
         fileItem.streamProvider().use { its ->
           // copy the stream to the file with buffering
           commentFile.outputStream().buffered().use {
             // note that this is blocking
             its.copyTo(it)
           }
         }
         fileItem.dispose()
       }.mapError { IOError }
         .andThen { _ ->
           queryPersistence.updateCommentPath(queryId,commentFile.path)
         }.fold(
           success = {call.respond(it.id.toString())},
           failure = {
             commentFile.delete()
             call.respondDomainMessage(it)
           }
         )
     }
   }



  }
}
const val QUERY_ID_PARAMETER = "queryId"

private fun Parameters.readQueryId(): Result<QueryId, DomainMessage> {
  return get(QUERY_ID_PARAMETER)
    .toResultOr { QueryIdInvalid }
    .andThen {
      log.debug { "Read $QUERY_ID_PARAMETER = $it" }
      runCatching {
        val queryId = QueryId(UUID.fromString(it))
        log.debug { "Parsed $QUERY_ID_PARAMETER = $it" }
        queryId
      }.mapError { QueryIdInvalid }
    }
}
