package io.reitmaier.banjaraapi.repo


import com.github.michaelbull.logging.InlineLogger
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import io.reitmaier.banjaraapi.*
import io.reitmaier.banjaraapi.db.QueryResult
import io.reitmaier.banjaraapi.db.QueryResultQueries
import kotlinx.serialization.Serializable
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import java.util.*

@Serializable
enum class Rating(val value: Int) {
  POSITIVE(1),
  NEGATIVE(-1),
  UNRATED(0),
}

@Serializable
data class SpeechResult(
  val photo: String,
  val confidence: Double,
  val rating: Rating = Rating.UNRATED,
)

typealias QueryResultId = Pair<QueryId, PhotoId>
interface ResultPersistence {
  /** Creates a new audio item in the database, and returns the [AudioID] of the newly created user */
  suspend fun insert(
    queryId: QueryId,
    photoId: PhotoId,
    confidence: Double,
    rating: Rating,
    ranking: Int,
  ): Result<QueryResultId, DomainMessage>
  suspend fun exists(
    queryId: QueryId,
    photoId: PhotoId,
  ) : Result<QueryResult?, DomainMessage>
  suspend fun rate(
    queryId: QueryId,
    photoId: PhotoId,
    rating: Rating,
  ) : Result<Unit, DomainMessage>
  suspend fun getByQueryId(
    queryId: QueryId
  ) : Result<List<QueryResult>, DomainMessage>
}

/** QueryPersistence implementation based on SqlDelight */
fun resultPersistence(
  resultQueries: QueryResultQueries,
  log: InlineLogger = InlineLogger(),
) =
  object : ResultPersistence {

    override suspend fun insert(
      queryId: QueryId,
      photoId: PhotoId,
      confidence: Double,
      rating: Rating,
      ranking: Int,
    ): Result<QueryResultId, DomainMessage> =
      runCatching {
        resultQueries.insert(queryId, photoId, confidence, rating, ranking)
      }.mapError { e ->
        log.debug { e.stackTraceToString() }
        if (e is PSQLException && e.sqlState == PSQLState.UNIQUE_VIOLATION.state) {
          QueryResultAlreadyExists(queryId, photoId)
        }
        DatabaseError
      }.map {
        QueryResultId(queryId, photoId)
      }

    override suspend fun exists(
      queryId: QueryId,
      photoId: PhotoId,
    ) : Result<QueryResult?, DomainMessage> =
      runCatching {
        resultQueries.selectByQueryIdPhotoId(queryId, photoId).executeAsOneOrNull()
      }.mapError { e ->
        log.debug { e.stackTraceToString() }
        DatabaseError
      }

    override suspend fun rate(
      queryId: QueryId,
      photoId: PhotoId,
      rating: Rating
    ): Result<Unit, DomainMessage> =
      runCatching {
        resultQueries.updateResultRating(rating, queryId, photoId)
      }.mapError { e ->
        log.debug { e.stackTraceToString() }
        DatabaseError
      }

    override suspend fun getByQueryId(queryId: QueryId): Result<List<QueryResult>, DomainMessage> =
      runCatching {
        resultQueries.selectByQueryId(queryId).executeAsList()
      }.mapError { e ->
        log.debug { e.stackTraceToString() }
        DatabaseError
      }


  }

