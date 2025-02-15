package de.ph1b.audiobook.features.bookOverview.list

import de.ph1b.audiobook.data.Book2
import de.ph1b.audiobook.features.bookOverview.list.header.BookOverviewHeaderComponent
import de.ph1b.audiobook.features.bookOverview.list.header.OpenCategoryListener
import de.ph1b.audiobook.misc.recyclerComponent.CompositeListAdapter

typealias BookClickListener = (Book2.Id, BookOverviewClick) -> Unit

class BookOverviewAdapter(
  bookClickListener: BookClickListener,
  openCategoryListener: OpenCategoryListener
) : CompositeListAdapter<BookOverviewItem>(BookOverviewDiff()) {

  init {
    addComponent(GridBookOverviewComponent(bookClickListener))
    addComponent(ListBookOverviewComponent(bookClickListener))
    addComponent(BookOverviewHeaderComponent(openCategoryListener))
  }

  fun itemAtPositionIsHeader(position: Int): Boolean {
    val item = getItem(position)
    return item is BookOverviewHeaderModel
  }
}
