package io.reitmaier.banjaraapi.views

import com.github.michaelbull.result.get
import io.ktor.server.html.*
import io.reitmaier.banjaraapi.repo.*
import kotlinx.html.*
import java.text.DecimalFormat
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.roundToInt

private val dtf = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss")
val df = DecimalFormat("#.####")

class QueryResultTemplate(
  private val queryWithResult: HydratedQuery
): Template<FlowContent> {
  private val query = queryWithResult.query
  private val results = queryWithResult.results
  private val translationAudio = queryWithResult.translationAudio
  override fun FlowContent.apply() {
    h1(classes = "title") { +"User Query Result"}
    h3 {
      +"Date:"
    }
    p {
      +"${query.created_at.format(dtf)}"
    }
    if(query.sample_id.isValid) {
      h3 {
        +"Sample Id:"
      }
      val sampleId = query.sample_id
      val next = sampleId.next().get()
      val prev = sampleId.prev().get()
        if(prev != null) {
          a("/query/${prev.value}") {
            span(classes = "tag is-link is-light") {
              +"Prev (${prev.value})"
            }
          }
        }
      span(classes = "tag is-primary") {
        +"${query.sample_id.value}"
      }

      if(next != null) {
        a("/query/${next.value}") {
          span(classes = "tag is-link is-light") {
            +"Next (${next.value})"
          }
        }
      }
    }
    h3 {
      +"ID:"
    }
    p {
      +"${query.id.id}"
    }
    h3 {
      +"Query:"
    }
    audio {
      controls = true
      src = "/${query.path}"
      attributes["type"] = "audio/mpeg"
      attributes["preload"] = "none"
    }
    if(query.commentPath != null) {
      h3 {
        +"Audio Comments:"
      }
      val audios = query.commentPath.split(",")
      for(a in audios) {
        audio {
          controls = true
          src = "/$a"
          attributes["type"] = "audio/mpeg"
          attributes["preload"] = "none"
        }
      }
    }
    h3 {
      +"Translations"
    }
    Language.entries.filter { it != Language.UNKNOWN }.forEach { language ->
      val langName =
        language.name.lowercase().replaceFirstChar { if(it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
      val translationAudioItems = translationAudio.filter { it.language == language }
      h4 {
        +"$langName Translation Audio"
      }
      if(translationAudioItems.isNotEmpty()) {
        table(
          classes = "table is-bordered is-hoverable"
        ) {
          thead {
            tr {
              th { +"ID" }
              th { +"Date" }
              th { +"Audio" }
              th { +"Google Transcription" }
              if(language != Language.ENGLISH) {
                th { +"Google Translation" }
              }
            }
          }
          for (ta in translationAudioItems) {
            tbody {
              tr {
                th {
                  +ta.id.serial.toString()
                }
                td {
                  +ta.created_at.format(dtf)
                }
                td {
                  audio {
                    controls = true
                    src = "/${ta.path}"
                    attributes["type"] = "audio/mpeg"
                    attributes["preload"] = "none"
                  }
                }
                td {
                  when(ta.transcription_status) {
                    TranscriptionStatus.PENDING,
                    TranscriptionStatus.UNKNOWN,
                    TranscriptionStatus.FAILED -> em { +ta.transcription_status.name }
                    TranscriptionStatus.COMPLETED -> +ta.transcript
                  }
                }
                if(language != Language.ENGLISH) {
                  td {
                    if(ta.translation_google_en != null) {
                      +ta.translation_google_en.value
                    } else {
                      if(ta.transcription_status == TranscriptionStatus.COMPLETED) {
                        form(action = "/translationAudio/${ta.id.serial}/translate", method = FormMethod.post) {
                          button(type = ButtonType.submit, classes = "button") {
                            id = query.id.id.toString()
                            +"Translate"
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      form(action = "/query/${query.id.id}/translationAudio", method = FormMethod.post, encType = FormEncType.multipartFormData) {
        name = "translationAudio${langName}Form"
        input(type = InputType.hidden) {
          name = "lang"
          value = language.value
        }
        input(type = InputType.file){
          name = "translationAudio"
          attributes["placeholder"] = "$langName Audio Translation"
          attributes["autocomplete"] = "off"
          id = query.id.id.toString()
        }
        br {  }
        button(type = ButtonType.submit, classes = "button") {
          +"Upload $langName Translation Audio"
        }
      }
      br {  }
      h4 {
        +"(Corrected) $langName Translation Text"
      }
      val translationText = when(language) {
        Language.MARATHI -> query.translation_mr.value
        Language.ENGLISH -> query.translation_en.value
        Language.UNKNOWN -> ""
        Language.HINDI -> query.translation_hi.value
      }
      form(action = "/query/${query.id.id}/translation", method = FormMethod.post) {
        name = "translation${langName}Form"
        textArea(classes = "textarea"){
          name = "translation${langName}"
          attributes["autocomplete"] = "off"
          attributes["placeholder"] = "E.g. show me the picture of ..."
          id = query.id.id.toString()
          +"$translationText"
        }
        br {  }
        button(type = ButtonType.submit, classes = "button is-primary is-outlined") {
          +"Save ${langName} Translation Text"
        }
      }
      br {  }
    }
    h3 {
      +"Include in Dataset"
    }
    div(classes = "select") {
      select()
      {
        id = query.id.id.toString()
        attributes["autocomplete"] = "off"
        attributes["name"] = "includeSelect"
        Include.entries.forEach {
          option {
            if(it.value == query.include.value) {
              attributes["selected"] = "true"
            }
            +"${it.value}"
          }
        }
      }
    }
    h3 {
      +"Comments:"
    }
    form(action = "/query/${query.id.id}/textComment", method = FormMethod.post) {
      name = "textCommentForm"
      textArea(classes = "textarea"){
        name = "textComment"
        attributes["autocomplete"] = "off"
        attributes["placeholder"] = "E.g. testing, blank, partial, etc"
        id = query.id.id.toString()
        +query.text_comment
      }
      br {  }
      button(type = ButtonType.submit, classes = "button is-primary is-outlined") {
        +"Save Comments"
      }
    }

    h3 {
      +"Navigation"
    }
    a("/query/#${query.id.id}") {
      +"All Queries"
    }
    br {}
    if(queryWithResult.previousId != null) {
      a("/query/${queryWithResult.previousId.id}") {
        +"Previous Query"
      }
    }
    if(queryWithResult.nextId != null) {
      br {  }
      a("/query/${queryWithResult.nextId.id}") {
        +"Next Query"
      }
    }
    if(results.isNotEmpty()) {
      h3 {
        +"Results"
      }
      results.forEach { result ->
        val ratingClass = when(result.rating) {
          Rating.POSITIVE -> "positive"
          Rating.NEGATIVE -> "negative"
          Rating.UNRATED -> "unrated"
        }
        a("/photo/${result.photo_id.serial}"){
          img(
            classes = ratingClass
          ) {
            src = "/${result.photo_path}"
            alt = result.photo_alias
          }
        }
        p {
          +"${result.rating} (${df.format(result.confidence)}) ${result.photo_alias} (${result.photo_id.serial}) "
        }
      }
    } else {
      h3 {
        "No Results"
      }
    }
  }
}
class QueryListViewTemplate(
  private val queries: List<HydratedQuery>,
): Template<FlowContent> {
  override fun FlowContent.apply() {
    h1(classes = "title") { +"User Queries"}
    insert(QueryList(queries)){}
  }
}

class QueryList(
  private val queries: List<HydratedQuery>,
) : Template<FlowContent> {
  override fun FlowContent.apply() {
    table(
      classes = "table is-bordered is-hoverable"
    ) {
      thead {
        tr {
          th { +"ID"}
          th { +"Date"}
          th { +"Include"}
          th { +"Audio"}
          th { +"Comment"}
          th { +"First Positive"}
        }
      }
      tbody {
        for (q in queries) {
          tr {
            insert(QueryRow(q)) {}
          }
        }
      }
    }
  }
}

class QueryRow(
  queryWithResults: HydratedQuery,
) : Template<TR> {
  val query = queryWithResults.query
  val results = queryWithResults.results
  override fun TR.apply() {
    th {
      attributes["id"] = query.id.id.toString()
      a("/query/${query.id.id}") { +"${query.id.id}"}
    }
    td {
      +query.created_at.format(dtf)
    }

    td {
      div(classes = "select") {
        select()
        {
          id = query.id.id.toString()
          attributes["autocomplete"] = "off"
          attributes["name"] = "includeSelect"
          Include.entries.forEach {
            option {
              if(it == query.include) {
                attributes["selected"] = "true"
              }
              +it.value
            }
          }
        }
      }
    }
    td {
      audio {
        controls = true
        src = "/${query.path}"
        attributes["type"] = "audio/mpeg"
        attributes["preload"] = "none"
      }
    }
    td {
      if(query.commentPath != null) {
        val audios = query.commentPath.split(",")
        for(a in audios) {
          audio {
            controls = true
            src = "/$a"
            attributes["type"] = "audio/mpeg"
            attributes["preload"] = "none"
          }
        }
      }
    }

    td {
      val firstPositive = results.indexOfFirst { it.rating == Rating.POSITIVE } + 1
      if(firstPositive >= 1) {
        +"$firstPositive"
      }
      if(results.all { it.rating == Rating.UNRATED }) {
        +"Unrated"
      }
    }
  }
}
class ListViewTemplate(
  private val photos: List<PhotoInfo>,
  private val audioLength: Double,
): Template<FlowContent> {
  override fun FlowContent.apply() {
    h1(classes = "title") { +"Banjara Data"}
    p {
      +"Total Audio Length: ${audioLength.roundToInt()} seconds"
    }
    insert(PhotoList(photos)){}
//    insert(TaskStatus(tasks, users)){}
  }
}

class PhotoItemTemplate(
  private val info: PhotoWithAudioAndQuery,
): Template<FlowContent> {
  private val photo = info.photo
  private val audioItems = info.audio
  private val queries = info.queries
  override fun FlowContent.apply() {
    h1(classes = "title") { +"${photo.alias.replaceFirstChar { it.uppercaseChar() }}:  ${photo.audioLength.roundToInt()} seconds" }
    a("/${photo.path}") {
      img {
        src = "/${photo.path}"
      }
    }
    if(queries.isNotEmpty()) {
      h3 {
        +"Associated Queries For Photo:"
      }
      table(
        classes = "table is-bordered is-hoverable"
      ) {
        thead {
          tr {
            th { +"Query Audio" }
            th { +"Query Id" }
            th { +"Photo Result Ranking" }
          }
        }
        queries.forEach { queryWithRanking ->
          tr {
            th {
              audio {
                controls = true
                src = "/${queryWithRanking.query.path}"
                attributes["type"] = "audio/mpeg"
                attributes["preload"] = "none"
              }
            }
            td {
              attributes["id"] = queryWithRanking.query.id.id.toString()
              a("/query/${queryWithRanking.query.id.id}") { +"${queryWithRanking.query.id.id}"}
            }
            td {
              +"${queryWithRanking.ranking}"
            }
          }
        }
      }
    }
    if(audioItems.isNotEmpty()) {
      h3 {
        +"Associated Community-Sourced Audio Recordings:"
      }
      audioItems.forEach { audioItem ->
        p {
          audio {
            controls = true
            src = "/${audioItem.path}"
            attributes["type"] = "audio/mpeg"
            attributes["preload"] = "none"
          }
          +"${audioItem.id.serial}: ${audioItem.length / 1000} seconds, ${audioItem.hash}"
        }
      }
    }
  }
}



class PhotoList(
  private val photos: List<PhotoInfo>,
) : Template<FlowContent> {
  override fun FlowContent.apply() {
    h2(classes = "title") { +"Photos"}
    table(
      classes = "table is-bordered is-hoverable"
    ) {
      thead {
        tr {
          th { +"ID"}
          th { +"Alias"}
          th { +"Audio"}
          th { +"Thumbnail"}
        }
      }
      tbody {
        for (p in photos) {
          tr {
            insert(PhotoRow(p)) {}
          }
        }
      }
    }
  }
}

class PhotoRow(
  private val photo: PhotoInfo,
) : Template<TR> {
  override fun TR.apply() {
    th {
      a("/photo/${photo.id.serial}") { +"${photo.id.serial}"}
    }
    td {
      +"${photo.alias.replaceFirstChar { it.uppercaseChar() }}"
    }
    td {
      +"${photo.audioLength.roundToInt()}"
    }
    td {
      a("/photo/${photo.id.serial}"){
        img() {
          src = "/${photo.path}"
        }
      }
    }
  }
}


//class TaskRow(
//  private val task: Hydrated_task,
//  private val audio: Boolean
//) : Template<TR> {
//  override fun TR.apply() {
//    th {
//      +"${task.path.substringAfter("/")}"
//      if(audio) {
//        br {}
//        audio {
//          controls = true
//          src = "/user/${task.user_id.value}/task/${task.id.value}/file"
//          attributes["type"] = "audio/mpeg"
//          attributes["preload"] = "none"
//        }
//      }
//    }
//    td { +"${task.user_id.value} "}
//      td {
//        if(task.photo.isNullOrBlank() && task.reject_reason == null) {
//          em {
//            +"Awaiting Transcription"
//          }
//        } else if(task.reject_reason != null) {
//          em {
//            +"${task.reject_reason}"
//          }
//        }
//        else {
//          +"${task.photo}"
//        }
//      }
//  }
//}
//class TaskStatus(
//  private val tasks: List<Hydrated_task>,
//  private val users: List<User>,
//) : Template<FlowContent> {
//  override fun FlowContent.apply() {
//    val groupedTasks = tasks.groupBy { it.path }.map { it.key to it.value }
//      .sortedByDescending { it.second.size  }
//    h2(classes = "title") { +"Tasks"}
//    table(
//      classes = "table is-bordered is-hoverable"
//    ) {
//      // TODO headers
//      // TODO footers
//      thead {
//        tr {
//          th { +"ID"}
//          th { +"User"}
//          th { +"Transcript"}
//        }
//      }
//      tbody {
//        for (t in groupedTasks) {
//          t.second.forEachIndexed { index, item ->
//            tr {
//              insert(TaskRow(item, index == 0)) {}
//            }
//          }
//        }
//      }
//    }
//  }
//}

class Layout: Template<HTML> {
  val content =  Placeholder<HtmlBlockTag>()
//  val menu = TemplatePlaceholder<NavTemplate>()

  override fun HTML.apply() {
    head{
      title { +"Banjara Data" }
      meta { charset = "UTF-8" }
      meta {
        name = "viewport"
        content = "width=device-width, initial-scale=1"
      }

      link(
        rel = "stylesheet",
        href = "/static/css/style.css",
        type = "text/css"
      )
      link(
        rel = "stylesheet",
        href = "/static/css/bulma.min.css",
        type = "text/css"
      ) {
        this.integrity = "sha512-HqxHUkJM0SYcbvxUw5P60SzdOTy/QVwA1JJrvaXJv4q7lmbDZCmZaqz01UPOaQveoxfYRv1tHozWGPMcuTBuvQ=="
        this.attributes["crossorigin"] = "anonymous"
        this.attributes["referrerpolicy"]="no-referrer"
      }
      link(
        rel = "stylesheet",
        href = "/static/css/toastify.min.css",
        type = "text/css"
      ) {
      }

      link(
        rel = "stylesheet",
        href = "/static/css/bulma-tooltip.min.css",
        type = "text/css"
      ) {
        this.integrity = "sha512-eQONsEIU2JzPniggWsgCyYoASC8x8nS0w6+e5LQZbdvWzDUVfUh+vQZFmB2Ykj5uqGDIsY7tSUCdTxImWBShYg=="
        this.attributes["crossorigin"] = "anonymous"
        this.attributes["referrerpolicy"]="no-referrer"
      }
    }

    body{
      section(classes = "section") {
        div(classes = "container") {
          div(classes = "content") {
            insert(content)
          }
        }
      }

      script(src = "/static/js/postinclude.js", type = "text/javascript") {}
      script(src = "https://cdn.jsdelivr.net/npm/toastify-js", type = "text/javascript") {}
    }

  }

}
