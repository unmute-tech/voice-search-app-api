package io.reitmaier.banjaraapi.repo


import com.github.michaelbull.logging.InlineLogger
import com.github.michaelbull.result.*
import io.reitmaier.banjaraapi.*
import io.reitmaier.banjaraapi.db.Query
import io.reitmaier.banjaraapi.db.QueryQueries
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import java.util.*

@JvmInline value class SampleId(val value: Int) {
  val isValid : Boolean get() = this.value > 0;
  fun next()  = SampleId.from(this.value + 1)
  fun prev()  = SampleId.from(this.value - 1)
  companion object {
    fun from(newValue: Int) =
      if(newValue > 0) {
        Ok(SampleId(newValue))
      } else {
        Err(SampleIdInvalid)
      }
  }
}
@JvmInline value class TranslationEnglish(val value: String)
@JvmInline value class TranslationMarathi(val value: String)
@JvmInline value class TranslationHindi(val value: String)
@JvmInline value class QueryId(val id: UUID) {
  companion object {
    fun from(input: String?) =
      runCatching {
        QueryId(
          UUID.fromString(input)
        )
      }.mapError { QueryIdInvalid }
  }
}

enum class Language(val value: String) {
  MARATHI("mr-IN"),
  HINDI("hi-IN"),
  UNKNOWN("Unknown"),
  ENGLISH("en-UK");
  companion object {
    fun byValueIgnoreCase(input: String?) =
      runCatching {
        entries.first { it.value.equals(input, true) }
      }.mapError { LanguageInvalid }
    }
  }

enum class Include(val value: String) {
  UNKNOWN("Unknown"),
  EXCLUDE("Exclude"),
  INCLUDE("Include"),
  DISCUSS("Discuss");
  companion object {
    fun byNameIgnoreCaseDefaultUnknown(input: String): Include {
      return entries.firstOrNull { it.name.equals(input, true) } ?: UNKNOWN
    }
  }
}


interface QueryPersistence {
  /** Creates a new audio item in the database, and returns the [AudioID] of the newly created user */
  suspend fun insert(queryId: QueryId, path: String): Result<QueryId, DomainMessage>
  suspend fun updateCommentPath(queryId: QueryId, commentPath: String): Result<QueryId, DomainMessage>
  suspend fun exists(queryId: QueryId) : Result<QueryId?, DomainMessage>
  suspend fun getByQueryId(queryId: QueryId) : Result<Query, DomainMessage>
  suspend fun getBySampleId(sampleId: SampleId) : Result<Query, DomainMessage>
  suspend fun getByPhotoId(photoId: PhotoId) : Result<List<QueryWithResultRank>, DomainMessage>
  suspend fun getNextByQueryId(queryId: QueryId) : Result<Query?, DomainMessage>
  suspend fun getPreviousByQueryId(queryId: QueryId) : Result<Query?, DomainMessage>
  suspend fun getQueries() : Result<List<Query>, DomainMessage>
  suspend fun updateInclude(queryId: QueryId, include: Include) : Result<QueryId, DomainMessage>
  suspend fun updateTextComment(queryId: QueryId, textComment: String) : Result<QueryId, DomainMessage>
  suspend fun updateTranslation(queryId: QueryId, translationEnglish: TranslationEnglish) : Result<QueryId, DomainMessage>
  suspend fun updateTranslation(queryId: QueryId, translationMarathi: TranslationMarathi) : Result<QueryId, DomainMessage>
  suspend fun updateTranslation(queryId: QueryId, translationHindi: TranslationHindi): Result<QueryId, DomainMessage>
}

/** QueryPersistence implementation based on SqlDelight */
fun queryPersistence(
  queryQueries: QueryQueries,
  log: InlineLogger = InlineLogger(),
) =
  object : QueryPersistence {

    override suspend fun insert(
      queryId: QueryId,
      path: String,
    ): Result<QueryId, DomainMessage> =
      runCatching {
        queryQueries.insertAndGetId(queryId, path).executeAsOne()
      }.mapError { e ->
        log.debug { e.stackTraceToString() }
        if (e is PSQLException && e.sqlState == PSQLState.UNIQUE_VIOLATION.state) {
          QueryAlreadyExists(queryId)
        }
        DatabaseError
      }

    override suspend fun updateCommentPath(
      queryId: QueryId,
      commentPath: String
    ): Result<QueryId, DomainMessage> = runCatching {
      val existingCommentPath =
        queryQueries.selectById(queryId).executeAsOneOrNull()?.commentPath
      val newCommentPath = if(existingCommentPath == null) {
        commentPath
      } else {
        "$existingCommentPath,$commentPath"
      }
      queryQueries.updateCommentPath(newCommentPath, queryId)
    }.mapError { e ->
      log.debug { e.stackTraceToString() }
      if (e is PSQLException && e.sqlState == PSQLState.UNIQUE_VIOLATION.state) {
        QueryAlreadyExists(queryId)
      }
      DatabaseError
    }.map {
      queryId
    }

    override suspend fun updateInclude(queryId: QueryId, include: Include): Result<QueryId, DomainMessage> =
      runCatching {
        queryQueries.updateInclude(include, queryId)
      }.mapError { e ->
        log.debug { e.stackTraceToString() }
        DatabaseError
      }.map { queryId }

    override suspend fun updateTextComment(queryId: QueryId, textComment: String): Result<QueryId, DomainMessage> =
      runCatching {
        queryQueries.updateTextComment(textComment, queryId)
      }.mapError { e ->
        log.debug { e.stackTraceToString() }
        DatabaseError
      }.map { queryId }

    override suspend fun updateTranslation(queryId: QueryId, translationMarathi: TranslationMarathi) =
      runCatching {
        queryQueries.updateTranslationMarathi(translationMarathi, queryId)
      }.mapError { e ->
        log.debug { e.stackTraceToString() }
        DatabaseError
      }.map { queryId }

    override suspend fun updateTranslation(queryId: QueryId, translationEnglish: TranslationEnglish) =
      runCatching {
        queryQueries.updateTranslationEnglish(translationEnglish, queryId)
      }.mapError { e ->
        log.debug { e.stackTraceToString() }
        DatabaseError
      }.map { queryId }

    override suspend fun updateTranslation(queryId: QueryId, translationHindi: TranslationHindi) =
      runCatching {
        queryQueries.updateTranslationHindi(translationHindi, queryId)
      }.mapError { e ->
        log.debug { e.stackTraceToString() }
        DatabaseError
      }.map { queryId }

      override suspend fun exists(queryId: QueryId): Result<QueryId?, DomainMessage> =
      runCatching {
        queryQueries.selectById(queryId).executeAsOneOrNull()?.id
      }.mapError { e ->
        log.debug { e.stackTraceToString() }
        DatabaseError
      }

    override suspend fun getByQueryId(queryId: QueryId): Result<Query, DomainMessage> =
      runCatching {
        queryQueries.selectById(queryId).executeAsOne()
      }.mapError { e ->
        log.debug { e.stackTraceToString() }
        DatabaseError
      }

    override suspend fun getBySampleId(sampleId: SampleId): Result<Query, DomainMessage> =
      runCatching {
        queryQueries.selectBySampleId(sampleId).executeAsOne()
      }.mapError { e ->
        log.debug { e.stackTraceToString() }
        DatabaseError
      }

    override suspend fun getByPhotoId(photoId: PhotoId): Result<List<QueryWithResultRank>, DomainMessage> =
      runCatching {
        queryQueries.selectByPhotoId(photoId).executeAsList()
      }.map { list ->
        list.map {
          val query = Query(
            id = it.query_id,
            path = it.path,
            created_at = it.created_at,
            updated_at = it.updated_at,
            commentPath = it.commentPath,
            text_comment = it.text_comment,
            include = it.include,
            translation_en = it.translation_en,
            translation_hi = it.translation_hi,
            sample_id = it.sample_id,
            translation_mr = it.translation_mr,
          )
          QueryWithResultRank(query,it.ranking)
        }
      }
        .mapError { e ->
        log.debug { e.stackTraceToString() }
        DatabaseError
      }
    override suspend fun getNextByQueryId(queryId: QueryId): Result<Query?, DomainMessage> =
      runCatching {
        queryQueries.selectNextById(queryId).executeAsOneOrNull()
      }.mapError { e ->
        log.debug { e.stackTraceToString() }
        DatabaseError
      }

    override suspend fun getPreviousByQueryId(queryId: QueryId): Result<Query?, DomainMessage> =
      runCatching {
        queryQueries.selectPreviousById(queryId).executeAsOneOrNull()
      }.mapError { e ->
        log.debug { e.stackTraceToString() }
        DatabaseError
      }

    override suspend fun getQueries(): Result<List<Query>, DomainMessage> =
      runCatching {
        queryQueries.selectAll().executeAsList()
      }.mapError { e ->
        log.debug { e.stackTraceToString() }
        DatabaseError
      }

  }

