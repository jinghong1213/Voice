package de.ph1b.audiobook.persistence

import de.ph1b.audiobook.Book
import de.ph1b.audiobook.persistence.internals.InternalBookRegister
import e
import rx.Observable
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import v
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Provides access to all books.

 * @author Paul Woitaschek
 */
@Singleton class BookChest
@Inject constructor(private val register: InternalBookRegister) {

    private val active: MutableList<Book> by lazy { register.activeBooks().toMutableList() }
    private val orphaned: MutableList<Book> by lazy { register.orphanedBooks().toMutableList() }

    private val updated = PublishSubject.create<Book>()

    private val all: BehaviorSubject<List<Book>> by lazy { BehaviorSubject.create<List<Book>>(active) }

    fun updateObservable(): Observable<Book> = updated.asObservable()

    fun booksStream(): Observable<List<Book>> = all.asObservable()

    private fun sortBooksAndNotifySubject() {
        active.sort()
        all.onNext(active)
    }

    @Synchronized fun addBook(book: Book) {
        v { "addBook=${book.name}" }

        val bookWithId = register.addBook(book)
        active.add(bookWithId)
        sortBooksAndNotifySubject()
    }

    /**
     * All active books. We
     */
    val activeBooks: List<Book>
        get() = synchronized(this) { ArrayList(active) }

    @Synchronized fun bookById(id: Long) = active.firstOrNull { it.id == id }

    @Synchronized fun getOrphanedBooks(): List<Book> = ArrayList(orphaned)

    @Synchronized fun updateBook(book: Book, chaptersChanged: Boolean = false) {
        v { "updateBook=${book.name} with time ${book.time}" }

        val index = active.indexOfFirst { it.id == book.id }
        if (index != -1) {
            active[index] = book
            register.updateBook(book, chaptersChanged)
            updated.onNext(book)
            sortBooksAndNotifySubject()
        } else e { "update failed as there was no book" }
    }

    @Synchronized fun hideBook(toDelete: List<Book>) {
        v { "hideBooks=${toDelete.size}" }

        val idsToDelete = toDelete.map { it.id }
        active.removeAll { idsToDelete.contains(it.id) }
        orphaned.addAll(toDelete)
        sortBooksAndNotifySubject()
    }


    @Synchronized fun revealBook(book: Book) {
        v { "Called revealBook=$book" }

        orphaned.removeAll { it.id == book.id }
        register.revealBook(book.id)
        active.add(book)
        sortBooksAndNotifySubject()
    }
}
