package io.reitmaier.banjaraapi.routes

import com.github.michaelbull.logging.InlineLogger
import com.github.michaelbull.result.*
import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.google.common.io.Files
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.reitmaier.banjaraapi.*
import io.reitmaier.banjaraapi.repo.AudioPersistence
import io.reitmaier.banjaraapi.repo.BanjaraRepo
import io.reitmaier.banjaraapi.repo.PhotoId
import io.reitmaier.banjaraapi.repo.PhotoPersistence
import io.reitmaier.banjaraapi.views.Layout
import io.reitmaier.banjaraapi.views.ListViewTemplate
import io.reitmaier.banjaraapi.views.PhotoItemTemplate
import java.io.File
import java.io.IOException
import java.util.*

fun Application.photoRoutes(
  photoDir: File,
  photoPersistence: PhotoPersistence,
  audioPersistence: AudioPersistence,
  banjaraRepo: BanjaraRepo,
  log: InlineLogger = InlineLogger()
) = routing {
  route("/photo/") {

    get() {
      val photos = photoPersistence.getPhotos().get() ?: emptyList()
      val audioLength = (audioPersistence.getAudioLength().get() ?: 0.0) / 1000.0
      call.respondHtmlTemplate(Layout()) {
        content {
          insert(ListViewTemplate(photos, audioLength)) {}
        }
      }
    }
    post {
      val multipartData = call.receiveMultipart().readAllParts()

      val fileItem =
        multipartData.filterIsInstance<PartData.FileItem>().firstOrNull() ?: return@post call.respondDomainMessage(
          RequestFileMissing
        )
      val displayName = fileItem.originalFileName ?: return@post call.respondDomainMessage(FileNameMissing)
        .also { fileItem.dispose() }

      log.debug { "Receiving: $displayName" }

      val extension = displayName.substringAfterLast(".", "jpg")

      val formItems = multipartData.filterIsInstance<PartData.FormItem>()

      val uuid = UUID.randomUUID()
      val fileName = "${uuid}.tmp"

      val alias = formItems.firstOrNull { it.name == "alias" }?.value ?: uuid.toString()

      val tmpFile = photoDir.resolve(fileName)

      log.debug { "Writing to file: ${tmpFile.absolutePath}" }

      runCatching {
        fileItem.streamProvider().use { its ->
          // copy the stream to the file with buffering
          tmpFile.outputStream().buffered().use {
            // note that this is blocking
            its.copyTo(it)
          }
        }
        fileItem.dispose()
        val byteSource = Files.asByteSource(tmpFile)
        val hc: HashCode = byteSource.hash(Hashing.md5())
        val hash = hc.toString()
        hash
      }
        .mapError { IOError }
        .andThen { hash ->
          val exists = photoPersistence.exists(hash).get() != null
          if(exists) {
            Err(PhotoAlreadyExists(hash))
          } else {
            Ok(hash)
          }
        }.andThen {hash ->
          runCatching {
            val photoFile = photoDir.resolve("$hash.$extension")
            val res = tmpFile.renameTo(photoFile)
            if(!res)
              throw IOException("Rename failed")
            Pair(photoFile, hash)
          }.mapError {
            IOError
          }
        }.andThen {
          photoPersistence.insert(it.first.path,it.second, alias)
        }.fold(
          success = {call.respond(it.toString())},
          failure = {
            tmpFile.delete()
            call.respondDomainMessage(it)
          }
        )
    }

    get("{$PHOTO_ID_PARAMETER}") {
      call.parameters.readPhotoId()
        .andThen {
          banjaraRepo.getPhotoWithAudioAndQueries(it)
        }.orElse {
          call.parameters.readPhotoAlias().andThen {
            banjaraRepo.getPhotoWithAudioAndQueries(it)
          }
        }
        .fold(
          failure = { call.respondDomainMessage(it) },
          success = { info ->
            call.respondHtmlTemplate(Layout()) {
              content {
                insert(PhotoItemTemplate(info)) {}
              }
            }
          }
        )
    }
  }
}
const val PHOTO_ID_PARAMETER = "photoId"
const val TICKBOX_PARAMETER = "include"

private fun Parameters.readPhotoId(): Result<PhotoId, DomainMessage> {
  return get(PHOTO_ID_PARAMETER)
    .toResultOr { PhotoIdInvalid }
    .andThen {
      log.debug { "Read $PHOTO_ID_PARAMETER = $it" }
      val serial = it.toLongOrNull() ?: return Err(PhotoIdInvalid)
      log.debug { "Parsed $PHOTO_ID_PARAMETER = $it" }
      Ok(PhotoId(serial))
    }
    .mapError { PhotoIdInvalid }

}

private fun Parameters.readPhotoAlias(): Result<String, DomainMessage> {
  return get(PHOTO_ID_PARAMETER)
    .toResultOr { PhotoIdInvalid }
}
