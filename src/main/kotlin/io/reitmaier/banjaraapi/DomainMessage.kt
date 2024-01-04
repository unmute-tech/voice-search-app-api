package io.reitmaier.banjaraapi

import io.ktor.http.*
import io.reitmaier.banjaraapi.repo.Include
import io.reitmaier.banjaraapi.repo.PhotoId
import io.reitmaier.banjaraapi.repo.QueryId
import java.util.Collections

/**
 * All possible things that can happen in the use-cases
 */
sealed class DomainMessage(val message: String, val statusCode: HttpStatusCode)

data object PhotoIdInvalid : DomainMessage("Photo Id is Invalid", HttpStatusCode.BadRequest)
data object RequestFileMissing : DomainMessage("The request is missing a file item", HttpStatusCode.BadRequest)
data object FileNameMissing : DomainMessage("The request is missing a file name", HttpStatusCode.BadRequest)
data object PhotoHashMissing : DomainMessage("The request is missing a photo hash", HttpStatusCode.BadRequest)
data object AudioLengthMissing : DomainMessage("The request is missing the length of the audio", HttpStatusCode.BadRequest)
data object PhotoHashInvalid : DomainMessage("The request has an invalid photo hash", HttpStatusCode.BadRequest)
data object IOError : DomainMessage("IO Error", HttpStatusCode.InternalServerError)

/* internal errors */

data object TranscriptionError : DomainMessage("Unable to transcribe Audio", HttpStatusCode.InternalServerError)
data object TranslationError : DomainMessage("Unable to translate text", HttpStatusCode.InternalServerError)
data object DatabaseError : DomainMessage("An Internal Error Occurred", HttpStatusCode.InternalServerError)

data class PhotoAlreadyExists(val hash: String): DomainMessage("Photo with hash ($hash) already exists", HttpStatusCode.Conflict)
data class AudioAlreadyExists(val hash: String): DomainMessage("Audio with hash ($hash) already exists", HttpStatusCode.Conflict)

data class QueryAlreadyExists(val id: QueryId): DomainMessage("Query with id (${id.id}) already exists", HttpStatusCode.Conflict)
data object QueryIdInvalid: DomainMessage("Query id is invalid", HttpStatusCode.Conflict)
data object TranslationAudioIdInvalid: DomainMessage("Translation Audio id is invalid", HttpStatusCode.Conflict)
data object LanguageInvalid: DomainMessage("Language parameter missing/invalid", HttpStatusCode.Conflict)
data object SampleIdInvalid: DomainMessage("Sample id is invalid", HttpStatusCode.Conflict)
data object IncludeParameterInvalid: DomainMessage("The include parameter must be ${Include.values()}", HttpStatusCode.Conflict)

data object SpeechResultsInvalid: DomainMessage("Speech Result list is invalid", HttpStatusCode.BadRequest)
data object SpeechResultInvalid: DomainMessage("Speech Result is invalid", HttpStatusCode.BadRequest)

data class QueryResultAlreadyExists(val queryId: QueryId, val photoId: PhotoId): DomainMessage("Query Result with $queryId and $photoId already exists", HttpStatusCode.Conflict)
