package com.github.mdr.mash.parser

import com.github.mdr.mash.parser.AbstractSyntax._
import scala.PartialFunction._
import java.text.DecimalFormat
import com.github.mdr.mash.utils.NumberUtils
import com.github.mdr.mash.evaluator.MashNumber
import com.github.mdr.mash.evaluator.MashString

/**
 * Routines to print out syntax trees for debugging purposes.
 */
object PrettyPrinter {

  /**
   * Pretty-print the expression
   */
  def pretty(expr: Expr): String = expr match {
    case IfExpr(cond, body, elseOpt, sourceInfoOpt) ⇒ "if " + pretty(cond) + " then " + pretty(body) + elseOpt.map(elseBody ⇒ " " + pretty(elseBody)).getOrElse("")
    case StringLiteral(s, _, _, _)                  ⇒ '"' + s + '"' // TOOD: more faithful representation
    case Literal(n: MashNumber, _)                  ⇒ NumberUtils.prettyString(n.n)
    case Literal(x, _)                              ⇒ x + ""
    case InterpolatedString(start, parts, end, _) ⇒
      val chunks = parts.map {
        case StringPart(s)  ⇒ s
        case ExprPart(expr) ⇒ pretty(expr)
      }
      "\"" + start + chunks.mkString + end + "\""
    case Identifier(n, _)                        ⇒ n.toString
    case MishFunction(w, _)                      ⇒ "!" + w.toString
    case Hole(_)                                 ⇒ "_"
    case MinusExpr(subExpr, _)                   ⇒ "-" + parens(pretty(subExpr))
    case MemberExpr(left, member, isNullSafe, _) ⇒ parens(pretty(left), simpleOmitParens(left)) + (if (isNullSafe) "?." else ".") + member
    case LookupExpr(expr, index, _)              ⇒ parens(pretty(expr), simpleOmitParens(expr)) + "[" + pretty(index) + "]"
    case PipeExpr(left, right, _)                ⇒ "(" + pretty(left) + ") | (" + pretty(right) + ")"
    case ParenExpr(body, _)                      ⇒ "(" + pretty(body) + ")"
    case LambdaExpr(v, body, _)                  ⇒ v + " => " + pretty(body)
    case BinOpExpr(left, op, right, _)           ⇒ parens(pretty(left), simpleOmitParens(left)) + " " + pretty(op) + " " + parens(pretty(right), simpleOmitParens(right))
    case AssignmentExpr(left, right, _)          ⇒ parens(pretty(left), simpleOmitParens(left)) + " = " + parens(pretty(right), simpleOmitParens(right))
    case ListExpr(items, _)                      ⇒ items.mkString("[", ", ", "]")
    case ObjectExpr(entries, _)                  ⇒ entries.map { case (label, value) ⇒ s"$label: ${pretty(value)}" }.mkString("{ ", ", ", " }")
    case InvocationExpr(function, args, _) ⇒
      parens(pretty(function), simpleOmitParens(function)) + " " + args.map(arg ⇒
        arg match {
          case Argument.PositionArg(e) ⇒
            parens(pretty(e), simpleOmitParens(e))
          case Argument.ShortFlag(flags) ⇒
            "-" + flags
          case Argument.LongFlag(flag, valueOpt) ⇒
            "--" + flag + valueOpt.map(value ⇒ "=" + parens(pretty(value), simpleOmitParens(value))).getOrElse("")
        }).mkString(" ")
    case MishExpr(command, args, _) ⇒ pretty(command) + " " + args.map(pretty)
    case MishInterpolation(part, _) ⇒
      part match {
        case StringPart(s)  ⇒ s
        case ExprPart(expr) ⇒ pretty(expr)
      }
    case FunctionDeclaration(name, params, body, _) ⇒ "def " + name + params.mkString(" ") + " = " + pretty(body)
    case HelpExpr(subExpr, _)                       ⇒ pretty(subExpr) + "?"
  }

  private def pretty(op: BinaryOperator): String = op match {
    case BinaryOperator.And               ⇒ "and"
    case BinaryOperator.Or                ⇒ "or"
    case BinaryOperator.Equals            ⇒ "=="
    case BinaryOperator.NotEquals         ⇒ "!="
    case BinaryOperator.GreaterThan       ⇒ ">"
    case BinaryOperator.GreaterThanEquals ⇒ ">="
    case BinaryOperator.LessThan          ⇒ "<"
    case BinaryOperator.LessThanEquals    ⇒ "<="
    case BinaryOperator.Plus              ⇒ "+"
    case BinaryOperator.Minus             ⇒ "-"
    case BinaryOperator.Multiply          ⇒ "*"
    case BinaryOperator.Divide            ⇒ "/"
    case BinaryOperator.Sequence          ⇒ ";"

  }

  private def parens(s: String, omitParens: Boolean = false): String = if (omitParens) s else "(" + s + ")"

  private def simpleOmitParens(expr: Expr): Boolean = cond(expr) {
    case Identifier(_, _) | Literal(_, _) | Hole(_) | MemberExpr(_, _, _, _) | ParenExpr(_, _) ⇒ true
  }

}