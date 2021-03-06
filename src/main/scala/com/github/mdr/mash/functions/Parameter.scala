package com.github.mdr.mash.functions

case class Parameter(
    name: String,
    summary: String, // One-liner description of the parameter
    descriptionOpt: Option[String] = None, // Rest of the docs
    shortFlagOpt: Option[Character] = None, // Single character version of the flag
    defaultValueGeneratorOpt: Option[() ⇒ Any] = None, // Default value if none provided
    isVariadic: Boolean = false, // Is a variadic parameter (can be bound to 0-to-many arguments)
    isFlag: Boolean = false, // If true, can only be called in flag mode, not positional
    isFlagValueAllowed: Boolean = true, // If true, flag can have a value
    isFlagValueMandatory: Boolean = false, // If true, flag must have a value
    flagValueNameOpt: Option[String] = None, // Name of flag value (used in generating calling summary)
    isLast: Boolean = false /* If true, is the last parameter -- absorbs the last parameter in the list */ ) {

  def isOptional = defaultValueGeneratorOpt.isDefined

  def isMandatory = defaultValueGeneratorOpt.isEmpty

}
