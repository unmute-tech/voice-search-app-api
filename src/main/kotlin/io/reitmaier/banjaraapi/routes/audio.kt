package io.reitmaier.banjaraapi.routes

import com.github.michaelbull.logging.InlineLogger
import com.github.michaelbull.result.*
import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.google.common.io.Files
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.reitmaier.banjaraapi.*
import io.reitmaier.banjaraapi.repo.AudioPersistence
import io.reitmaier.banjaraapi.repo.PhotoPersistence
import java.io.File
import java.io.IOException
import java.util.*

fun Application.audioRoutes(
  audioDir: File,
  photoPersistence: PhotoPersistence,
  audioPersistence: AudioPersistence,
  log: InlineLogger = InlineLogger()
) = routing {
  route("/audio/") {
    post {
      val multipartData = call.receiveMultipart().readAllParts()

      val fileItem =
        multipartData.filterIsInstance<PartData.FileItem>().firstOrNull() ?: return@post call.respondDomainMessage(
          RequestFileMissing
        )
      val displayName = fileItem.originalFileName ?: return@post call.respondDomainMessage(FileNameMissing)
        .also { fileItem.dispose() }

      log.debug { "Receiving: $displayName" }

      val extension = displayName.substringAfterLast(".", "mp3")

      val formItems = multipartData.filterIsInstance<PartData.FormItem>()

      val photoHash = formItems.firstOrNull { it.name == "photo" }?.value
        ?: return@post call.respondDomainMessage(PhotoHashMissing).also {
          fileItem.dispose()
        }

      val photoId = photoPersistence.exists(photoHash).get()
        ?: return@post call.respondDomainMessage(PhotoHashInvalid).also {
          fileItem.dispose()
        }

      val length = formItems.firstOrNull { it.name == "length" }?.value?.toDoubleOrNull()
        ?: return@post call.respondDomainMessage(AudioLengthMissing).also {
          fileItem.dispose()
        }

      val fileName = "${UUID.randomUUID()}.tmp"

      val tmpFile = audioDir.resolve(fileName)

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
        val exists = audioPersistence.exists(hash).get() != null
        if(exists) {
          Err(AudioAlreadyExists(hash))
        } else {
          Ok(hash)
        }
      }.andThen {hash ->
        runCatching {
          val audioFile = audioDir.resolve("$hash.$extension")
          val res = tmpFile.renameTo(audioFile)
          if(!res)
            throw IOException("Rename failed")
          Pair(audioFile, hash)
        }.mapError {
          IOError
        }
      }.andThen {
        audioPersistence.insert(photoId, it.first.path,it.second, (length*1000).toLong())
      }.fold(
        success = {call.respond(it.toString())},
        failure = {
          tmpFile.delete()
          call.respondDomainMessage(it)
        }
      )
    }
    }
  }
