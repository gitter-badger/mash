package com.github.mdr.mash

import com.github.mdr.mash.completions.Completion
import com.github.mdr.mash.utils.Region
import com.github.mdr.mash.utils.StringUtils

sealed trait CompletionState {

  val replacementLocation: Region

  val completions: Seq[Completion]

  def allQuoted = completions.forall(_.isQuoted)

}

/**
 * Incremental completion state is where the user has requested completions once, but hasn't left the primary
 * line editing mode. Completions are filtered incrementally according to what the user types.
 */
case class IncrementalCompletionState(
    starterPrefix: String,
    completions: Seq[Completion],
    accepted: String,
    replacementLocation: Region,
    immediatelyAfterCompletion: Boolean) extends CompletionState {

  def getCommonPrefix: String = completions.map(_.text).fold(accepted)(StringUtils.commonPrefix)

  def getReplacement = if (allQuoted) "\"" + getCommonPrefix + "\"" else getCommonPrefix

}

/**
 * In the browser completion state, the user has left the primary line editing mode, and can navigate the list
 * of options using tab / the arrow keys.
 */
case class BrowserCompletionState(
    completions: Seq[Completion],
    replacementLocation: Region,
    activeCompletion: Int) extends CompletionState {

}
