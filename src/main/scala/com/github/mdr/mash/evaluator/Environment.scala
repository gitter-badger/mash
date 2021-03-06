package com.github.mdr.mash.evaluator

import com.github.mdr.mash.ns.core._
import com.github.mdr.mash.ns.time._
import com.github.mdr.mash.ns.os._
import com.github.mdr.mash.ns.collections._
import scala.collection.mutable
import scala.collection.JavaConverters._
import com.github.mdr.mash.ns.StandardFunctions
import scala.collection.immutable.ListMap
import scala.collection.mutable.LinkedHashMap
import com.github.mdr.mash.ns.git.LogFunction

case class Environment(bindings: Map[String, Any], globalVariables: mutable.Map[String, Any]) {

  def get(name: String): Option[Any] = bindings.get(name)

  def addBinding(name: String, value: Any) = Environment(bindings + (name -> value), globalVariables)

  def valuesMap: Map[String, Any] = {
    (for ((k, v) ← globalVariables.toMap) yield k -> v) ++
      (for ((k, v) ← bindings) yield k -> v)
  }

}

object Environment {

  def create = Environment(Map(), createGlobalVariables())

  def createGlobalVariables(): mutable.Map[String, Any] = {
    val nameFunctionPairs = StandardFunctions.Functions.flatMap(f ⇒ f.nameOpt.map(_ -> f))
    val aliasPairs = StandardFunctions.Aliases.toSeq
    val otherPairs = Seq(
      "env" -> systemEnvironment,
      "config" -> defaultConfig,
      "git" -> MashObject(LinkedHashMap(
        "log" -> LogFunction)))
    mutable.Map(nameFunctionPairs ++ aliasPairs ++ otherPairs: _*)
  }

  private def defaultConfig =
    MashObject(LinkedHashMap(
      "language" -> MashObject(LinkedHashMap(
        "bareWords" -> false))))

  private def systemEnvironment = {
    val fields: Map[String, Any] =
      for ((k, v) ← System.getenv.asScala.toMap)
        yield k -> MashString(v)
    MashObject(ListMap(fields.toSeq: _*), classOpt = None)
  }
}

