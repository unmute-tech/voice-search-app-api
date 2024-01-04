package io.reitmaier.banjaraapi.repo


import com.github.michaelbull.logging.InlineLogger
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import io.reitmaier.banjaraapi.*
import io.reitmaier.banjaraapi.db.HydratedResult
import io.reitmaier.banjaraapi.db.HydratedResultQueries
import io.reitmaier.banjaraapi.db.QueryResult
import io.reitmaier.banjaraapi.db.QueryResultQueries
import kotlinx.serialization.Serializable
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import java.util.*

interface HydratedResultPersistence {
  /** Creates a new audio item in the database, and returns the [AudioID] of the newly created user */
  suspend fun getByQueryId(
    queryId: QueryId
  ) : Result<List<HydratedResult>, DomainMessage>
}

/** QueryPersistence implementation based on SqlDelight */
fun hydratedResultPersistence(
  hydratedResultQueries: HydratedResultQueries,
  log: InlineLogger = InlineLogger(),
) =
  object : HydratedResultPersistence {


    override suspend fun getByQueryId(queryId: QueryId): Result<List<HydratedResult>, DomainMessage> =
      runCatching {
        hydratedResultQueries.selectByQueryId(queryId).executeAsList()
      }.mapError { e ->
        log.debug { e.stackTraceToString() }
        DatabaseError
      }


  }

