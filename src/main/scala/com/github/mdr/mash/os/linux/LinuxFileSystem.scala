package com.github.mdr.mash.os.linux

import scala.collection.JavaConverters._
import java.io.IOException
import java.nio.file._
import java.nio.file.attribute._
import java.util.stream.Collectors
import scala.collection.JavaConverters
import scala.collection.mutable.ArrayBuffer
import org.apache.tools.ant.DirectoryScanner
import java.io.File
import com.github.mdr.mash.ns.os.FileTypeClass
import java.util.EnumSet
import com.github.mdr.mash.os.FileSystem
import com.github.mdr.mash.os.PathSummary
import com.github.mdr.mash.os.Permissions
import java.nio.file.attribute.PosixFileAttributeView
import com.github.mdr.mash.os.PermissionsSection
import com.github.mdr.mash.Posix
import org.apache.commons.io.FileUtils

object LinuxFileSystem extends FileSystem {

  override def getChildren(parentDir: Path, ignoreDotFiles: Boolean, recursive: Boolean): Seq[PathSummary] = {
    var files: Seq[Path] =
      if (recursive) {
        import scala.collection.JavaConverters._
        val foundPaths = ArrayBuffer[Path]()
        val visitor = new SimpleFileVisitor[Path]() {

          override def preVisitDirectory(dir: Path, attributes: BasicFileAttributes): FileVisitResult = {
            if (dir != parentDir)
              foundPaths += dir
            if (ignoreDotFiles && dir.getFileName.toString.startsWith(".") && dir != parentDir)
              FileVisitResult.SKIP_SUBTREE
            else
              FileVisitResult.CONTINUE
          }

          override def visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult = {
            foundPaths += file
            FileVisitResult.CONTINUE
          }

          override def visitFileFailed(file: Path, e: IOException): FileVisitResult =
            FileVisitResult.CONTINUE

        }
        Files.walkFileTree(parentDir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, visitor)
        foundPaths
      } else
        Files.list(parentDir).collect(Collectors.toList()).asScala.toSeq.sortBy(_.getFileName)
    if (ignoreDotFiles)
      files = files.filterNot(f ⇒ f.getFileName.toString.startsWith("."))
    files.map(getPathSummary)
  }

  override def getPathSummary(path: Path): PathSummary = {
    val owner = Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).getName
    val attrs = Files.getFileAttributeView(path, classOf[PosixFileAttributeView], LinkOption.NOFOLLOW_LINKS).readAttributes()
    val group = attrs.group().getName
    val lastModified = attrs.lastModifiedTime.toInstant
    val lastAccessed = attrs.lastAccessTime.toInstant
    val perms = attrs.permissions()
    val fileType =
      if (attrs.isSymbolicLink()) FileTypeClass.Values.Link
      else if (attrs.isRegularFile()) FileTypeClass.Values.File
      else if (attrs.isDirectory()) FileTypeClass.Values.Dir
      else if (attrs.isOther()) FileTypeClass.Values.Other
      else null

    PathSummary(
      path = path,
      fileType = fileType,
      size = attrs.size(),
      owner = owner,
      group = group,
      permissions = permissionsObject(perms.asScala.toSet),
      lastModified = lastModified,
      lastAccessed = lastAccessed)
  }

  private def permissionsObject(perms: Set[PosixFilePermission]): Permissions = {
    import PosixFilePermission._
    val ownerPerms = PermissionsSection(
      canRead = perms.contains(OWNER_READ),
      canWrite = perms.contains(OWNER_WRITE),
      canExecute = perms.contains(OWNER_EXECUTE))
    val groupPerms = PermissionsSection(
      canRead = perms.contains(GROUP_READ),
      canWrite = perms.contains(GROUP_WRITE),
      canExecute = perms.contains(GROUP_EXECUTE))
    val othersPerms = PermissionsSection(
      canRead = perms.contains(OTHERS_READ),
      canWrite = perms.contains(OTHERS_WRITE),
      canExecute = perms.contains(OTHERS_EXECUTE))
    Permissions(
      owner = ownerPerms,
      group = groupPerms,
      others = othersPerms)
  }

  override def glob(pattern: String): Seq[PathSummary] = {
    val startDir = GlobHelper.globStart(pattern)
    val matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern)
    val foundPaths = ArrayBuffer[Path]()
    val visitor = new SimpleFileVisitor[Path]() {

      override def preVisitDirectory(dir: Path, attributes: BasicFileAttributes): FileVisitResult = {
        if (matcher.matches(dir))
          foundPaths += dir
        FileVisitResult.CONTINUE
      }

      override def visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult = {
        if (matcher.matches(file))
          foundPaths += file
        FileVisitResult.CONTINUE
      }

      override def visitFileFailed(file: Path, e: IOException): FileVisitResult =
        FileVisitResult.CONTINUE

    }
    Files.walkFileTree(startDir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, visitor)
    foundPaths.map(getPathSummary)
  }

  override def pwd: Path = Paths.get(Posix.posix.getcwd)

  override def chdir(path: Path) {
    //    System.setProperty("user.dir", path.toString)
    Posix.posix.chdir(path.toString)
  }

  def readLines(path: Path): Seq[String] = FileUtils.readLines(path.toFile).asScala.toSeq
}