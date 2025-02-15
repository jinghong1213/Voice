package de.ph1b.audiobook.data

import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

data class Book2(
  val content: BookContent2,
  val chapters: List<Chapter2>,
) {

  val id: Id = content.id

  val transitionName: String = id.transitionName

  init {
    if (BuildConfig.DEBUG) {
      check(chapters.size == content.chapters.size) {
        "Different chapter count in $this"
      }
      check(chapters.map { it.id } == content.chapters) {
        "Different chapter order in $this"
      }
    }
  }

  val currentChapter: Chapter2 = chapters[content.currentChapterIndex]
  val previousChapter: Chapter2? = chapters.getOrNull(content.currentChapterIndex - 1)
  val nextChapter: Chapter2? = chapters.getOrNull(content.currentChapterIndex + 1)

  val nextMark: ChapterMark? = currentChapter.nextMark(content.positionInChapter)
  val currentMark: ChapterMark = currentChapter.markForPosition(content.positionInChapter)

  val position: Long = chapters.takeWhile { it.id != content.currentChapter }
    .sumOf { it.duration } + content.positionInChapter
  val duration: Long = chapters.sumOf { it.duration }

  inline fun update(update: (BookContent2) -> BookContent2): Book2 {
    return copy(content = update(content))
  }

  @Serializable(with = BookIdSerializer::class)
  @Parcelize
  data class Id(val value: String) : Parcelable {

    val transitionName: String get() = value

    constructor(uri: Uri) : this(uri.toString())
  }
}

object BookIdSerializer : KSerializer<Book2.Id> {

  override val descriptor: SerialDescriptor
    get() = PrimitiveSerialDescriptor("bookId", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): Book2.Id = Book2.Id(decoder.decodeString())

  override fun serialize(encoder: Encoder, value: Book2.Id) {
    encoder.encodeString(value.value)
  }
}


private fun Chapter2.nextMark(positionInChapterMs: Long): ChapterMark? {
  val markForPosition = markForPosition(positionInChapterMs)
  val marks = chapterMarks
  val index = marks.indexOf(markForPosition)
  return if (index != -1) {
    marks.getOrNull(index + 1)
  } else {
    null
  }
}

fun Bundle.putBookId(key: String, id: Book2.Id) {
  putString(key, id.value)
}

fun Bundle.getBookId(key: String): Book2.Id? {
  return getString(key)?.let(Book2::Id)
}
