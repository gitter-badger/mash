package com.github.mdr.mash.compiler

import com.github.mdr.mash.parser.AbstractSyntax._
import scala.collection.immutable.ListMap

object DesugarHoles {

  val VariableName = "$hole"

  def desugarHoles(expr: Expr): Expr = addLambdaIfNeeded(expr)

  private def addLambdaIfNeeded(expr: Expr): Expr = {
    val Result(newExpr, hasHole) = desugarHoles_(expr)
    if (hasHole)
      LambdaExpr(VariableName, newExpr, None)
    else
      newExpr
  }

  private def desugarHoles_(argument: Argument): Result[Argument] = argument match {
    case Argument.PositionArg(expr)          ⇒ desugarHoles_(expr).map(Argument.PositionArg(_))
    case Argument.ShortFlag(_)               ⇒ Result(argument)
    case Argument.LongFlag(flag, None)       ⇒ Result(Argument.LongFlag(flag, None))
    case Argument.LongFlag(flag, Some(expr)) ⇒ desugarHoles_(expr).map(e ⇒ Argument.LongFlag(flag, Some(e)))
  }

  private def desugarHoles_(expr: Expr): Result[Expr] = expr match {
    case Hole(sourceInfoOpt) ⇒
      Result(Identifier(VariableName, sourceInfoOpt), hasHole = true)
    case InterpolatedString(start, parts, end, sourceInfoOpt) ⇒
      val newPartsResult: Seq[Result[InterpolationPart]] = parts.map {
        case StringPart(s)  ⇒ Result(StringPart(s))
        case ExprPart(expr) ⇒ desugarHoles_(expr).map(ExprPart)
      }
      for {
        newParts ← sequence(newPartsResult)
      } yield InterpolatedString(start, newParts, end, sourceInfoOpt)
    case LambdaExpr(parameter, body, sourceInfoOpt) ⇒
      Result(LambdaExpr(parameter, addLambdaIfNeeded(body), sourceInfoOpt))
    case ParenExpr(expr, sourceInfoOpt) ⇒
      Result(ParenExpr(addLambdaIfNeeded(expr), sourceInfoOpt))
    case PipeExpr(left, right, sourceInfoOpt) ⇒
      for (left ← desugarHoles_(left))
        yield PipeExpr(left, addLambdaIfNeeded(right), sourceInfoOpt)
    case Literal(_, _) | StringLiteral(_, _, _, _) | Identifier(_, _) | MishFunction(_, _) ⇒ Result(expr)
    case MemberExpr(target, name, isNullSafe, sourceInfoOpt) ⇒
      for (newTarget ← desugarHoles_(target))
        yield MemberExpr(newTarget, name, isNullSafe, sourceInfoOpt)
    case MinusExpr(expr, sourceInfoOpt) ⇒
      for (newExpr ← desugarHoles_(expr))
        yield MinusExpr(newExpr, sourceInfoOpt)
    case LookupExpr(expr, index, sourceInfoOpt) ⇒
      for {
        newExpr ← desugarHoles_(expr)
        newIndex ← desugarHoles_(index)
      } yield LookupExpr(newExpr, newIndex, sourceInfoOpt)
    case InvocationExpr(function, args, sourceInfoOpt) ⇒
      for {
        newFunction ← desugarHoles_(function)
        newArgs ← sequence(args.map(desugarHoles_))
      } yield InvocationExpr(newFunction, newArgs, sourceInfoOpt)
    case ListExpr(items, sourceInfoOpt) ⇒
      for (newItems ← sequence(items.map(desugarHoles_)))
        yield ListExpr(newItems, sourceInfoOpt)
    case ObjectExpr(entries, sourceInfoOpt) ⇒
      val desugaredEntries = for ((k, v) ← entries) yield k -> desugarHoles_(v)
      for (newEntries ← sequence(desugaredEntries))
        yield ObjectExpr(newEntries, sourceInfoOpt)
    case IfExpr(cond, body, elseOpt, sourceInfoOpt) ⇒
      for {
        newCond ← desugarHoles_(cond)
        newBody ← desugarHoles_(body)
        newElseOpt ← sequence(elseOpt.map(desugarHoles_))
      } yield IfExpr(newCond, newBody, newElseOpt, sourceInfoOpt)
    case BinOpExpr(left, op, right, sourceInfoOpt) ⇒
      for {
        newLeft ← desugarHoles_(left)
        newRight ← desugarHoles_(right)
      } yield BinOpExpr(newLeft, op, newRight, sourceInfoOpt)
    case AssignmentExpr(left, right, sourceInfoOpt) ⇒
      for {
        newLeft ← desugarHoles_(left)
        newRight ← desugarHoles_(right)
      } yield AssignmentExpr(newLeft, newRight, sourceInfoOpt)
    case MishExpr(command, args, sourceInfoOpt) ⇒
      for {
        newCommand ← desugarHoles_(command)
        newArgs ← sequence(args.map(desugarHoles_))
      } yield MishExpr(newCommand, newArgs, sourceInfoOpt)
    case MishInterpolation(part, sourceInfoOpt) ⇒
      val newPartResult = part match {
        case StringPart(s)  ⇒ Result(StringPart(s))
        case ExprPart(expr) ⇒ desugarHoles_(expr).map(ExprPart)
      }
      for (newPart ← newPartResult)
        yield MishInterpolation(newPart, sourceInfoOpt)
    case FunctionDeclaration(name, params, body, sourceInfoOpt) ⇒
      for (newBody ← desugarHoles_(body))
        yield FunctionDeclaration(name, params, newBody, sourceInfoOpt)
    case HelpExpr(expr, sourceInfoOpt) ⇒
      for (newExpr ← desugarHoles_(expr))
        yield HelpExpr(newExpr, sourceInfoOpt)
  }

  /**
   * A Result monad so we can ferry the information about the presence or absence of a hole with less plumbing.
   */
  private case class Result[+T](value: T, hasHole: Boolean = false) {

    def map[U](f: T ⇒ U) = Result(f(value), hasHole)

    def flatMap[U](f: T ⇒ Result[U]) = {
      val Result(newValue, hasHole) = f(value)
      Result(newValue, hasHole = hasHole || this.hasHole)
    }

  }

  private def sequence[T](resultOpt: Option[Result[T]]): Result[Option[T]] = resultOpt match {
    case Some(result) ⇒ result.map(Some(_))
    case None         ⇒ Result(None)
  }

  private def sequence[T](results: Seq[Result[T]]): Result[Seq[T]] = results match {
    case Seq() ⇒
      Result(Seq())
    case xs ⇒
      for {
        head ← xs.head
        tail ← sequence(xs.tail)
      } yield head +: tail
  }
  private def sequence[T](resultMap: ListMap[String, Result[T]]): Result[ListMap[String, T]] =
    Result(
      for ((k, result) ← resultMap)
        yield k -> result.value, hasHole = resultMap.values.exists(_.hasHole))

  private def sequence[T](resultMap: Map[String, Result[T]]): Result[Map[String, T]] =
    Result(
      for ((k, result) ← resultMap)
        yield k -> result.value, hasHole = resultMap.values.exists(_.hasHole))

}