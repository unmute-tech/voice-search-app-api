package io.reitmaier.banjaraapi

import com.github.michaelbull.logging.InlineLogger
import com.github.michaelbull.result.map
import com.google.cloud.translate.TranslateOptions
import com.google.cloud.translate.Translation
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.reitmaier.banjaraapi.data.Env
import io.reitmaier.banjaraapi.data.hikari
import io.reitmaier.banjaraapi.data.sqlDelight
import io.reitmaier.banjaraapi.plugins.configureHTTP
import io.reitmaier.banjaraapi.plugins.configureSerialization
import io.reitmaier.banjaraapi.repo.*
import io.reitmaier.banjaraapi.routes.*
import java.io.File

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {

  val env = Env()
  val log = InlineLogger()
  val dataDir = File("data")
  if(!dataDir.exists())
    dataDir.mkdir()
  val audioDir = dataDir.resolve("audio")
  if(!audioDir.exists())
    audioDir.mkdir()
  val photoDir = dataDir.resolve("photos")
  if(!photoDir.exists())
    photoDir.mkdir()

  val commentsDir = dataDir.resolve("comments")
  if(!commentsDir.exists())
    commentsDir.mkdir()

  val queryDir = dataDir.resolve("queries")
  if(!queryDir.exists())
    queryDir.mkdir()

  val translationDir = dataDir.resolve("translations")
  if(!translationDir.exists())
    translationDir.mkdir()

  val hikariDataSource = hikari(env.dataSource)

  val db = sqlDelight(hikariDataSource)
  val photoRepo = photoPersistence(db.photoQueries)
  val audioRepo = audioPersistence(db.audioQueries, log)
  val translationAudioPersistence = translationAudioPersistence(db.translationAudioQueries, log)
  val resultRepo = resultPersistence(db.queryResultQueries)
  val queryPersistence = queryPersistence(db.queryQueries)
  val hydratedResultPersistence = hydratedResultPersistence(db.hydratedResultQueries)
  val banjaraRepo = BanjaraRepo(
    audioPersistence = audioRepo,
    photoPersistence = photoRepo,
    queryPersistence = queryPersistence,
    hydratedResultPersistence = hydratedResultPersistence,
    resultPersistence = resultRepo,
    translationAudioPersistence = translationAudioPersistence,
    log = log,
  )
  val speechService = SpeechService()

  configureHTTP()

  staticRoutes()
  photoRoutes(
    photoDir = photoDir,
    photoPersistence = photoRepo,
    audioPersistence = audioRepo,
    banjaraRepo = banjaraRepo,
    log = log
  )
  audioRoutes(
    audioDir = audioDir,
    photoPersistence = photoRepo,
    audioPersistence = audioRepo,
    log = log
  )

  queryRoutes(
    queryDir = queryDir,
    translationDir = translationDir,
    commentsDir = commentsDir,
    queryPersistence = queryPersistence,
    banjaraRepo = banjaraRepo,
    speechService = speechService,
    log = log
  )
  translationAudioRoutes(banjaraRepo)
  configureSerialization()
}
