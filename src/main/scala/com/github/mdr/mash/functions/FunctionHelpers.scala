package com.github.mdr.mash.functions

import java.nio.file.Path
import java.nio.file.Paths

import com.github.mdr.mash.evaluator.BoundMethod
import com.github.mdr.mash.evaluator.EvaluatedArgument
import com.github.mdr.mash.evaluator.Evaluator
import com.github.mdr.mash.evaluator.EvaluatorException
import com.github.mdr.mash.evaluator.MashNumber
import com.github.mdr.mash.evaluator.MashObject
import com.github.mdr.mash.evaluator.MashString
import com.github.mdr.mash.evaluator.MemberEvaluator
import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.evaluator.TildeExpander
import com.github.mdr.mash.ns.os.PathClass
import com.github.mdr.mash.ns.os.PathSummaryClass
import com.github.mdr.mash.os.linux.LinuxEnvironmentInteractions
import scala.PartialFunction.condOpt

case class Flag(summary: String, shortFlagOpt: Option[String] = None, longFlagOpt: Option[String] = None)

object FunctionHelpers {

  def interpretAsPaths(x: Any): Seq[Path] =
    x match {
      case xs: Seq[_] ⇒ xs.flatMap(interpretAsPaths)
      case _          ⇒ Seq(interpretAsPath(x))
    }

  private def safeInterpretAsPath(x: Any): Option[Path] =
    condOpt(x) {
      case MashString(s, _) ⇒
        Paths.get(s)
      case mo: MashObject if mo.classOpt == Some(PathSummaryClass) ⇒
        Paths.get(MemberEvaluator.lookup(mo, PathSummaryClass.Fields.Path.name).asInstanceOf[MashString].s)
    }

  def interpretAsPath(x: Any): Path =
    safeInterpretAsPath(x).getOrElse(throw new EvaluatorException("Could not interpret as path: " + x))

  def asPathString(p: Path): MashString = MashString(p.toString, Some(PathClass))

  def asPathString(s: String): MashString = MashString(s, Some(PathClass))

  def interpretAsFunction(f: Any): (Any ⇒ Any) = o ⇒ {
    val args = Arguments(Seq(EvaluatedArgument.PositionArg(o)))
    Evaluator.callFunction(f, args)
  }

  def interpretAsSequence(x: Any): Seq[Any] = x match {
    case xs: Seq[Any]          ⇒ xs
    case MashString(s, tagOpt) ⇒ s.toSeq.map(c ⇒ MashString(c.toString, tagOpt))
    case _                     ⇒ throw new EvaluatorException("Could not interpret as sequence: " + x)
  }

}