package com.github.mdr.mash.compiler

import com.github.mdr.mash.parser.AbstractSyntax._
import com.github.mdr.mash.evaluator.MashString
import com.github.mdr.mash.ns.os.PathClass
import com.github.mdr.mash.parser.BinaryOperator
import com.github.mdr.mash.lexer.Token
import com.github.mdr.mash.parser.QuotationType

object BareStringify {

  def bareStringify(expr: Expr, bindings: Set[String]): Expr = {
    val context = new BareStringificationContext
    context.bareStringify(expr, bindings)
  }

  def getBareTokens(expr: Expr, bindings: Set[String]): Set[Token] = {
    val context = new BareStringificationContext
    context.bareStringify(expr, bindings)
    context.bareWords
  }

}

class BareStringificationContext {

  var bareWords: Set[Token] = Set()

  def bareStringify(expr: Expr, bindings: Set[String]): Expr = expr match {
    case Identifier(name, sourceInfoOpt) ⇒
      if (bindings contains name)
        expr
      else {
        for {
          sourceInfo ← sourceInfoOpt
          token ← sourceInfo.expr.tokens
        } bareWords += token
        StringLiteral(name, QuotationType.Double, tildePrefix = false, sourceInfoOpt = sourceInfoOpt)
      }
    case Hole(_) | Literal(_, _) | StringLiteral(_, _, _, _) | MishFunction(_, _) ⇒
      expr
    case InterpolatedString(start, parts, end, sourceInfoOpt) ⇒
      val newParts = parts.map {
        case StringPart(s)  ⇒ StringPart(s)
        case ExprPart(expr) ⇒ ExprPart(bareStringify(expr, bindings))
      }
      InterpolatedString(start, newParts, end, sourceInfoOpt)
    case ParenExpr(expr, sourceInfoOpt) ⇒
      ParenExpr(bareStringify(expr, bindings), sourceInfoOpt)
    case LambdaExpr(parameter, body, sourceInfoOpt) ⇒
      LambdaExpr(parameter, bareStringify(body, bindings + parameter), sourceInfoOpt)
    case PipeExpr(left, right, sourceInfoOpt) ⇒
      PipeExpr(bareStringify(left, bindings), bareStringify(right, bindings), sourceInfoOpt)
    case MemberExpr(expr, name, isNullSafe, sourceInfoOpt) ⇒
      MemberExpr(bareStringify(expr, bindings), name, isNullSafe, sourceInfoOpt)
    case LookupExpr(expr, index, sourceInfoOpt) ⇒
      LookupExpr(bareStringify(expr, bindings), bareStringify(index, bindings), sourceInfoOpt)
    case InvocationExpr(function, arguments, sourceInfoOpt) ⇒
      val newArguments = arguments.map {
        case Argument.PositionArg(expr)        ⇒ Argument.PositionArg(bareStringify(expr, bindings))
        case arg @ Argument.ShortFlag(_)       ⇒ arg
        case Argument.LongFlag(flag, valueOpt) ⇒ Argument.LongFlag(flag, valueOpt.map(bareStringify(_, bindings)))
      }
      InvocationExpr(bareStringify(function, bindings), newArguments, sourceInfoOpt)
    case BinOpExpr(left, op @ BinaryOperator.Sequence, right, sourceInfoOpt) ⇒
      val extraGlobals = left.findAll { case AssignmentExpr(left @ Identifier(name, _), right, sourceInfoOpt) ⇒ name }
      val newBindings = bindings ++ extraGlobals
      BinOpExpr(bareStringify(left, bindings), op, bareStringify(right, newBindings), sourceInfoOpt)
    case BinOpExpr(left, op, right, sourceInfoOpt) ⇒
      BinOpExpr(bareStringify(left, bindings), op, bareStringify(right, bindings), sourceInfoOpt)
    case IfExpr(cond, body, elseOpt, sourceInfoOpt) ⇒
      IfExpr(bareStringify(cond, bindings), bareStringify(body, bindings), elseOpt.map(bareStringify(_, bindings)), sourceInfoOpt)
    case ListExpr(items, sourceInfoOpt) ⇒
      ListExpr(items.map(bareStringify(_, bindings)), sourceInfoOpt)
    case ObjectExpr(entries, sourceInfoOpt) ⇒
      val newEntries = for ((label, value) ← entries) yield (label, bareStringify(value, bindings))
      ObjectExpr(newEntries, sourceInfoOpt)
    case MinusExpr(expr, sourceInfoOpt) ⇒
      MinusExpr(bareStringify(expr, bindings), sourceInfoOpt)
    case AssignmentExpr(left @ Identifier(name, _), right, sourceInfoOpt) ⇒
      AssignmentExpr(left, bareStringify(right, bindings + name), sourceInfoOpt)
    case AssignmentExpr(left, right, sourceInfoOpt) ⇒
      AssignmentExpr(bareStringify(left, bindings), bareStringify(right, bindings), sourceInfoOpt)
    case MishExpr(command, args, sourceInfoOpt) ⇒
      MishExpr(bareStringify(command, bindings), args.map(bareStringify(_, bindings)), sourceInfoOpt)
    case MishInterpolation(part, sourceInfoOpt) ⇒
      val newPart = part match {
        case StringPart(s)  ⇒ StringPart(s)
        case ExprPart(expr) ⇒ ExprPart(bareStringify(expr, bindings))
      }
      MishInterpolation(newPart, sourceInfoOpt)
    case FunctionDeclaration(name, params, body, sourceInfoOpt) ⇒
      FunctionDeclaration(name, params, bareStringify(body, bindings ++ params.collect { case SimpleParam(n) ⇒ n }), sourceInfoOpt)
    case HelpExpr(expr, sourceInfoOpt) ⇒
      HelpExpr(bareStringify(expr, bindings), sourceInfoOpt)
  }

}