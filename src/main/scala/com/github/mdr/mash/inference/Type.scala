package com.github.mdr.mash.inference

import com.github.mdr.mash.ns.core._
import com.github.mdr.mash.ns.collections._
import com.github.mdr.mash.evaluator.MashClass
import com.github.mdr.mash.functions.MashMethod
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.parser.AbstractSyntax._
import scala.collection.immutable.ListMap

sealed trait Type

trait TypeFunction {

  def apply(positionArgs: Seq[Option[Type]], argSet: Set[String], argValues: Map[String, Type]): Option[Type]

}

object Type {
  case object Any extends Type
  case class Seq(t: Type) extends Type
  case class Group(keyType: Type, valuesType: Type) extends Type
  case class Tagged(baseClass: MashClass, tagClass: MashClass) extends Type
  case class Instance(klass: MashClass) extends Type { override def toString = klass.toString }
  case class Object(knownFields: ListMap[String, Type]) extends Type
  case class DefinedFunction(f: MashFunction) extends Type
  case class BoundMethod(receiver: Type, method: MashMethod) extends Type
  case class Lambda(parameter: String, body: Expr, bindings: Map[String, Type]) extends Type {
    override def toString = s"Lambda($parameter, $body)"
  }
}
