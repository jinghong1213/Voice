package de.ph1b.audiobook.playback.session

import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat.TransportControls
import android.support.v4.media.session.MediaSessionCompat
import androidx.datastore.core.DataStore
import de.ph1b.audiobook.common.pref.CurrentBook
import de.ph1b.audiobook.data.Book2
import de.ph1b.audiobook.data.Chapter2
import de.ph1b.audiobook.playback.BuildConfig
import de.ph1b.audiobook.playback.androidauto.AndroidAutoConnectedReceiver
import de.ph1b.audiobook.playback.di.PlaybackScope
import de.ph1b.audiobook.playback.player.MediaPlayer
import de.ph1b.audiobook.playback.session.search.BookSearchHandler
import de.ph1b.audiobook.playback.session.search.BookSearchParser
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

/**
 * Media session callback that handles playback controls.
 */
@PlaybackScope
class MediaSessionCallback
@Inject constructor(
  private val bookUriConverter: BookUriConverter,
  @CurrentBook
  private val currentBook: DataStore<Book2.Id?>,
  private val bookSearchHandler: BookSearchHandler,
  private val autoConnection: AndroidAutoConnectedReceiver,
  private val bookSearchParser: BookSearchParser,
  private val player: MediaPlayer,
) : MediaSessionCompat.Callback() {

  override fun onSkipToQueueItem(id: Long) {
    Timber.i("onSkipToQueueItem $id")
    val chapter = player.book
      ?.chapters?.getOrNull(id.toInt()) ?: return
    player.changePosition(0, chapter.id)
    player.play()
  }

  override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
    Timber.i("onPlayFromMediaId $mediaId")
    mediaId ?: return
    val parsed = bookUriConverter.parse(mediaId)
    if (parsed is BookUriConverter.Parsed.Book) {
      runBlocking {
        currentBook.updateData { parsed.id }
      }
      onPlay()
    } else {
      Timber.e("Didn't handle $parsed")
    }
  }

  override fun onPlayFromSearch(query: String?, extras: Bundle?) {
    Timber.i("onPlayFromSearch $query")
    val bookSearch = bookSearchParser.parse(query, extras)
    runBlocking {
      bookSearchHandler.handle(bookSearch)
    }
  }

  override fun onSkipToNext() {
    Timber.i("onSkipToNext")
    if (autoConnection.connected) {
      player.next()
    } else {
      onFastForward()
    }
  }

  override fun onRewind() {
    Timber.i("onRewind")
    player.skip(forward = false)
  }

  override fun onSkipToPrevious() {
    Timber.i("onSkipToPrevious")
    if (autoConnection.connected) {
      player.previous(toNullOfNewTrack = true)
    } else {
      onRewind()
    }
  }

  override fun onFastForward() {
    Timber.i("onFastForward")
    player.skip(forward = true)
  }

  override fun onStop() {
    Timber.i("onStop")
    player.stop()
  }

  override fun onPause() {
    Timber.i("onPause")
    player.pause(rewind = true)
  }

  override fun onPlay() {
    Timber.i("onPlay")
    player.play()
  }

  override fun onSeekTo(pos: Long) {
    player.changePosition(pos)
  }

  override fun onSetPlaybackSpeed(speed: Float) {
    player.setPlaybackSpeed(speed)
  }

  override fun onCustomAction(action: String?, extras: Bundle?) {
    Timber.i("onCustomAction $action")
    when (action) {
      ANDROID_AUTO_ACTION_NEXT -> onSkipToNext()
      ANDROID_AUTO_ACTION_PREVIOUS -> onSkipToPrevious()
      ANDROID_AUTO_ACTION_FAST_FORWARD -> onFastForward()
      ANDROID_AUTO_ACTION_REWIND -> onRewind()
      PLAY_PAUSE_ACTION -> player.playPause()
      SKIP_SILENCE_ACTION -> {
        val skip = extras!!.getBoolean(SKIP_SILENCE_EXTRA)
        player.setSkipSilences(skip)
      }
      SET_POSITION_ACTION -> {
        val id = Chapter2.Id(extras!!.getString(SET_POSITION_EXTRA_CHAPTER)!!)
        val time = extras.getLong(SET_POSITION_EXTRA_TIME)
        player.changePosition(time, id)
      }
      FORCED_PREVIOUS -> {
        player.previous(toNullOfNewTrack = true)
      }
      FORCED_NEXT -> {
        player.next()
      }
      else -> if (BuildConfig.DEBUG) {
        error("Didn't handle $action")
      }
    }
  }
}

private inline fun TransportControls.sendCustomAction(action: String, fillBundle: Bundle.() -> Unit = {}) {
  sendCustomAction(action, Bundle().apply(fillBundle))
}

private const val PLAY_PAUSE_ACTION = "playPause"

fun TransportControls.playPause() = sendCustomAction(PLAY_PAUSE_ACTION)

private const val SKIP_SILENCE_ACTION = "skipSilence"
private const val SKIP_SILENCE_EXTRA = "$SKIP_SILENCE_ACTION#value"

fun TransportControls.skipSilence(skip: Boolean) = sendCustomAction(SKIP_SILENCE_ACTION) {
  putBoolean(SKIP_SILENCE_EXTRA, skip)
}

private const val SET_POSITION_ACTION = "setPosition"
private const val SET_POSITION_EXTRA_TIME = "$SET_POSITION_ACTION#time"
private const val SET_POSITION_EXTRA_CHAPTER = "$SET_POSITION_ACTION#uri"

fun TransportControls.setPosition(time: Long, id: Chapter2.Id) = sendCustomAction(SET_POSITION_ACTION) {
  putString(SET_POSITION_EXTRA_CHAPTER, id.value)
  putLong(SET_POSITION_EXTRA_TIME, time)
}

const val ANDROID_AUTO_ACTION_FAST_FORWARD = "fast_forward"
const val ANDROID_AUTO_ACTION_REWIND = "rewind"
const val ANDROID_AUTO_ACTION_NEXT = "next"
const val ANDROID_AUTO_ACTION_PREVIOUS = "previous"

private const val FORCED_PREVIOUS = "forcedPrevious"
fun TransportControls.forcedPrevious() = sendCustomAction(FORCED_PREVIOUS)

private const val FORCED_NEXT = "forcedNext"
fun TransportControls.forcedNext() = sendCustomAction(FORCED_NEXT)
