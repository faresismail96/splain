package splain.runtime

import scala.reflect.runtime.{currentMirror, universe}
import scala.tools.nsc.reporters.{Reporter, StoreReporter}
import scala.tools.nsc.{Global, Settings}
import scala.tools.reflect.ToolBox
import scala.util.Try

trait TryCompile {

  def issues: Seq[Issue]

  case class Level(level: Int) {

    def filteredIssues: Seq[Issue] = issues.filter { i =>
      i.severity == level
    }

    def displayIssues: String = issues
      .map { i =>
        i.display
      }
      .mkString("\n")
  }

  object Error extends Level(2)
  object Warning extends Level(1)
  object Info extends Level(0)
}

object TryCompile {

  trait Resolved extends TryCompile

  case class Success(issues: Seq[Issue] = Nil) extends Resolved

  object Empty extends Success()

  trait Failure extends Resolved {

    lazy val maySucceed: Resolved = {
      if (Error.filteredIssues.isEmpty) {
        Success(issues)
      } else {
        this
      }
    }
  }

  case class TypingError(issues: Seq[Issue] = Nil) extends Failure
  case class ParsingError(issues: Seq[Issue] = Nil) extends Failure
  case class OtherFailure(e: Throwable) extends Failure {
    override def issues: Seq[Issue] = Nil
  }

  trait Engine {

    def args: String

    final def apply(code: String): TryCompile =
      try {
        doCompile(code)
      } catch {
        case e: Throwable =>
          OtherFailure(e)
      }

    def doCompile(code: String): TryCompile
  }

  val mirror: universe.Mirror = currentMirror

  case class UseReflect(args: String, sourceName: String = "newSource1.scala") extends Engine {

    override def doCompile(code: String): TryCompile = {

      val frontEnd = CachingFrontEnd(sourceName)

      val toolBox: ToolBox[universe.type] =
        ToolBox(mirror).mkToolBox(frontEnd, options = args)

      val cached = frontEnd.cached.toSeq

      val parsed = Try {
        toolBox.parse(code)
      }.recover {
        case _: Throwable =>
          return TryCompile.ParsingError(cached)
      }.get

      Try {
        toolBox.compile(parsed)
      }.recover {
        case _: Throwable =>
          return TryCompile.TypingError(cached)
      }.get

      TryCompile.Success(cached)
    }
  }

  case class UseNSC(args: String, sourceName: String = "newSource1.scala") extends Engine {

    val global: Global = {
      val _settings = new Settings()

      _settings.reporter.value = classOf[StoreReporter].getCanonicalName
      _settings.usejavacp.value = true
      _settings.processArgumentString(args)

      val global = Global(_settings, Reporter(_settings))
      global
    }

    val reporter: StoreReporter = global.reporter.asInstanceOf[StoreReporter]

    override def doCompile(code: String): TryCompile = {

      val unit = global.newCompilationUnit(code, sourceName)

      val run = new global.Run()

      val parser = global.newUnitParser(unit)
      parser.parse()

      def reports = reporter.infos.toSeq.map { info =>
        Issue(info.severity.id, info.msg, info.pos, sourceName)
      }

      val result = if (reports.exists(v => v.severity == Empty.Error.level)) {

        ParsingError(reports)
      } else {

        run.compileUnits(List(unit))
        TypingError(reports)
      }

      result.maySucceed
    }
  }
}
