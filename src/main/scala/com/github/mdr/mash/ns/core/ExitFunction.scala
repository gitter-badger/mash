package com.github.mdr.mash.ns.core

import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.functions.Parameter
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.inference._

object ExitFunction extends MashFunction("core.exit") {

  object Params {
    val Status = Parameter(
      name = "status",
      summary = "Exit status",
      defaultValueGeneratorOpt = Some(() ⇒ MashNumber(0)))
  }
  import Params._

  val params = ParameterModel(Seq(Status))

  def apply(arguments: Arguments): Unit = {
    val boundParams = params.validate(arguments)
    val status = boundParams(Status).asInstanceOf[MashNumber].asInt.get
    System.exit(status)
  }

  override def typeInferenceStrategy = ConstantTypeInferenceStrategy(Type.Instance(UnitClass))

  override def summary = "Exit mash"

}