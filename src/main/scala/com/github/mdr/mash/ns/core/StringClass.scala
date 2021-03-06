package com.github.mdr.mash.ns.core

import java.util.regex.Pattern
import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.inference._
import com.github.mdr.mash.ns.time._
import com.github.mdr.mash.functions.MashMethod
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.functions.Parameter
import com.github.mdr.mash.ns.os.PathSummaryClass
import com.github.mdr.mash.os.linux.LinuxEnvironmentInteractions
import com.github.mdr.mash.os.linux.LinuxFileSystem
import com.github.mdr.mash.ns.os.PathClass

object StringClass extends MashClass("core.String") {

  private val fileSystem = LinuxFileSystem
  private val envInteractions = LinuxEnvironmentInteractions

  override val methods = Seq(
    FirstMethod,
    GlobMethod,
    MatchesMethod,
    LengthMethod,
    LastMethod,
    RMethod,
    ReplaceMethod,
    ReverseMethod,
    StartsWithMethod,
    SplitMethod,
    TagMethod,
    ToLowerMethod,
    ToNumberMethod,
    ToPathMethod,
    ToUpperMethod,
    UntaggedMethod,
    MashClass.alias("g", GlobMethod))

  object GlobMethod extends MashMethod("glob") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): Seq[MashObject] = {
      params.validate(arguments)
      val pattern = new TildeExpander(envInteractions).expand(target.asInstanceOf[MashString].s)
      fileSystem.glob(pattern).map(PathSummaryClass.asMashObject)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Seq(Type.Instance(PathSummaryClass)))

    override def summary = "Return paths matching a glob pattern"

  }

  object RMethod extends MashMethod("r") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashString = {
      params.validate(arguments)
      target.asInstanceOf[MashString].copy(tagClassOpt = Some(RegexClass))
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(StringClass, RegexClass))

    override def summary = "This string as a regular expression"

  }

  object TagMethod extends MashMethod("tag") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashClass = {
      params.validate(arguments)
      target.asInstanceOf[MashString].tagClassOpt.orNull
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(ClassClass))

    override def summary = "This string's tagged type, if any"
  }

  object UntaggedMethod extends MashMethod("untagged") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashString = {
      params.validate(arguments)
      target.asInstanceOf[MashString].copy(tagClassOpt = None)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(StringClass))

    override def summary = "This string without any tag class"
  }

  object MatchesMethod extends MashMethod("matches") {

    private val _Pattern = "pattern"

    val params = ParameterModel(Seq(
      Parameter(
        _Pattern,
        "Regular expression pattern")))

    def apply(target: Any, arguments: Arguments): Boolean = {
      val boundParams = params.validate(arguments)
      val s = target.asInstanceOf[MashString].s
      val pattern = boundParams(_Pattern).asInstanceOf[MashString].s
      Pattern.compile(pattern).matcher(s).find
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(BooleanClass))

    override def summary = "Test whether this string matches a regular expression"

  }

  object SplitMethod extends MashMethod("split") {

    private val Separator = "separator"

    val params = ParameterModel(Seq(
      Parameter(
        Separator,
        "Separator to split string on",
        defaultValueGeneratorOpt = Some(() ⇒ null))))

    def apply(target: Any, arguments: Arguments): Seq[MashString] = {
      val boundParams = params.validate(arguments)
      val targetString = target.asInstanceOf[MashString]
      val pieces = boundParams(Separator) match {
        case null ⇒
          targetString.s.split("\\s+")
        case MashString(separator, _) ⇒
          targetString.s.split(Pattern.quote(separator))
        case _ ⇒
          throw new EvaluatorException("Invalid separator")
      }
      pieces.map(piece ⇒ MashString(piece, targetString.tagClassOpt))
    }

    override def typeInferenceStrategy = new MethodTypeInferenceStrategy {

      override def inferTypes(inferencer: Inferencer, targetTypeOpt: Option[Type], arguments: TypedArguments): Option[Type] =
        targetTypeOpt.orElse(Some(Type.Instance(StringClass))).map(Type.Seq)

    }

    override def summary = "Split this string into a sequence of substrings using a separator"

  }

  object ReplaceMethod extends MashMethod("replace") {

    private val Target = "target"
    private val Replacement = "replacement"

    val params = ParameterModel(Seq(
      Parameter(
        Target,
        "String to replace"),
      Parameter(
        Replacement,
        "Replacement string")))

    def apply(target: Any, arguments: Arguments): MashString = {
      val boundParams = params.validate(arguments)
      val s = target.asInstanceOf[MashString]
      val targetString = boundParams(Target).asInstanceOf[MashString].s
      val replacement = boundParams(Replacement).asInstanceOf[MashString].s
      s.modify(_.replace(targetString, replacement))
    }

    override def typeInferenceStrategy = SameStringMethodTypeInferenceStrategy

    override def summary = "Replace occurrences of a string with another"

  }

  object LengthMethod extends MashMethod("length") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashNumber = {
      params.validate(arguments)
      target.asInstanceOf[MashString].length
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(NumberClass))

    override def summary = "Length of this string"

  }

  object ToLowerMethod extends MashMethod("toLower") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashString = {
      params.validate(arguments)
      target.asInstanceOf[MashString].modify(_.toLowerCase)
    }

    override def summary = "Convert string to lowercase"

    override def typeInferenceStrategy = SameStringMethodTypeInferenceStrategy

  }

  object ToUpperMethod extends MashMethod("toUpper") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashString = {
      params.validate(arguments)
      target.asInstanceOf[MashString].modify(_.toUpperCase)
    }

    override def summary = "Convert string to uppercase"

    override def typeInferenceStrategy = SameStringMethodTypeInferenceStrategy

  }

  object ToNumberMethod extends MashMethod("toNumber") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashNumber = {
      params.validate(arguments)
      MashNumber(target.asInstanceOf[MashString].s.toDouble)
    }

    override def summary = "Parse this string as a number"

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(NumberClass))

  }

  object ToPathMethod extends MashMethod("toPath") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashString = {
      params.validate(arguments)
      target.asInstanceOf[MashString].copy(tagClassOpt = Some(PathClass))
    }

    override def summary = "Tag this string as a path"

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(StringClass, PathClass))

  }

  object LastMethod extends MashMethod("last") {

    private val N = "n"

    val params = ParameterModel(Seq(Parameter(
      name = N,
      summary = "Number of elements",
      defaultValueGeneratorOpt = Some(() ⇒ null))))

    def apply(target: Any, arguments: Arguments): MashString = {
      val boundParams = params.validate(arguments)
      val countOpt = Option(boundParams(N)).map(_.asInstanceOf[MashNumber].asInt.get)
      val s = target.asInstanceOf[MashString]
      countOpt match {
        case None    ⇒ s.last
        case Some(n) ⇒ s.modify(_.takeRight(n))
      }
    }

    override def summary = "Last character(s) of this string"

    override def typeInferenceStrategy = SameStringMethodTypeInferenceStrategy

  }

  object FirstMethod extends MashMethod("first") {

    private val N = "n"

    val params = ParameterModel(Seq(Parameter(
      name = N,
      summary = "Number of elements",
      defaultValueGeneratorOpt = Some(() ⇒ null))))

    def apply(target: Any, arguments: Arguments): MashString = {
      val boundParams = params.validate(arguments)
      val countOpt = Option(boundParams(N)).map(_.asInstanceOf[MashNumber].asInt.get)
      val s = target.asInstanceOf[MashString]
      countOpt match {
        case None    ⇒ s.first
        case Some(n) ⇒ s.modify(_.take(n))
      }
    }

    override def summary = "First character(s) of the string"

    override def typeInferenceStrategy = SameStringMethodTypeInferenceStrategy

  }

  object ReverseMethod extends MashMethod("reverse") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashString = {
      params.validate(arguments)
      target.asInstanceOf[MashString].reverse
    }

    override def typeInferenceStrategy = SameStringMethodTypeInferenceStrategy

    override def summary = "Reverse this string"

  }

  object StartsWithMethod extends MashMethod("startsWith") {

    private val Prefix = "prefix"

    val params = ParameterModel(Seq(
      Parameter(
        Prefix,
        "Prefix to test")))

    def apply(target: Any, arguments: Arguments): Boolean = {
      val boundParams = params.validate(arguments)
      val s = target.asInstanceOf[MashString]
      val pattern = boundParams(Prefix).asInstanceOf[MashString]
      s.startsWith(pattern)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(BooleanClass))

    override def summary = "Check if this string starts with another"

  }

  override def summary = "A string"

}

object SameStringMethodTypeInferenceStrategy extends MethodTypeInferenceStrategy {

  override def inferTypes(inferencer: Inferencer, targetTypeOpt: Option[Type], arguments: TypedArguments): Option[Type] =
    targetTypeOpt.orElse(Some(Type.Instance(StringClass)))

}