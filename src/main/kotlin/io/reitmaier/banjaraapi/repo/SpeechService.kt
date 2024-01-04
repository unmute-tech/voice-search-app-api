package io.reitmaier.banjaraapi.repo

import com.google.cloud.speech.v1.RecognitionAudio
import com.google.cloud.speech.v1.RecognitionConfig
import com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding
import com.google.cloud.speech.v1.RecognizeResponse
import com.google.cloud.speech.v1.SpeechClient
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

class SpeechService {

    suspend fun transcribeAudio(audioFile: File, language: Language): String =
        withContext(Dispatchers.IO) {
            val speechClient : SpeechClient = SpeechClient.create()
            val audioBytes = Files.readAllBytes(audioFile.toPath())
            val audio = RecognitionAudio.newBuilder()
                .setContent(ByteString.copyFrom(audioBytes))
                .build()
            val encoding = when(audioFile.extension) {
                "ogg",
                "oga",
                "opus" -> AudioEncoding.OGG_OPUS
                else -> AudioEncoding.LINEAR16
            }

            val config = RecognitionConfig.newBuilder()
                .setEncoding(encoding)
                .setSampleRateHertz(16000)  // Adjust this based on your WAV file's sample rate
                .setLanguageCode(language.value)   // Change this to the appropriate language code
                .build()

            val response: RecognizeResponse = speechClient.recognize(config, audio)

            val transcription = StringBuilder()
            for (result in response.resultsList) {
                transcription.append(result.alternativesList[0].transcript).append(" ")
            }
            speechClient.close()
            speechClient.shutdown()

            transcription.toString().trim()
        }

}