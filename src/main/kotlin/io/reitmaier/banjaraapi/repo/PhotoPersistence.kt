package io.reitmaier.banjaraapi.repo


import com.github.michaelbull.logging.InlineLogger
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import io.reitmaier.banjaraapi.DatabaseError
import io.reitmaier.banjaraapi.DomainMessage
import io.reitmaier.banjaraapi.PhotoAlreadyExists
import io.reitmaier.banjaraapi.db.PhotoQueries
import io.reitmaier.banjaraapi.db.PhotoWithAudio
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import java.time.OffsetDateTime

@JvmInline value class PhotoId(val serial: Long)

interface PhotoPersistence {
  /** Creates a new user in the database, and returns the [UserId] of the newly created user */
  suspend fun insert(
    path: String,
    hash: String,
    alias: String,
  ): Result<PhotoId, DomainMessage>
  suspend fun exists(hash: String) : Result<PhotoId?, DomainMessage>
  suspend fun getPhotoById(photoId: PhotoId) : Result<PhotoInfo, DomainMessage>
  suspend fun getPhotos() : Result<List<PhotoInfo>, DomainMessage>
  suspend fun getPhotoIdByAlias(alias: String): Result<PhotoId, DomainMessage>
}

/** PhotoPersistence implementation based on SqlDelight */
fun photoPersistence(
  photoQueries: PhotoQueries,
  log: InlineLogger = InlineLogger(),
) =
  object : PhotoPersistence {

    override suspend fun insert(
      path: String,
      hash: String,
      alias: String,
    ): Result<PhotoId, DomainMessage> =
      runCatching {
        photoQueries.insertAndGetId(hash, path, alias).executeAsOne()
      }.mapError { e ->
        log.error { e }
        if (e is PSQLException && e.sqlState == PSQLState.UNIQUE_VIOLATION.state) {
          PhotoAlreadyExists(hash)
        }
        DatabaseError
      }

    override suspend fun exists(hash: String): Result<PhotoId?, DomainMessage> =
      runCatching {
        photoQueries.selectByHash(hash).executeAsOneOrNull()
      }.mapError {
        log.error { it }
        DatabaseError
      }

    override suspend fun getPhotoById(photoId: PhotoId): Result<PhotoInfo, DomainMessage> =
      runCatching {
        photoQueries.selectPhotoWithAudioById(photoId).executeAsOne()
      }.mapError {
        log.error { it }
        DatabaseError
      }.map {
        PhotoInfo.from(it)
      }

    override suspend fun getPhotoIdByAlias(alias: String): Result<PhotoId, DomainMessage> =
      runCatching {
        photoQueries.selectByAlias(alias).executeAsOne()
      }.mapError {
        log.error { it }
        DatabaseError
      }
    override suspend fun getPhotos(): Result<List<PhotoInfo>, DomainMessage> =
      runCatching {
        photoQueries.selectPhotosWithAudio().executeAsList()
          .map {
            PhotoInfo.from(it)
          }
      }.mapError {
        log.error { it }
        DatabaseError
      }
  }

data class PhotoInfo(
  val id: PhotoId,
  val hash: String,
  val path: String,
  val alias: String,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
  val audioLength: Double,
) {
  companion object {
    fun from(photo: PhotoWithAudio) =
      PhotoInfo(
        id = photo.id,
        hash = photo.hash,
        path = photo.path,
        alias = photo.alias,
        createdAt = photo.created_at,
        updatedAt = photo.updated_at,
        audioLength = (photo.audio_length ?: 0.0) / 1000,
      )
  }
}
