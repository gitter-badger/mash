package com.github.mdr.mash

import org.scalatest.Matchers
import org.scalatest.FlatSpec
import com.github.mdr.mash.parser.AbstractSyntax._
import com.github.mdr.mash.parser.MashParser
import com.github.mdr.mash.parser.Abstractifier

class LineBufferTest extends FlatSpec with Matchers {

  "LineBuffer" should "let you delete forward a word" in {
    lineBuffer("▶foo").deleteForwardWord should equal(lineBuffer("▶"))
    lineBuffer("fo▶o").deleteForwardWord should equal(lineBuffer("fo▶"))
    lineBuffer("foo▶").deleteForwardWord should equal(lineBuffer("foo▶"))
    lineBuffer("foo ▶bar baz").deleteForwardWord should equal(lineBuffer("foo ▶ baz"))
    lineBuffer("▶").deleteForwardWord should equal(lineBuffer("▶"))
  }

  it should "let you delete backwards a word" in {
    lineBuffer("▶foo").deleteBackwardWord should equal(lineBuffer("▶foo"))
    lineBuffer("fo▶o").deleteBackwardWord should equal(lineBuffer("▶o"))
    lineBuffer("foo▶").deleteBackwardWord should equal(lineBuffer("▶"))
    lineBuffer("foo bar▶ baz").deleteBackwardWord should equal(lineBuffer("foo ▶ baz"))
    lineBuffer("▶").deleteBackwardWord should equal(lineBuffer("▶"))
  }

  it should "let you move backwards a word" in {
    lineBuffer("foo bar▶ baz").backwardWord should equal(lineBuffer("foo ▶bar baz"))
    lineBuffer("foo bar ▶baz").backwardWord should equal(lineBuffer("foo ▶bar baz"))
  }

  it should "let you move forwards a word" in {
    lineBuffer("foo bar ▶baz").forwardWord should equal(lineBuffer("foo bar baz▶"))
    lineBuffer("foo bar▶ baz").forwardWord should equal(lineBuffer("foo bar baz▶"))
  }

  private def lineBuffer(s: String) = LineBuffer(s.filterNot('▶' == _), cursorPos = s.indexOf('▶'))

}