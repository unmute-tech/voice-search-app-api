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
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState

@JvmInline value class AudioId(val serial: Long)

interface AudioPersistence {
  /** Creates a new audio item in the database, and returns the [AudioID] of the newly created user */
  suspend fun insert(photoId: PhotoId, path: String, hash: String, length: Long): Result<AudioId, DomainMessage>
  suspend fun exists(hash: String) : Result<AudioId?, DomainMessage>
  suspend fun getByPhotoId(photoId: PhotoId) : Result<List<Audio>, DomainMessage>
  suspend fun getAudioLength() : Result<Double, DomainMessage>
  suspend fun getAudioLengthByPhotoId(photoId: PhotoId) : Result<Double, DomainMessage>
}

/** AudioPersistence implementation based on SqlDelight */
fun audioPersistence(
  audioQueries: AudioQueries,
  log: InlineLogger,
) =
  object : AudioPersistence {

    override suspend fun insert(
      photoId: PhotoId,
      path: String,
      hash: String,
      length: Long,
    ): Result<AudioId, DomainMessage> =
      runCatching {
        audioQueries.insertAndGetId(photoId, hash, path, length).executeAsOne()
      }.mapError { e ->
        if (e is PSQLException && e.sqlState == PSQLState.UNIQUE_VIOLATION.state) {
          AudioAlreadyExists(hash)
        }
        DatabaseError
      }

    override suspend fun exists(hash: String): Result<AudioId?, DomainMessage> =
      runCatching {
        audioQueries.selectByHash(hash).executeAsOneOrNull()
      }.mapError {
        log.error { it }
        DatabaseError
      }

    override suspend fun getByPhotoId(photoId: PhotoId): Result<List<Audio>, DomainMessage> =
      runCatching {
        audioQueries.selectByPhotoId(photoId).executeAsList()
      }.mapError {
        log.error { it }
        DatabaseError
      }

    override suspend fun getAudioLength(): Result<Double, DomainMessage> =
      runCatching {
        audioQueries.selectAudioLengthSum().executeAsOne().SUM!!
      }.mapError {
        log.error { it }
        DatabaseError
      }

    override suspend fun getAudioLengthByPhotoId(
      photoId: PhotoId,
    ): Result<Double, DomainMessage> =
      runCatching {
        audioQueries.selectAudioLengthSumByPhotoId(photoId)
          .executeAsOne().SUM!!
      }.mapError {
        log.error { it }
        DatabaseError
      }
  }

