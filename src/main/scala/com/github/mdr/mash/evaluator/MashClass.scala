package com.github.mdr.mash.evaluator

import scala.collection.immutable.ListMap
import com.github.mdr.mash.inference.AnnotatedExpr
import com.github.mdr.mash.inference.Type
import com.github.mdr.mash.inference.TypeInferenceStrategy
import com.github.mdr.mash.ns.core.ObjectClass
import com.github.mdr.mash.parser.AbstractSyntax
import com.github.mdr.mash.inference.TypedArguments
import com.github.mdr.mash.functions.Namespace
import com.github.mdr.mash.functions.MashMethod

object MashClass {

  /**
   * Create an alias to a method
   */
  def alias(name: String, method: MashMethod): MashMethod = new MashMethod(name) {

    val params = method.params

    def apply(target: Any, arguments: Arguments): Any =
      method.apply(target, arguments)

    override def typeInferenceStrategy = method.typeInferenceStrategy

    override def getCompletionSpecs(argPos: Int, targetTypeOpt: Option[Type], arguments: TypedArguments) =
      method.getCompletionSpecs(argPos, targetTypeOpt, arguments)

    override def summary = method.summary

    override def descriptionOpt = method.descriptionOpt

  }

}

abstract class MashClass(val nameOpt: Option[String], val namespaceOpt: Option[Namespace] = None) {

  def this(s: String) = this(s.split("\\.").lastOption, Some(Namespace(s.split("\\.").init)))

  def fields: Seq[Field] = Seq()

  lazy val fieldsMap: ListMap[String, Field] = {
    val pairs = for (field ← fields) yield field.name -> field
    ListMap(pairs: _*)
  }

  def methods: Seq[MashMethod] = Seq()

  def getMethod(name: String) = methods.find(_.name == name)

  lazy val memberNames: Seq[String] = fields.map(_.name) ++ methods.map(_.name)

  override def toString = fullyQualifiedName

  def name = nameOpt.getOrElse("AnonymousClass")

  def parentOpt: Option[MashClass] = Some(ObjectClass)

  def enumerationValues: Option[Seq[String]] = None

  def fullyQualifiedName: String = namespaceOpt.map(_ + ".").getOrElse("") + name

  def summary: String

  def descriptionOpt: Option[String] = None

}

case class Field(name: String, summary: String, fieldType: Type, descriptionOpt: Option[String] = None)