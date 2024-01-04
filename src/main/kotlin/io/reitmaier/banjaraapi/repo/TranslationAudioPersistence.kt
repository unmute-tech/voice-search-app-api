package io.reitmaier.banjaraapi.repo


import com.github.michaelbull.logging.InlineLogger
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import io.reitmaier.banjaraapi.AudioAlreadyExists
import io.reitmaier.banjaraapi.DatabaseError
import io.reitmaier.banjaraapi.DomainMessage
import io.reitmaier.banjaraapi.db.Audio
import io.reitmaier.banjaraapi.db.AudioQueries
import io.reitmaier.banjaraapi.db.TranslationAudio
import io.reitmaier.banjaraapi.db.TranslationAudioQueries
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState

enum class TranscriptionStatus {
  PENDING,
  FAILED,
  UNKNOWN,
  COMPLETED;

  companion object {
    fun byNameIgnoreCaseDefaultUnknown(input: String): TranscriptionStatus {
      return entries.firstOrNull { it.name.equals(input, true) } ?: UNKNOWN
    }
  }
}
@JvmInline value class TranslationAudioId(val serial: Long)
@JvmInline value class TranslationEnGoogle(val value: String)

interface TranslationAudioPersistence {
  /** Creates a new audio item in the database, and returns the [AudioID] of the newly created user */
  suspend fun insert(
    queryId: QueryId,
    language: Language,
    path: String
  ): Result<TranslationAudioId, DomainMessage>
  suspend fun getById(translationAudioId: TranslationAudioId) : Result<TranslationAudio, DomainMessage>
  suspend fun getByQueryId(queryId: QueryId) : Result<List<TranslationAudio>, DomainMessage>
  suspend fun updateTranscriptionStatus(
    translationAudioId: TranslationAudioId,
    status: TranscriptionStatus
  ): Result<Unit, DatabaseError>

  suspend fun updateTranscript(translationAudioId: TranslationAudioId, transcript: String): Result<Unit, DatabaseError>
  suspend fun updateTranslation(translationAudioId: TranslationAudioId, translation: TranslationEnGoogle): Result<TranslationEnGoogle?, DatabaseError>
}

/** AudioPersistence implementation based on SqlDelight */
fun translationAudioPersistence(
  translationAudioQueries: TranslationAudioQueries,
  log: InlineLogger,
) =
  object : TranslationAudioPersistence {

    override suspend fun insert(
      queryId: QueryId,
      language: Language,
      path: String
    ) = runCatching {
        translationAudioQueries.insertAndGetId(queryId, language, path).executeAsOne()
      }.mapError {
        log.error { it }
        DatabaseError
      }

    override suspend fun updateTranscript(
      translationAudioId: TranslationAudioId,
      transcript: String,
    ) = runCatching {
        translationAudioQueries.updateTranscript(transcript, translationAudioId)
    }.mapError {
      log.error { it }
      DatabaseError
    }

    override suspend fun updateTranslation(
      translationAudioId: TranslationAudioId,
      translationEnGoogle: TranslationEnGoogle
    ) = runCatching {
      translationAudioQueries.updateTranslation(translationEnGoogle, translationAudioId).executeAsOne().translation_google_en
    }.mapError {
      log.error { it }
      DatabaseError
    }

    override suspend fun getByQueryId(queryId: QueryId) =
      runCatching {
        translationAudioQueries.selectByQueryId(queryId).executeAsList()
      }.mapError {
        log.error { it }
        DatabaseError
      }

    override suspend fun updateTranscriptionStatus(
      translationAudioId: TranslationAudioId,
      status: TranscriptionStatus
    ) = runCatching {
      translationAudioQueries.updateTranscriptionStatus(status,translationAudioId)
    }.mapError {
      log.error { it }
      DatabaseError
    }

    override suspend fun getById(id: TranslationAudioId) =
      runCatching {
        translationAudioQueries.selectById(id).executeAsOne()
      }.mapError {
        log.error { it }
        DatabaseError
      }
  }

