package com.github.mdr.mash.ns.core.help

import com.github.mdr.mash.evaluator.MashClass
import com.github.mdr.mash.evaluator.Field
import com.github.mdr.mash.inference.Type
import com.github.mdr.mash.ns.core.StringClass

object FieldHelpClass extends MashClass("core.help.FieldHelp") {

  object Fields {
    val Name = Field("name", "Field name", Type.Instance(StringClass))
    val Class = Field("class", "Class this field belongs to", Type.Instance(StringClass))
    val Summary = Field("summary", "Summary of what the function does", Type.Instance(StringClass))
    val Description = Field("description", "Description of the function", Type.Instance(StringClass))
  }

  import Fields._

  override val fields = Seq(Name, Class, Summary, Description)

  override def summary = "Help documentation for a field"

}