/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.scriptedtest.ScriptedLauncher
import sbt.util.LogExchange

import scala.annotation.tailrec
import scala.sys.process.Process
import java.io.File.pathSeparator

object RunFromSourceMain {
  def fork(
      workingDirectory: File,
      scalaVersion: String,
      sbtVersion: String,
      classpath: Seq[File]
  ): Process = {
    val fo = ForkOptions()
      .withOutputStrategy(OutputStrategy.StdoutOutput)
    fork(fo, workingDirectory, scalaVersion, sbtVersion, classpath)
  }

  def fork(
      fo0: ForkOptions,
      workingDirectory: File,
      scalaVersion: String,
      sbtVersion: String,
      cp: Seq[File]
  ): Process = {
    val fo = fo0
      .withWorkingDirectory(workingDirectory)
      .withRunJVMOptions(sys.props.get("sbt.ivy.home") match {
        case Some(home) => Vector(s"-Dsbt.ivy.home=$home")
        case _          => Vector()
      })
    implicit val runner = new ForkRun(fo)
    val options =
      Vector(workingDirectory.toString, scalaVersion, sbtVersion, cp.mkString(pathSeparator))
    val log = LogExchange.logger("RunFromSourceMain.fork", None, None)
    runner.fork("sbt.RunFromSourceMain", cp, options, log)
  }

  def main(args: Array[String]): Unit = args match {
    case Array() =>
      sys.error(
        s"Must specify working directory, scala version and sbt version and classpath as the first three arguments"
      )
    case Array(wd, scalaVersion, sbtVersion, classpath, args @ _*) =>
      System.setProperty("jna.nosys", "true")
      run(file(wd), scalaVersion, sbtVersion, classpath, args)
  }

  // this arrangement is because Scala does not always properly optimize away
  // the tail recursion in a catch statement
  @tailrec private[sbt] def run(
      baseDir: File,
      scalaVersion: String,
      sbtVersion: String,
      classpath: String,
      args: Seq[String],
  ): Unit =
    runImpl(baseDir, scalaVersion, sbtVersion, classpath, args) match {
      case Some((baseDir, args)) => run(baseDir, scalaVersion, sbtVersion, classpath, args)
      case None                  => ()
    }

  private def runImpl(
      baseDir: File,
      scalaVersion: String,
      sbtVersion: String,
      classpath: String,
      args: Seq[String],
  ): Option[(File, Seq[String])] =
    try launch(baseDir, scalaVersion, sbtVersion, classpath, args) map exit
    catch {
      case r: xsbti.FullReload            => Some((baseDir, r.arguments()))
      case scala.util.control.NonFatal(e) => e.printStackTrace(); errorAndExit(e.toString)
    }

  private def launch(
      baseDirectory: File,
      scalaVersion: String,
      sbtVersion: String,
      classpath: String,
      arguments: Seq[String],
  ): Option[Int] = {
    ScriptedLauncher
      .launch(
        scalaHome(scalaVersion),
        sbtVersion,
        scalaVersion,
        bootDirectory,
        baseDirectory,
        classpath.split(java.io.File.pathSeparator).map(file),
        arguments.toArray
      )
      .orElse(null) match {
      case null                   => None
      case i if i == Int.MaxValue => None
      case i                      => Some(i)
    }
  }

  private lazy val bootDirectory: File = file(sys.props("user.home")) / ".sbt" / "boot"
  private def scalaHome(scalaVersion: String): File = {
    val log = sbt.util.LogExchange.logger("run-from-source")
    val scalaHome0 = bootDirectory / s"scala-$scalaVersion"
    if ((scalaHome0 / "lib").exists) scalaHome0
    else {
      log.info(s"""scalaHome ($scalaHome0) wasn't found""")
      val fakeboot = file(sys.props("user.home")) / ".sbt" / "fakeboot"
      val scalaHome1 = fakeboot / s"scala-$scalaVersion"
      val scalaHome1Lib = scalaHome1 / "lib"
      val scalaHome1Temp = scalaHome1 / "temp"
      if (scalaHome1Lib.exists) log.info(s"""using $scalaHome1 that was found""")
      else {
        log.info(s"""creating $scalaHome1 by downloading scala-compiler $scalaVersion""")
        IO.createDirectories(List(scalaHome1Lib, scalaHome1Temp))
        val lm = {
          import sbt.librarymanagement.ivy.IvyDependencyResolution
          val ivyConfig = InlineIvyConfiguration().withLog(log)
          IvyDependencyResolution(ivyConfig)
        }
        val Name = """(.*)(\-[\d|\.]+)\.jar""".r
        val module = "org.scala-lang" % "scala-compiler" % scalaVersion
        lm.retrieve(module, scalaModuleInfo = None, scalaHome1Temp, log) match {
          case Right(_) =>
            (scalaHome1Temp ** "*.jar").get foreach { x =>
              val Name(head, _) = x.getName
              IO.copyFile(x, scalaHome1Lib / (head + ".jar"))
            }
          case Left(w) => sys.error(w.toString)
        }
      }
      scalaHome1
    }
  }

  private def errorAndExit(msg: String): Nothing = { System.err.println(msg); exit(1) }
  private def exit(code: Int): Nothing = System.exit(code).asInstanceOf[Nothing]
}
