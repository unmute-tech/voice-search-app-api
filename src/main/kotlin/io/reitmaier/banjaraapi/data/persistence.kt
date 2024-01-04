package io.reitmaier.banjaraapi.data

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.github.michaelbull.result.get
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import io.reitmaier.banjaraapi.db.*
import io.reitmaier.banjaraapi.repo.*
import org.postgresql.Driver
import javax.sql.DataSource

fun Application.hikari(env: Env.DataSource): HikariDataSource =
  HikariDataSource(
    HikariConfig().apply {
      jdbcUrl = env.url
      username = env.username
      password = env.password
//      driverClassName = env.driver

      // Driver needs to be explicitly set in order to produce fatjar
      // https://github.com/brettwooldridge/HikariCP/issues/540
      driverClassName = Driver::class.java.name
    }
  )
fun Application.sqlDelight(dataSource: DataSource): BanjaraDb {
  val driver : SqlDriver =  dataSource.asJdbcDriver()
  val db = BanjaraDb(
    driver = driver,
    photoAdapter = Photo.Adapter(photoIdAdapter),
    audioAdapter = Audio.Adapter(audioIdAdapter, photoIdAdapter),
    queryAdapter = Query.Adapter(
      idAdapter = queryIdAdapter,
      includeAdapter = includeAdapter,
      translation_enAdapter = translationEnglishAdapter,
      translation_hiAdapter = translationHindiAdapter,
      translation_mrAdapter = translationMarathiAdapter,
      sample_idAdapter = sampleIdAdapter,
    ),
    queryResultAdapter =  QueryResult.Adapter(queryIdAdapter, photoIdAdapter, ratingAdapter),
    translationAudioAdapter = TranslationAudio.Adapter(
      idAdapter = translationAudioIdAdapter,
      query_idAdapter = queryIdAdapter,
      languageAdapter = languageAdapter,
      transcription_statusAdapter = transcriptStatusAdapter,
      translation_google_enAdapter = translationGoogleEnAdapter,
    ),

  )
  driver.migrate(db)
  return db
}

private fun SqlDriver.migrate(database: BanjaraDb) {
  // Check if setting's table exists
  if(runCatching {  database.settingsQueries.getSettings().executeAsOneOrNull() }.getOrNull() == null) {
    // Settings table is version 2
    BanjaraDb.Schema.migrate(this, 1, 2)
  }
  val settings = database.settingsQueries.getSettings().executeAsOne()
  val dbVersion = settings.version
  val schemaVersion = BanjaraDb.Schema.version
  println("Current db version: $dbVersion")
  for (version in (dbVersion until schemaVersion)) {
    println("Migrating to ${version + 1}")
    BanjaraDb.Schema.migrate(this, version, version + 1)
    database.settingsQueries.setVersion(version + 1)
  }
}
//
//private val articleIdAdapter = columnAdapter(::ArticleId, ArticleId::serial)
private val photoIdAdapter = columnAdapter(::PhotoId, PhotoId::serial)
private val audioIdAdapter = columnAdapter(::AudioId, AudioId::serial)
private val translationAudioIdAdapter = columnAdapter(::TranslationAudioId, TranslationAudioId::serial)
private val sampleIdAdapter = columnAdapter(::SampleId, SampleId::value)
private val queryIdAdapter = columnAdapter(::QueryId, QueryId::id)
private val ratingAdapter = columnAdapter(
  decode = { when(it) {
    Rating.POSITIVE.value -> Rating.POSITIVE
    Rating.NEGATIVE.value -> Rating.NEGATIVE
    else -> Rating.UNRATED
  }
  },
  encode = Rating::value
)

private val includeAdapter = columnAdapter(
  decode = {
    Include.byNameIgnoreCaseDefaultUnknown(it)
  },
  encode = Include::value
)

private val transcriptStatusAdapter = columnAdapter(
  decode = {
    TranscriptionStatus.byNameIgnoreCaseDefaultUnknown(it)
  },
  encode = TranscriptionStatus::name
)
private val languageAdapter = columnAdapter(
  decode = {
    Language.byValueIgnoreCase(it).get() ?: Language.UNKNOWN
  },
  encode = Language::value
)

private val translationEnglishAdapter = columnAdapter(::TranslationEnglish, TranslationEnglish::value)
private val translationHindiAdapter = columnAdapter(::TranslationHindi, TranslationHindi::value)
private val translationGoogleEnAdapter = columnAdapter(::TranslationEnGoogle, TranslationEnGoogle::value)
private val translationMarathiAdapter = columnAdapter(::TranslationMarathi, TranslationMarathi::value)
//private val localDateTimeAdapter = columnAdapter(Off::parse, LocalDateTime::toString)
//
private inline fun <A : Any, B> columnAdapter(
  crossinline decode: (databaseValue: B) -> A,
  crossinline encode: (value: A) -> B
): ColumnAdapter<A, B> =
  object : ColumnAdapter<A, B> {
    override fun decode(databaseValue: B): A = decode(databaseValue)
    override fun encode(value: A): B = encode(value)
  }
