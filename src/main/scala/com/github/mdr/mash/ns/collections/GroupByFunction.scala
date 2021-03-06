package com.github.mdr.mash.ns.collections

import scala.collection.immutable.ListMap

import com.github.mdr.mash.completions.CompletionSpec
import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.functions._
import com.github.mdr.mash.inference._
import com.github.mdr.mash.ns.core._
import scala.PartialFunction.condOpt

object GroupByFunction extends MashFunction("collections.groupBy") {

  private val DefaultTotalKeyName = "Total"

  object Params {
    val Discriminator = Parameter(
      name = "discriminator",
      summary = "Function to apply to elements of the sequence to determine a key")
    val Total = Parameter(
      name = "total",
      summary = "Include a total group containing all the results",
      shortFlagOpt = Some('t'),
      defaultValueGeneratorOpt = Some(() ⇒ false),
      isFlag = true,
      flagValueNameOpt = Some("key"),
      descriptionOpt = Some(s"""If true, include an additional group containing all the elements.
If false (the default), this group is not included.
If a non-boolean argument is given, that is used as the key for the additional group.
Otherwise, a default key of "$DefaultTotalKeyName" is used. """))
    val IncludeNull = Parameter(
      name = "includeNull",
      summary = "Include groups that have null keys",
      shortFlagOpt = Some('n'),
      defaultValueGeneratorOpt = Some(() ⇒ false),
      isFlag = true,
      flagValueNameOpt = Some("key"),
      descriptionOpt = Some("""If true, include a group with null keys, if any elements exist for such a group.
If false (the default), exclude a group with a null key.
If a non-boolean argument is given, that will be used as the key for the null group instead of null."""))
    val Sequence = Parameter(
      name = "sequence",
      summary = "Sequence from which to form groups",
      isLast = true)
  }

  import Params._

  val params = ParameterModel(Seq(Discriminator, Total, IncludeNull, Sequence))

  def apply(arguments: Arguments): Any = {
    val boundParams = params.validate(arguments)
    val sequence = FunctionHelpers.interpretAsSequence(boundParams(Sequence))
    val discriminator = FunctionHelpers.interpretAsFunction(boundParams(Discriminator))
    val includeNulls = Truthiness.isTruthy(boundParams(IncludeNull))
    val includeTotal = Truthiness.isTruthy(boundParams(Total))

    val nullKey = boundParams.get(IncludeNull) match {
      case Some(true) | None ⇒ null
      case Some(x)           ⇒ x
    }
    def translateKey(k: Any) = k match {
      case null ⇒ nullKey
      case _    ⇒ k
    }
    var groups =
      for {
        (key, values) ← sequence.groupBy(discriminator).toSeq
        if key != null || includeNulls
        groupKey = translateKey(key)
      } yield makeGroup(groupKey, values)

    if (includeTotal) {
      val totalKey = boundParams(Total) match {
        case true ⇒ MashString(DefaultTotalKeyName)
        case x    ⇒ x
      }
      groups = groups :+ makeGroup(totalKey, sequence)
    }

    groups
  }

  private def makeGroup(key: Any, values: Seq[Any]) =
    MashObject(ListMap(GroupClass.Fields.Key -> key, GroupClass.Fields.Values -> values), GroupClass)

  override def typeInferenceStrategy = GroupByTypeInferenceStrategy

  override def getCompletionSpecs(argPos: Int, arguments: TypedArguments) =
    MapFunction.getCompletionSpecs(argPos, arguments)

  override def summary = "Group together the elements of a sequence sharing a common key"

  override def descriptionOpt = Some("""Returns a sequence of Group objects, where each group contains a subset of the sequence 
sharing the same key, as determined by the given discriminator function.
    
Example:
  groupBy first ["foo", "bar", "baz"]
  ╔═══╤═════╤════════╗
  ║key│count│values  ║
  ╟───┼─────┼────────╢
  ║b  │2    │bar, baz║
  ║f  │1    │foo     ║
  ╚═══╧═════╧════════╝""")

}

object GroupByTypeInferenceStrategy extends TypeInferenceStrategy {

  def inferTypes(inferencer: Inferencer, arguments: TypedArguments): Option[Type] = {
    val argBindings = GroupByFunction.params.bindTypes(arguments)
    import GroupByFunction.Params._
    val sequenceExprOpt = argBindings.get(Sequence)
    val discriminatorExprOpt = argBindings.get(Discriminator)
    for {
      keyType ← MapTypeInferenceStrategy.inferAppliedType(inferencer, discriminatorExprOpt, sequenceExprOpt)
      AnnotatedExpr(_, sequenceTypeOpt) ← sequenceExprOpt
      sequenceType ← sequenceTypeOpt
      valuesType ← condOpt(sequenceType) {
        case Type.Seq(elementType)                                    ⇒ elementType
        case Type.Instance(StringClass) | Type.Tagged(StringClass, _) ⇒ sequenceType
      }
    } yield Type.Seq(Type.Group(keyType, valuesType))
  }

}