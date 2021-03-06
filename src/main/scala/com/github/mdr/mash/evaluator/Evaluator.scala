package com.github.mdr.mash.evaluator

import java.time.Instant
import java.time.LocalDate
import scala.PartialFunction.condOpt
import scala.collection.immutable.ListMap
import com.github.mdr.mash.functions.AnonymousFunction
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.functions.Parameter
import com.github.mdr.mash.functions.UserDefinedFunction
import com.github.mdr.mash.ns.core.help.FunctionHelpClass
import com.github.mdr.mash.ns.core.help.ParameterHelpClass
import com.github.mdr.mash.ns.os.PathClass
import com.github.mdr.mash.ns.os.RunFunction
import com.github.mdr.mash.parser.AbstractSyntax._
import com.github.mdr.mash.parser.BinaryOperator
import com.github.mdr.mash.parser.ConcreteSyntax
import com.github.mdr.mash.utils.PointedRegion
import com.github.mdr.mash.utils.Utils
import com.github.mdr.mash.ns.core.FunctionClass
import com.github.mdr.mash.ns.core.help.HelpFunction
import com.github.mdr.mash.subprocesses.ProcessRunner
import com.github.mdr.mash.os.linux.LinuxEnvironmentInteractions
import com.github.mdr.mash.parser.QuotationType

object Evaluator {

  private val environmentInteractions = LinuxEnvironmentInteractions

  def evaluate(expr: Expr, env: Environment): Any = {
    try {
      val v = simpleEvaluate(expr, env)
      val result = immediatelyResolveNullaryFunctions(v)
      checkIsValidRuntimeValue(result)
      result
    } catch {
      case e: EvaluatorException ⇒
        throw e
      case t: Exception ⇒
        throw EvaluatorException("Unexpected error in evaluation: " + t.toString,
          locationOpt = expr.sourceInfoOpt.map(_.location),
          cause = t)
    }
  }

  def checkIsValidRuntimeValue(x: Any) = x match {
    case null | () | true | false | MashString(_, _) | MashNumber(_, _) | MashObject(_, _) ⇒
    case _: MashFunction | _: BoundMethod ⇒
    case _: MashClass ⇒
    case _: Instant | _: LocalDate ⇒
    case xs: Seq[_] ⇒
    case _ ⇒ throw EvaluatorException("Unexpected runtime type: " + x.getClass)
  }

  /**
   * If the given value is a function or bound method that allows nullary invocation, invoke it immediately and
   * return the result.
   */
  def immediatelyResolveNullaryFunctions(v: Any): Any =
    v match {
      case f: MashFunction if f.allowsNullary ⇒ f(Arguments(Seq()))
      case BoundMethod(target, method, _) if method.allowsNullary ⇒ method(target, Arguments(Seq()))
      case _ ⇒ v
    }

  /**
   * Evaluate the given expression. If the result is a function that allows a nullary call, it is not called.
   */
  private def simpleEvaluate(expr: Expr, env: Environment): Any =
    expr match {
      case Hole(_) | PipeExpr(_, _, _) ⇒ // Should have been removed from the AST by now
        throw EvaluatorException("Unexpected AST node: " + expr, expr.sourceInfoOpt.map(_.location))
      case interpolatedString: InterpolatedString ⇒
        evaluateInterpolatedString(interpolatedString, env)
      case ParenExpr(body, _) ⇒
        evaluate(body, env)
      case Literal(v, _) ⇒
        v
      case StringLiteral(s, quotationType, tildePrefix, _) ⇒
        val tagOpt = condOpt(quotationType) { case QuotationType.Double ⇒ PathClass }
        val detilded = if (tildePrefix) environmentInteractions.home + s else s
        MashString(detilded, tagOpt)
      case Identifier(name, _) ⇒
        env.get(name).orElse(env.globalVariables.get(name)).getOrElse {
          val locationOpt = expr.sourceInfoOpt.map(_.location)
          throw EvaluatorException(s"No binding for '$name'", locationOpt)
        }
      case MinusExpr(subExpr, _) ⇒
        evaluate(subExpr, env) match {
          case n: MashNumber ⇒ n.negate
          case _             ⇒ throw new EvaluatorException("Could not negate a non-number", expr.sourceInfoOpt.map(_.location))
        }
      case memberExpr: MemberExpr ⇒
        evaluateMemberExpr(memberExpr, env, immediatelyResolveNullaryWhenVectorising = true).result
      case lookupExpr: LookupExpr ⇒
        evaluateLookupExpr(lookupExpr, env)
      case invocationExpr: InvocationExpr ⇒
        evaluateInvocationExpr(invocationExpr, env)
      case LambdaExpr(parameter, body, _) ⇒
        makeAnonymousFunction(parameter, body, env)
      case binOp @ BinOpExpr(_, _, _, _) ⇒
        evaluateBinOp(binOp, env)
      case assExpr: AssignmentExpr ⇒ evaluateAssignment(assExpr, env)
      case ifExpr: IfExpr ⇒
        evaluateIfExpr(ifExpr, env)
      case ListExpr(items, _) ⇒
        items.map(evaluate(_, env))
      case ObjectExpr(entries, _) ⇒
        val fields = for ((label, value) ← entries) yield label -> evaluate(value, env)
        MashObject(fields, classOpt = None)
      case mishExpr: MishExpr      ⇒ evaluateMishExpr(mishExpr, env)
      case expr: MishInterpolation ⇒ evaluateMishInterpolation(expr, env)
      case FunctionDeclaration(name, params, body, sourceInfoOpt) ⇒
        val fn = UserDefinedFunction(name, params.map(_.asInstanceOf[SimpleParam].name), body, env)
        env.globalVariables += name -> fn
        ()
      case MishFunction(command, _) ⇒ SystemCommandFunction(command)
      case HelpExpr(expr, _)        ⇒ evaluateHelpExpr(expr, env)

    }

  private def evaluateAssignment(expr: AssignmentExpr, env: Environment): Any = {
    val AssignmentExpr(left, right, _) = expr
    left match {
      case Identifier(name, _) ⇒
        env.globalVariables += name -> evaluate(right, env)
        ()
      case MemberExpr(target, member, /* isNullSafe */ false, _) ⇒
        val targetValue = evaluate(target, env)
        val assignmentValue = evaluate(right, env)
        targetValue match {
          case MashObject(fields, _) ⇒
            fields += member -> assignmentValue
            ()
          case _ ⇒
            throw new EvaluatorException("Cannot assign to fields of an object of this type", expr.sourceInfoOpt.map(_.location))
        }
      case _ ⇒
        throw new EvaluatorException("Expression is not assignable", expr.sourceInfoOpt.map(_.location))
    }
  }

  private def lookupField(target: Any, name: String): Option[(Field, MashClass)] = {
    condOpt(target) {
      case MashObject(_, Some(klass)) ⇒ klass.fields.find(_.name == name).map(field ⇒ (field, klass))
    }.flatten
  }

  private def getHelpForMember(target: Any, name: String): Option[MashObject] = {
    val fieldHelpOpt = lookupField(target, name).map { case (field, klass) ⇒ HelpFunction.getHelp(field, klass) }
    lazy val memberHelpOpt = MemberEvaluator.maybeLookup(target, name).collect { case bm: BoundMethod ⇒ HelpFunction.getHelp(bm) }
    fieldHelpOpt orElse memberHelpOpt
  }

  private def evaluateHelpExpr(expr: Expr, env: Environment) =
    expr match {
      case memberExpr @ MemberExpr(targetExpr, name, _, _) ⇒
        val target = evaluate(targetExpr, env)
        val scalarHelpOpt = getHelpForMember(target, name)
        lazy val vectorHelpOpt = condOpt(target) {
          case Seq(x, _*) ⇒ getHelpForMember(x, name)
        }.flatten
        lazy val directHelp = {
          val result = evaluateMemberExpr_(memberExpr, target, env, immediatelyResolveNullaryWhenVectorising = true).result
          HelpFunction.getHelp(result)
        }
        scalarHelpOpt orElse vectorHelpOpt getOrElse directHelp
      case _ ⇒
        val x = simpleEvaluate(expr, env)
        HelpFunction.getHelp(x)
    }

  private def evaluateMishInterpolation(expr: MishInterpolation, env: Environment) =
    expr.part match {
      case StringPart(s)  ⇒ MashString(s, Some(PathClass))
      case ExprPart(expr) ⇒ evaluate(expr, env)
    }

  private def evaluateMishExpr(expr: MishExpr, env: Environment) {
    val MishExpr(command, args, _) = expr
    val evaluatedCommand = evaluate(command, env)
    val evaluatedArgs = args.map(evaluate(_, env))
    val flattenedArgs: Seq[Any] = evaluatedArgs.flatMap {
      case xs: Seq[_] ⇒ xs
      case x          ⇒ Seq(x)
    }
    val allArgs = evaluatedCommand +: flattenedArgs
    ProcessRunner.runProcess(allArgs, expandTilde = true)
  }

  private def evaluateInterpolatedString(interpolatedString: InterpolatedString, env: Environment): MashString = {
    val InterpolatedString(start, parts, end, _) = interpolatedString
    val chunks =
      MashString(start, Some(PathClass)) +:
        parts.map {
          case StringPart(s) ⇒ MashString(s, Some(PathClass))
          case ExprPart(expr) ⇒ evaluate(expr, env) match {
            case ms: MashString ⇒ ms
            case x              ⇒ MashString(ToStringifier.stringify(x))
          }
        } :+ MashString(end, Some(PathClass))
    chunks.reduce(_ + _)
  }

  private def makeAnonymousFunction(parameter: String, body: Expr, env: Environment): AnonymousFunction =
    AnonymousFunction(parameter, body, env)

  private case class MemberExprEvalResult(result: Any, wasVectorised: Boolean)

  private def evaluateMemberExpr(memberExpr: MemberExpr, env: Environment, immediatelyResolveNullaryWhenVectorising: Boolean): MemberExprEvalResult = {
    val MemberExpr(expr, name, isNullSafe, sourceInfoOpt) = memberExpr
    val target = evaluate(expr, env)
    evaluateMemberExpr_(memberExpr, target, env, immediatelyResolveNullaryWhenVectorising)
  }

  private def evaluateMemberExpr_(memberExpr: MemberExpr, target: Any, env: Environment, immediatelyResolveNullaryWhenVectorising: Boolean): MemberExprEvalResult = {
    val MemberExpr(expr, name, isNullSafe, sourceInfoOpt) = memberExpr
    val locationOpt = sourceInfoOpt.flatMap(info ⇒ condOpt(info.expr) {
      case ConcreteSyntax.MemberExpr(_, _, name) ⇒ PointedRegion(name.offset, name.region)
    })
    if (target == null && isNullSafe)
      MemberExprEvalResult(null, wasVectorised = false)
    else {
      lazy val scalarLookup = MemberEvaluator.maybeLookup(target, name).map(x ⇒ MemberExprEvalResult(x, wasVectorised = false))
      lazy val vectorisedLookup = vectorisedMemberLookup(target, name, isNullSafe, immediatelyResolveNullaryWhenVectorising).map(x ⇒ MemberExprEvalResult(x, wasVectorised = true))
      scalarLookup orElse vectorisedLookup getOrElse (throw new EvaluatorException(s"Cannot find member '$name'", locationOpt))
    }
  }

  private def vectorisedMemberLookup(target: Any, name: String, isNullSafe: Boolean, immediatelyResolveNullaryWhenVectorising: Boolean): Option[Seq[_]] =
    target match {
      case xs: Seq[Any] if xs.nonEmpty ⇒
        val options = xs.map {
          case null if isNullSafe ⇒ Some(null)
          case x ⇒
            val lookupOpt = MemberEvaluator.maybeLookup(x, name)
            if (immediatelyResolveNullaryWhenVectorising)
              lookupOpt.map(Evaluator.immediatelyResolveNullaryFunctions)
            else
              lookupOpt
        }
        Utils.sequence(options)
      case _ ⇒
        None
    }

  private def evaluateArgument(arg: Argument, env: Environment): EvaluatedArgument = arg match {
    case Argument.PositionArg(expr)        ⇒ EvaluatedArgument.PositionArg(evaluate(expr, env))
    case Argument.ShortFlag(flags)         ⇒ EvaluatedArgument.ShortFlag(flags)
    case Argument.LongFlag(flag, valueOpt) ⇒ EvaluatedArgument.LongFlag(flag, valueOpt.map(v ⇒ evaluate(v, env)))
  }

  private def evaluateInvocationExpr(invocationExpr: InvocationExpr, env: Environment) = {
    val InvocationExpr(functionExpr, arguments, _) = invocationExpr
    val evaluatedArguments = Arguments(arguments.map(evaluateArgument(_, env)))
    functionExpr match {
      case memberExpr: MemberExpr ⇒
        val MemberExprEvalResult(result, wasVectorised) = evaluateMemberExpr(memberExpr, env, immediatelyResolveNullaryWhenVectorising = false)
        if (wasVectorised) {
          val functions = result.asInstanceOf[Seq[_]]
          functions.map(function ⇒ callFunction(function, evaluatedArguments, functionExpr, invocationExpr))
        } else
          callFunction(result, evaluatedArguments, functionExpr, invocationExpr)
      case _ ⇒
        val function = simpleEvaluate(functionExpr, env)
        callFunction(function, evaluatedArguments, functionExpr, invocationExpr)
    }
  }

  private def evaluateIfExpr(ifExpr: IfExpr, env: Environment) = {
    val IfExpr(cond, body, elseOpt, _) = ifExpr
    val result = evaluate(cond, env)
    if (Truthiness.isTruthy(result))
      evaluate(body, env)
    else elseOpt match {
      case None           ⇒ ()
      case Some(elseBody) ⇒ evaluate(elseBody, env)
    }
  }

  private def evaluateLookupExpr(lookupExpr: LookupExpr, env: Environment) = {
    val LookupExpr(targetExpr, indexExpr, sourceInfoOpt) = lookupExpr
    val target = evaluate(targetExpr, env)
    val index = evaluate(indexExpr, env)
    val locationOpt = targetExpr.sourceInfoOpt.map(_.location)
    index match {
      case MashString(s, _) ⇒ MemberEvaluator.lookup(target, s, locationOpt)
      case n: MashNumber ⇒
        val i = n.asInt.getOrElse(throw new EvaluatorException("Unable to lookup " + n, locationOpt))
        target match {
          case xs: Seq[_] ⇒
            val index = if (i < 0) i + xs.size else i
            xs(index)
          case s: MashString ⇒ s.lookup(i)
          case _             ⇒ throw new EvaluatorException("Unable to lookup", locationOpt)
        }
      case _ ⇒ throw new EvaluatorException("Unable to lookup", locationOpt)
    }
  }

  private def evaluateBinOp(binOp: BinOpExpr, env: Environment) = {
    val BinOpExpr(left, op, right, _) = binOp
    val allowImmediateBareStrings = op == BinaryOperator.Equals || op == BinaryOperator.NotEquals
    lazy val leftResult = evaluate(left, env)
    lazy val rightResult = evaluate(right, env)
    def compareWith(f: (Int, Int) ⇒ Boolean): Boolean =
      PartialFunction.cond(leftResult) {
        case l: Comparable[_] ⇒ f(l.asInstanceOf[Comparable[Any]].compareTo(rightResult), 0)
      }
    op match {
      case BinaryOperator.And               ⇒ if (Truthiness.isTruthy(leftResult)) rightResult else leftResult
      case BinaryOperator.Or                ⇒ if (Truthiness.isFalsey(leftResult)) rightResult else leftResult
      case BinaryOperator.Equals            ⇒ leftResult == rightResult
      case BinaryOperator.NotEquals         ⇒ leftResult != rightResult
      case BinaryOperator.Plus              ⇒ add(leftResult, rightResult, binOp.sourceInfoOpt.map(_.location))
      case BinaryOperator.Minus             ⇒ leftResult.asInstanceOf[MashNumber] - rightResult.asInstanceOf[MashNumber]
      case BinaryOperator.Multiply          ⇒ leftResult.asInstanceOf[MashNumber] * rightResult.asInstanceOf[MashNumber]
      case BinaryOperator.Divide            ⇒ leftResult.asInstanceOf[MashNumber] / rightResult.asInstanceOf[MashNumber]
      case BinaryOperator.LessThan          ⇒ compareWith(_ < _)
      case BinaryOperator.LessThanEquals    ⇒ compareWith(_ <= _)
      case BinaryOperator.GreaterThan       ⇒ compareWith(_ > _)
      case BinaryOperator.GreaterThanEquals ⇒ compareWith(_ >= _)
      case BinaryOperator.Sequence          ⇒ leftResult; rightResult
    }
  }

  def add(left: Any, right: Any, locationOpt: Option[PointedRegion]): Any = (left, right) match {
    case (xs: Seq[_], ys: Seq[_])              ⇒ xs ++ ys
    case (s: MashString, right)                ⇒ s + right
    case (left, s: MashString)                 ⇒ s.rplus(left)
    case (left: MashNumber, right: MashNumber) ⇒ left + right
    case _                                     ⇒ throw new EvaluatorException("Could not add, incompatible types", locationOpt)
  }

  private def callFunction(function: Any, arguments: Arguments, functionExpr: Expr, invocationExpr: Expr): Any =
    callFunction(function, arguments, Some(functionExpr), Some(invocationExpr))

  def callFunction(function: Any, arguments: Arguments, functionExprOpt: Option[Expr] = None, invocationExprOpt: Option[Expr] = None): Any = {
    val functionLocationOpt = functionExprOpt.flatMap(_.sourceInfoOpt).map(_.location)
    val invocationLocationOpt = invocationExprOpt.flatMap(_.sourceInfoOpt).map(_.location)
    function match {
      case xs: Seq[_] ⇒
        arguments.positionArgs(0) match {
          case n: MashNumber ⇒
            val i = n.asInt.getOrElse(
              throw new EvaluatorException("Invalid index", invocationLocationOpt))
            val index = if (i < 0) i + xs.size else i
            xs(index)
          case _ ⇒
            throw new EvaluatorException("Invalid index", invocationLocationOpt)
        }
      case MashString(s, _) ⇒
        arguments.positionArgs(0) match {
          case xs: Seq[_] ⇒
            xs.map { target ⇒
              val intermediateResult = MemberEvaluator.lookup(target, s, functionLocationOpt)
              Evaluator.immediatelyResolveNullaryFunctions(intermediateResult)
            }
          case target ⇒
            val intermediateResult = MemberEvaluator.lookup(target, s, functionLocationOpt)
            Evaluator.immediatelyResolveNullaryFunctions(intermediateResult)
        }
      case f: MashFunction ⇒
        f(arguments)
      case BoundMethod(target, method, _) ⇒
        method(target, arguments)
      case _ ⇒
        throw EvaluatorException(s"Not callable", functionLocationOpt)
    }
  }
}

sealed trait EvaluatedArgument

object EvaluatedArgument {

  case class PositionArg(value: Any) extends EvaluatedArgument

  case class LongFlag(flag: String, valueOpt: Option[Any]) extends EvaluatedArgument

  case class ShortFlag(flags: Seq[String]) extends EvaluatedArgument

}
