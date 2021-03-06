package com.github.mdr.mash.ns.os

import java.nio.file.Files
import java.nio.file.Paths
import scala.collection.JavaConverters._
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import com.github.mdr.mash.ns.time.DateTimeClass
import com.github.mdr.mash.functions.FunctionHelpers._
import com.github.mdr.mash.completions.CompletionSpec
import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.inference._
import com.github.mdr.mash.ns.core._
import com.github.mdr.mash.os.linux.LinuxFileSystem
import com.github.mdr.mash.functions.MashMethod
import com.github.mdr.mash.functions.FunctionHelpers
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.functions.Parameter

object PathClass extends MashClass("os.Path") {

  private val fileSystem = LinuxFileSystem

  override val methods = Seq(
    AbsoluteMethod,
    BaseNameMethod,
    CdMethod,
    ChildrenMethod,
    CopyIntoMethod,
    CopyMethod,
    DeleteMethod,
    ExistsMethod,
    ExtensionMethod,
    FollowLinkMethod,
    GroupMethod,
    InfoMethod,
    IsDirectoryMethod,
    IsEmptyDirMethod,
    IsFileMethod,
    LastModifiedMethod,
    MkdirMethod,
    MoveIntoMethod,
    NameMethod,
    OwnerMethod,
    ParentMethod,
    PermissionsMethod,
    ReadLinesMethod,
    RenameByMethod,
    RenameToMethod,
    SegmentsMethod,
    SizeMethod,
    TypeMethod,
    MashClass.alias("rm", DeleteMethod))

  object ExistsMethod extends MashMethod("exists") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): Boolean = {
      params.validate(arguments)
      val path = FunctionHelpers.interpretAsPath(target)
      Files.exists(path)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(BooleanClass))

    override def summary = "Whether or not an item exists at this location"

  }

  object InfoMethod extends MashMethod("info") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashObject = {
      params.validate(arguments)
      val path = FunctionHelpers.interpretAsPath(target)
      val summary = fileSystem.getPathSummary(path)
      PathSummaryClass.asMashObject(summary)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(PathSummaryClass))

    override def summary = "Get PathSummary object for this path"

  }

  object MkdirMethod extends MashMethod("mkdir") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): Unit = {
      params.validate(arguments)
      val path = FunctionHelpers.interpretAsPath(target)
      Files.createDirectory(path)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(UnitClass))

    override def summary = "Create directory at this path"

  }

  object FollowLinkMethod extends MashMethod("followLink") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashString = {
      params.validate(arguments)
      val path = FunctionHelpers.interpretAsPath(target)
      val resolved = Files.readSymbolicLink(path)
      MashString(resolved.toString, Some(PathClass))
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(StringClass, PathClass))

    override def summary = "Follow this symbolic link"

  }

  object CdMethod extends MashMethod("cd") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments) {
      params.validate(arguments)
      val path = FunctionHelpers.interpretAsPath(target)
      CdFunction.changeDirectory(path)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(UnitClass))

    override def summary = "Change directory to this path"

  }

  object ExtensionMethod extends MashMethod("extension") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): Any = {
      params.validate(arguments)
      val name = FunctionHelpers.interpretAsPath(target).getFileName.toString
      if (name.contains("."))
        MashString(name.reverse.takeWhile(_ != '.').reverse)
      else
        null
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(StringClass))

    override def summary = "File extension, if any"

  }

  object BaseNameMethod extends MashMethod("baseName") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashString = {
      params.validate(arguments)
      val name = FunctionHelpers.interpretAsPath(target).getFileName.toString
      MashString(FilenameUtils.getBaseName(name))
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(StringClass))

    override def summary = "Name without extension"

  }

  object ReadLinesMethod extends MashMethod("readLines") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): Seq[MashString] = {
      params.validate(arguments)
      val path = FunctionHelpers.interpretAsPath(target)
      ReadLinesFunction.readLines(path)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Seq(Type.Instance(StringClass)))

    override def summary = "Read a file and produce a sequence of lines"

  }

  object RenameToMethod extends MashMethod("renameTo") {

    private val NewName = "newName"

    val params = ParameterModel(Seq(
      Parameter(
        NewName,
        "New name")))

    def apply(target: Any, arguments: Arguments): MashString = {
      val boundParams = params.validate(arguments)
      val path = FunctionHelpers.interpretAsPath(target)
      val newName = FunctionHelpers.interpretAsPath(boundParams(NewName))
      val newPath = path.resolveSibling(newName)
      val newLocation = Files.move(path, newPath)
      asPathString(newLocation)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(StringClass, PathClass))

    override def summary = "Rename this path"

  }

  object RenameByMethod extends MashMethod("renameBy") {

    private val F = "f"

    val params = ParameterModel(Seq(
      Parameter(
        F,
        "Function to transform the old name into a new name")))

    def apply(target: Any, arguments: Arguments): MashString = {
      val boundParams = params.validate(arguments)
      val path = FunctionHelpers.interpretAsPath(target)
      val f = FunctionHelpers.interpretAsFunction(boundParams(F))
      val newFileName = f(asPathString(path.getFileName)).asInstanceOf[MashString].s
      val newPath = path.resolveSibling(Paths.get(newFileName))
      val newLocation = Files.move(path, newPath)
      asPathString(newLocation)
    }

    override def typeInferenceStrategy = new MethodTypeInferenceStrategy {
      def inferTypes(inferencer: Inferencer, targetTypeOpt: Option[Type], arguments: TypedArguments): Option[Type] = {
        val argBindings = params.bindTypes(arguments)
        for {
          annotatedExpr ← argBindings.get(F)
          functionType ← annotatedExpr.typeOpt
          targetType ← targetTypeOpt
        } inferencer.applyFunction(functionType, targetType, None)
        Some(Type.Tagged(StringClass, PathClass))
      }

    }

    override def summary = "Rename this path using a function to transform the name"

  }

  object AbsoluteMethod extends MashMethod("absolute") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashString = {
      params.validate(arguments)
      val path = FunctionHelpers.interpretAsPath(target)
      asPathString(fileSystem.pwd.resolve(path).toRealPath())
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(StringClass, PathClass))

    override def summary = "The absolute path to this location"

  }

  object ParentMethod extends MashMethod("parent") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): Any = {
      params.validate(arguments)
      val parent = FunctionHelpers.interpretAsPath(target).getParent
      if (parent == null)
        null
      else
        asPathString(parent)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(StringClass, PathClass))

    override def summary = "The parent of this path"
  }

  object ChildrenMethod extends MashMethod("children") {

    val params = ParameterModel(ChildrenFunction.params.params.tail)

    def apply(target: Any, arguments: Arguments): Seq[MashObject] = {
      val boundParams = params.validate(arguments)
      val ignoreDotFiles = Truthiness.isTruthy(boundParams(ChildrenFunction.Params.IgnoreDotFiles))
      val recursive = Truthiness.isTruthy(boundParams(ChildrenFunction.Params.Recursive))
      val parentDir = FunctionHelpers.interpretAsPath(target)
      ChildrenFunction.getChildren(parentDir, ignoreDotFiles, recursive)
    }

    override def typeInferenceStrategy = new MethodTypeInferenceStrategy() {
      def inferTypes(inferencer: Inferencer, targetTypeOpt: Option[Type], arguments: TypedArguments): Option[Type] = {
        val newArguments = SimpleTypedArguments(arguments.arguments :+ TypedArgument.PositionArg(AnnotatedExpr(None, targetTypeOpt)))
        ChildrenFunction.typeInferenceStrategy.inferTypes(inferencer, newArguments)
      }
    }

    override def summary = "The children of this path"

  }

  object CopyMethod extends MashMethod("copy") {

    private val Destination = "destination"

    val params = ParameterModel(Seq(
      Parameter(
        name = Destination,
        summary = "Location to copy file to")))

    def apply(target: Any, arguments: Arguments): Unit = {
      val boundParams = params.validate(arguments)
      val source = FunctionHelpers.interpretAsPath(target)
      val destination = FunctionHelpers.interpretAsPath(boundParams(Destination))
      if (Files.isDirectory(source))
        if (Files.exists(destination))
          throw new EvaluatorException("Destination already exists")
        else
          FileUtils.copyDirectory(source.toFile, destination.toFile)
      else {
        if (Files.exists(destination) && Files.isDirectory(destination))
          throw new EvaluatorException("Destination already exists, and is a directory")
        else
          Files.copy(source, destination)
      }
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(UnitClass))

    override def summary = "Copy this file or directory to another location"

  }

  object CopyIntoMethod extends MashMethod("copyInto") {

    private val Destination = "destination"

    val params = ParameterModel(Seq(
      Parameter(
        name = Destination,
        summary = "Directory to copy file into")))

    def apply(target: Any, arguments: Arguments): MashString = {
      val boundParams = params.validate(arguments)
      val source = FunctionHelpers.interpretAsPath(target)
      val destination = FunctionHelpers.interpretAsPath(boundParams(Destination))
      if (!Files.isDirectory(destination))
        throw new EvaluatorException(s"Cannot copy into $destination, not a directory")
      if (Files.isDirectory(source))
        FileUtils.copyDirectoryToDirectory(source.toFile, destination.toFile)
      else
        FileUtils.copyFileToDirectory(source.toFile, destination.toFile)
      asPathString(destination.resolve(source.getFileName))
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(StringClass, PathClass))

    override def summary = "Copy this path into another location"

    override def getCompletionSpecs(argPos: Int, targetTypeOpt: Option[Type], arguments: TypedArguments): Seq[CompletionSpec] =
      Seq(CompletionSpec.Directory)

  }

  object MoveIntoMethod extends MashMethod("moveInto") {

    private val Destination = "destination"

    val params = ParameterModel(Seq(
      Parameter(
        name = Destination,
        summary = "Directory to move into")))

    def apply(target: Any, arguments: Arguments): MashString = {
      val boundParams = params.validate(arguments)
      val source = FunctionHelpers.interpretAsPath(target)
      val destination = FunctionHelpers.interpretAsPath(boundParams(Destination))
      if (!Files.isDirectory(destination))
        throw new EvaluatorException(s"Cannot copy into $destination, not a directory")
      val newPath =
        if (Files.isDirectory(source))
          FileUtils.moveDirectoryToDirectory(source.toFile, destination.toFile, false)
        else
          FileUtils.moveFileToDirectory(source.toFile, destination.toFile, false)
      asPathString(destination.resolve(source.getFileName))
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(StringClass, PathClass))

    override def summary = "Move this path into the given directory"

    override def getCompletionSpecs(argPos: Int, targetTypeOpt: Option[Type], arguments: TypedArguments): Seq[CompletionSpec] =
      Seq(CompletionSpec.Directory)

  }

  object DeleteMethod extends MashMethod("delete") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments) {
      params.validate(arguments)
      val path = FunctionHelpers.interpretAsPath(target)
      if (Files.isDirectory(path))
        FileUtils.deleteDirectory(path.toFile)
      else
        Files.delete(path)

    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(UnitClass))

    override def summary = "Delete this path"

  }

  object NameMethod extends MashMethod("name") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashString = {
      params.validate(arguments)
      asPathString(FunctionHelpers.interpretAsPath(target).getFileName)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(StringClass, PathClass))

    override def summary = "Name (final section) of this path"

  }

  object IsDirectoryMethod extends MashMethod("isDirectory") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): Boolean = {
      params.validate(arguments)
      Files.isDirectory(FunctionHelpers.interpretAsPath(target))
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(BooleanClass))

    override def summary = "Check if path is a directory"

  }

  object IsEmptyDirMethod extends MashMethod("isEmptyDir") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): Boolean = {
      params.validate(arguments)
      val path = FunctionHelpers.interpretAsPath(target)
      Files.isDirectory(path) && fileSystem.getChildren(path, ignoreDotFiles = false, recursive = false).isEmpty
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(BooleanClass))

    override def summary = "Check if path is an empty directory"

  }

  object IsFileMethod extends MashMethod("isFile") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): Boolean = {
      params.validate(arguments)
      Files.isRegularFile(FunctionHelpers.interpretAsPath(target))
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(BooleanClass))

    override def summary = "Check if path is a directory"

  }

  object LastModifiedMethod extends MashMethod("lastModified") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): Any = {
      fileSystem.getPathSummary(FunctionHelpers.interpretAsPath(target)).lastModified
      params.validate(arguments)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(DateTimeClass))

    override def summary = "Last time path was modified"

  }

  object OwnerMethod extends MashMethod("owner") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): Any = {
      params.validate(arguments)
      val summary = fileSystem.getPathSummary(FunctionHelpers.interpretAsPath(target))
      MashString(summary.owner, Some(UsernameClass))
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(StringClass, UsernameClass))

    override def summary = "Owner of this path"

  }

  object GroupMethod extends MashMethod("group") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): Any = {
      params.validate(arguments)
      val summary = fileSystem.getPathSummary(FunctionHelpers.interpretAsPath(target))
      MashString(summary.group, Some(GroupClass))
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(StringClass, GroupClass))

    override def summary = "Group owner of this path"

  }

  object PermissionsMethod extends MashMethod("permissions") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashObject = {
      params.validate(arguments)
      val summary = fileSystem.getPathSummary(FunctionHelpers.interpretAsPath(target))
      val permissions = summary.permissions
      PermissionsClass.asMashObject(permissions)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(PermissionsClass))

    override def summary = "Permissions for this path"

  }

  object SizeMethod extends MashMethod("size") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): Any = {
      params.validate(arguments)
      val summary = fileSystem.getPathSummary(FunctionHelpers.interpretAsPath(target))
      MashNumber(summary.size, Some(BytesClass))
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(NumberClass, BytesClass))

    override def summary = "Size of the file at this path"

  }

  object TypeMethod extends MashMethod("type") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashString = {
      params.validate(arguments)
      val summary = fileSystem.getPathSummary(FunctionHelpers.interpretAsPath(target))
      MashString(summary.fileType, Some(FileTypeClass))
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(StringClass, FileTypeClass))

    override def summary = "Type of object at this path (file, directory etc)"

  }

  object SegmentsMethod extends MashMethod("segments") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): Seq[MashString] = {
      params.validate(arguments)
      FunctionHelpers.interpretAsPath(target).asScala.toSeq.map(p ⇒ MashString(p.toString))
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Seq(Type.Instance(StringClass)))

    override def summary = "A sequence of the segments of this path (the parts of the path separated by /)"

  }

  override def summary = "Tag class for a filesystem path"

}