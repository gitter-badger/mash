package com.github.mdr.mash.ns.collections

import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.evaluator.Evaluator
import com.github.mdr.mash.functions._
import com.github.mdr.mash.inference._
import com.github.mdr.mash.ns.core.UnitClass

object EachFunction extends MashFunction("collections.each") {

  object Params {
    val Action = Parameter(
      name = "action",
      summary = "Function used to act on elements of the sequence")
    val Sequence = Parameter(
      name = "sequence",
      summary = "Sequence to run an action over",
      isLast = true)
  }

  import Params._

  val params = ParameterModel(Seq(Action, Sequence))

  def apply(arguments: Arguments) {
    val boundParams = params.validate(arguments)
    val sequence = FunctionHelpers.interpretAsSequence(boundParams(Sequence))
    val f = FunctionHelpers.interpretAsFunction(boundParams(Action))
    sequence.map(f).map(Evaluator.immediatelyResolveNullaryFunctions)
    ()
  }

  override def typeInferenceStrategy = EachTypeInferenceStrategy

  override def summary = "Perform an action for each element in a sequence"

}

object EachTypeInferenceStrategy extends TypeInferenceStrategy {

  def inferTypes(inferencer: Inferencer, arguments: TypedArguments): Option[Type] = {
    val argBindings = EachFunction.params.bindTypes(arguments)
    import EachFunction.Params._
    val sequenceExprOpt = argBindings.get(Sequence)
    val predicateExprOpt = argBindings.get(Action)
    MapTypeInferenceStrategy.inferAppliedType(inferencer, predicateExprOpt, sequenceExprOpt)
    Some(Type.Instance(UnitClass))
  }
}