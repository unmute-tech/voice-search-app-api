package io.reitmaier.banjaraapi.repo

import com.github.michaelbull.logging.InlineLogger
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.binding.binding
import com.google.cloud.translate.Translate
import com.google.cloud.translate.TranslateOptions
import io.reitmaier.banjaraapi.DomainMessage
import io.reitmaier.banjaraapi.TranscriptionError
import io.reitmaier.banjaraapi.TranslationError
import io.reitmaier.banjaraapi.db.*
import java.io.File


data class PhotoWithAudioAndQuery(
  val photo: PhotoInfo,
  val audio: List<Audio>,
  val queries: List<QueryWithResultRank>,
)
data class QueryWithResultRank(
  val query: Query,
  val ranking: Int,
)

data class HydratedQuery(
  val query: Query,
  val results: List<HydratedResult>,
  val translationAudio: List<TranslationAudio>,
  val nextId: QueryId?,
  val previousId: QueryId?,
)

class BanjaraRepo(
  private val audioPersistence: AudioPersistence,
  private val photoPersistence: PhotoPersistence,
  private val resultPersistence: ResultPersistence,
  private val hydratedResultPersistence: HydratedResultPersistence,
  private val queryPersistence: QueryPersistence,
  private val translationAudioPersistence: TranslationAudioPersistence,
  private val log: InlineLogger,
  private val speechService: SpeechService = SpeechService(),
  private val translationClient: Translate = TranslateOptions.getDefaultInstance().getService(),
) {
  suspend fun transcribeAudio(queryId: QueryId, translationAudioId: TranslationAudioId, language: Language, audioFile: File) =
    translationAudioPersistence.updateTranscriptionStatus(translationAudioId,TranscriptionStatus.PENDING)
      .andThen {
        runCatching {
          speechService.transcribeAudio(audioFile,language)
        }
          .mapError {
            log.error { it }
            TranscriptionError
          }
      }.andThen {transcript ->
        translationAudioPersistence.updateTranscript(translationAudioId,transcript)
          .map { transcript }
      }.onFailure {
        translationAudioPersistence.updateTranscriptionStatus(translationAudioId,TranscriptionStatus.FAILED)
      }
      .andThen {
        translate(it,language,translationAudioId)
      }

  suspend fun translate(translationAudioId: TranslationAudioId) =
    translationAudioPersistence.getById(translationAudioId)
      .andThen {ta ->
        translate(ta.transcript,ta.language,ta.id).map { ta.query_id  }
      }

  private suspend fun translate(text: String, sourceLanguage: Language, translationAudioId: TranslationAudioId) : Result<TranslationEnGoogle?, DomainMessage>  {
    return runCatching {
      val response = translationClient.translate(text, Translate.TranslateOption.targetLanguage(Language.ENGLISH.value), Translate.TranslateOption.sourceLanguage(Language.HINDI.value))
      TranslationEnGoogle(response.translatedText)
    }.mapError { TranslationError }
      .andThen { translation ->
        translationAudioPersistence.updateTranslation(translationAudioId,translation)
      }
  }

  suspend fun insertTranslationAudio(queryId: QueryId, language: Language, path: String) =
    translationAudioPersistence.insert(queryId,language,path)

  suspend fun getPhotoWithAudioAndQueries(alias: String): Result<PhotoWithAudioAndQuery, DomainMessage> =
    photoPersistence.getPhotoIdByAlias(alias).andThen { getPhotoWithAudioAndQueries(it) }

  suspend fun getPhotoWithAudioAndQueries(photoId: PhotoId) = binding {
    val photoInfo = photoPersistence.getPhotoById(photoId).bind()
    val audio = audioPersistence.getByPhotoId(photoId).bind()
    val queries = queryPersistence.getByPhotoId(photoId).bind()
    PhotoWithAudioAndQuery(photoInfo, audio,queries)
  }

  suspend fun addResults(queryId: QueryId, speechResults: List<SpeechResult>) =
    speechResults.sortedByDescending { it.confidence }.mapIndexed {i, it ->
      photoPersistence.getPhotoIdByAlias(it.photo)
        .andThen { photoId ->
          resultPersistence.insert(
            queryId = queryId,
            photoId = photoId,
            confidence = it.confidence,
            rating = it.rating,
            ranking = i + 1
          )
        }
    }
  suspend fun getHydratedQueryById(queryId: QueryId) =
    binding {
      val query = queryPersistence.getByQueryId(queryId).bind()
      val next = queryPersistence.getNextByQueryId(queryId).bind()
      val previous = queryPersistence.getPreviousByQueryId(queryId).bind()
      val results = hydratedResultPersistence.getByQueryId(queryId).bind()
      val translations = translationAudioPersistence.getByQueryId(query.id).bind()
      HydratedQuery(query, results, translations,  next?.id, previous?.id)
    }

  suspend fun getHydratedQueryBySampleId(sampleId: SampleId) =
    binding {
      val nextSampleId = sampleId.next().get()
      val prevSampleId = sampleId.prev().get()
      val query = queryPersistence.getBySampleId(sampleId).bind()
      val previous = if(prevSampleId != null) {
        queryPersistence.getBySampleId(prevSampleId).get()
      } else {
        null
      }
      val next = if(nextSampleId != null) {
        queryPersistence.getBySampleId(nextSampleId).get()
      } else {
        null
      }
      val results = hydratedResultPersistence.getByQueryId(query.id).bind()
      val translations = translationAudioPersistence.getByQueryId(query.id).bind()
      HydratedQuery(query, results, translations, next?.id, previous?.id)
    }


  suspend fun rateResults(queryId: QueryId, speechResult: SpeechResult) {
    binding {
      val photoId = photoPersistence.getPhotoIdByAlias(speechResult.photo).bind()
      resultPersistence.rate(queryId, photoId, speechResult.rating).bind()
    }
  }

}
