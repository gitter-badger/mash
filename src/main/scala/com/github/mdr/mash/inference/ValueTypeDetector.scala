package com.github.mdr.mash.inference

import java.time.Instant
import com.github.mdr.mash.ns.core._
import com.github.mdr.mash.ns.time.DateTimeClass
import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.ns.collections.GroupClass
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.functions.AnonymousFunction

/** Detect the type of runtime values **/
object ValueTypeDetector {

  def getType(x: Any): Type = x match {
    case null                                ⇒ Type.Instance(NullClass)
    case AnonymousFunction(param, body, env) ⇒ Type.Lambda(param, body, TypeInferencer.buildBindings(env, includeGlobal = false))
    case f: MashFunction                     ⇒ Type.DefinedFunction(f)
    case BoundMethod(target, method, _)      ⇒ Type.BoundMethod(getType(target), method)
    case MashString(_, None)                 ⇒ Type.Instance(StringClass)
    case MashString(_, Some(tagClass))       ⇒ Type.Tagged(StringClass, tagClass)
    case MashNumber(_, None)                 ⇒ Type.Instance(NumberClass)
    case MashNumber(_, Some(tagClass))       ⇒ Type.Tagged(NumberClass, tagClass)
    case _: Boolean                          ⇒ Type.Instance(BooleanClass)
    case _: Instant                          ⇒ Type.Instance(DateTimeClass)
    case _: MashClass                        ⇒ Type.Instance(ClassClass)
    case ()                                  ⇒ Type.Instance(UnitClass)
    case MashObject(fields, Some(GroupClass)) ⇒
      val keyType = getType(fields("key"))
      val valueTypes = fields("values").asInstanceOf[Seq[Any]].map(getType).distinct
      valueTypes match {
        case Seq(valueType) ⇒ Type.Group(keyType, valueType)
        case _              ⇒ Type.Group(keyType, Type.Any)
      }
    case MashObject(_, Some(klass)) ⇒ Type.Instance(klass)
    case obj @ MashObject(_, None)  ⇒ Type.Object(for ((field, value) ← obj.immutableFields) yield field -> getType(value))
    case xs: Seq[_] ⇒
      val sequenceTypes = xs.map(getType).distinct
      sequenceTypes match {
        case Seq(sequenceType) ⇒ Type.Seq(sequenceType)
        case _                 ⇒ Type.Seq(Type.Any)
      }
    case _ ⇒ Type.Any
  }

}