package com.github.mdr.mash.ns.core

import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.functions.Parameter
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.inference._

object ParseNumberFunction extends MashFunction("core.parseNumber") {

  object Params {
    val String = Parameter(
      name = "string",
      summary = "String to parse as a number")
  }

  val params = ParameterModel(Seq(Params.String))

  def apply(arguments: Arguments): MashNumber = {
    val boundParams = params.validate(arguments)
    val s = boundParams(Params.String).asInstanceOf[MashString]
    MashNumber(s.s.toDouble)
  }

  override def typeInferenceStrategy = ConstantTypeInferenceStrategy(Type.Instance(NumberClass))

  override def summary = "Parse the given string as a number"

  override def descriptionOpt = Some("""Examples:
  parseNumber "42" # 42""")
}
