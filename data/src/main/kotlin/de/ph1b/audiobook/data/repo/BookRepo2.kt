package de.ph1b.audiobook.data.repo

import de.ph1b.audiobook.data.Book2
import de.ph1b.audiobook.data.BookContent2
import de.ph1b.audiobook.data.repo.internals.dao.BookContent2Dao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepo2
@Inject constructor(
  private val chapterRepo: ChapterRepo,
  private val contentDao: BookContent2Dao,
) {

  private val cacheMutex = Mutex()
  private var cacheFilled = false

  private val cache = MutableStateFlow<List<Book2>>(emptyList())

  private suspend fun fillCache() {
    if (cacheFilled) {
      return
    }
    cacheMutex.withLock {
      val contents = contentDao.all(isActive = true)
        .map { content ->
          val chapters = content.chapters.map { chapterId ->
            chapterRepo.get(chapterId) ?: error("Chapter for $chapterId not found")
          }
          Book2(content, chapters)
        }
      cache.emit(contents)
      cacheFilled = true
    }
  }

  fun flow(): Flow<List<Book2>> {
    return cache.onStart { fillCache() }
  }

  suspend fun setAllInactiveExcept(ids: List<Book2.Id>) {
    fillCache()

    val currentBooks = cache.value

    val (active, inactive) = currentBooks.partition { it.id in ids }
    contentDao.insert(
      inactive.map { book ->
        book.content.copy(isActive = false)
      }
    )
    cache.value = active
  }

  suspend fun updateBook(content: BookContent2) {
    fillCache()
    cache.update {
      it.toMutableList().apply {
        val book = find { book ->
          book.id == content.id
        }
        if (book != null) {
          remove(book)
          add(book.copy(content = content))
        }
      }
    }
    contentDao.insert(content)
  }

  suspend fun updateBook(id: Book2.Id, update: (BookContent2) -> BookContent2) {
    fillCache()
    val books = cache.updateAndGet {
      it.toMutableList().apply {
        val book = find { book -> book.id == id }
          ?: return
        remove(book)
        add(book.copy(content = update(book.content)))
      }
    }
    val updated = books.find { it.id == id }?.content
    if (updated != null) {
      contentDao.insert(updated)
    }
  }


  fun flow(id: Book2.Id): Flow<Book2?> {
    return flow().map { books ->
      books.find { it.id == id }
    }
  }
}
