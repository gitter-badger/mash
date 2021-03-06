package com.github.mdr.mash.ns.core.help

import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.functions.Parameter
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.inference._
import scala.collection.immutable.ListMap
import com.github.mdr.mash.functions.MashMethod

object HelpFunction extends MashFunction("core.help.help") {

  private val Item = "item"

  val params = ParameterModel(Seq(
    Parameter(
      name = Item,
      summary = "Find help for the given item")))

  def apply(arguments: Arguments): MashObject = {
    val boundParams = params.validate(arguments)
    val item = boundParams(Item)
    getHelp(item)
  }

  override def typeInferenceStrategy = ConstantTypeInferenceStrategy(Type.Instance(FunctionHelpClass))

  override def summary = "Find help for the given item"

  def getHelp(item: Any): MashObject = item match {
    case f: MashFunction  ⇒ getHelp(f)
    case bm: BoundMethod  ⇒ getHelp(bm)
    case klass: MashClass ⇒ getHelp(klass)
    case _                ⇒ throw new EvaluatorException("No help available")
  }

  def getHelp(f: MashFunction): MashObject = {
    import FunctionHelpClass.Fields._
    MashObject(
      ListMap(
        Name -> MashString(f.name),
        FullyQualifiedName -> MashString(f.fullyQualifiedName),
        Summary -> MashString(f.summary),
        CallingSyntax -> MashString(f.name + " " + f.params.callingSyntax),
        Description -> f.descriptionOpt.map(MashString(_)).orNull,
        Parameters -> f.params.params.map(getHelp),
        Class -> null),
      FunctionHelpClass)
  }

  def getHelp(bm: BoundMethod): MashObject = {
    val m = bm.method
    getHelp(bm.method, bm.klass)
  }

  def getHelp(m: MashMethod, klass: MashClass): MashObject = {
    import FunctionHelpClass.Fields._
    MashObject(
      ListMap(
        Name -> MashString(m.name),
        FullyQualifiedName -> MashString(m.name),
        Summary -> MashString(m.summary),
        CallingSyntax -> MashString(m.name + " " + m.params.callingSyntax),
        Description -> m.descriptionOpt.map(MashString(_)).orNull,
        Parameters -> m.params.params.map(getHelp),
        Class -> MashString(klass.fullyQualifiedName)),
      FunctionHelpClass)
  }

  def getHelp(field: Field, klass: MashClass): MashObject = {
    import FieldHelpClass.Fields._
    MashObject(
      ListMap(
        Name -> MashString(field.name),
        Class -> MashString(klass.fullyQualifiedName),
        Summary -> MashString(field.summary),
        Description -> field.descriptionOpt.map(MashString(_)).orNull),
      FieldHelpClass)
  }

  def getHelp(param: Parameter): MashObject = {
    import ParameterHelpClass.Fields._
    MashObject(
      ListMap(
        Name -> MashString(param.name),
        Summary -> MashString(param.summary),
        Description -> param.descriptionOpt.map(MashString(_)).orNull,
        ShortFlag -> param.shortFlagOpt.map(c ⇒ MashString(c + "")).orNull,
        IsFlagParameter -> param.isFlag,
        IsOptional -> param.isOptional,
        IsLast -> param.isLast,
        IsVariadic -> param.isVariadic),
      ParameterHelpClass)
  }

  def getHelp(klass: MashClass): MashObject = {
    import ClassHelpClass.Fields._
    val fields = klass.fields.map(getHelp(_, klass))
    val methods = klass.methods.sortBy(_.name).map(getHelp(_, klass))
    MashObject(
      ListMap(
        Name -> MashString(klass.name),
        FullyQualifiedName -> MashString(klass.fullyQualifiedName),
        Summary -> MashString(klass.summary),
        Description -> klass.descriptionOpt.map(MashString(_)).orNull,
        Parent -> klass.parentOpt.map(p ⇒ MashString(p.fullyQualifiedName)).orNull,
        Fields -> fields,
        Methods -> methods),
      ClassHelpClass)
  }

}
