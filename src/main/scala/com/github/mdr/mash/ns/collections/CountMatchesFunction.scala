package com.github.mdr.mash.ns.collections

import com.github.mdr.mash.completions.CompletionSpec
import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.evaluator.MashNumber
import com.github.mdr.mash.evaluator.Truthiness
import com.github.mdr.mash.functions._
import com.github.mdr.mash.inference._
import com.github.mdr.mash.ns.core.NumberClass

object CountMatchesFunction extends MashFunction("collections.countMatches") {

  object Params {
    val Predicate = Parameter(
      name = "predicate",
      summary = "Predicate used to test elements of the sequence")
    val Sequence = Parameter(
      name = "sequence",
      summary = "Sequence to count matches in",
      isLast = true)
  }
  import Params._

  val params = ParameterModel(Seq(Predicate, Sequence))

  def apply(arguments: Arguments): MashNumber = {
    val boundParams = params.validate(arguments)
    val sequence = FunctionHelpers.interpretAsSequence(boundParams(Sequence))
    val predicate = FunctionHelpers.interpretAsFunction(boundParams(Predicate))
    val n = sequence.count(x ⇒ Truthiness.isTruthy(predicate(x)))
    MashNumber(n)
  }

  override def typeInferenceStrategy = CountMatchesTypeInferenceStrategy

  override def getCompletionSpecs(argPos: Int, arguments: TypedArguments) =
    MapFunction.getCompletionSpecs(argPos, arguments)

  override def summary = "Count how many times a predicate holds within a sequence"

  override def descriptionOpt = Some("""Examples:
  countMatches (_ > 3) [1, 2, 3, 4, 5] # 2""")

}

object CountMatchesTypeInferenceStrategy extends TypeInferenceStrategy {

  def inferTypes(inferencer: Inferencer, arguments: TypedArguments): Option[Type] = {
    import CountMatchesFunction.Params._
    val argBindings = CountMatchesFunction.params.bindTypes(arguments)
    val sequenceExprOpt = argBindings.get(Sequence)
    val predicateExprOpt = argBindings.get(Predicate)
    MapTypeInferenceStrategy.inferAppliedType(inferencer, predicateExprOpt, sequenceExprOpt)
    Some(Type.Instance(NumberClass))
  }

}
