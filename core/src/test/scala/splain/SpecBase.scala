package splain

import org.scalactic.Prettifier
import org.scalatest.funspec.AnyFunSpec

import scala.util.Try

trait SpecBase extends AnyFunSpec with TestHelpers {

  protected def _it: ItWord = it
}

object SpecBase {

  trait Direct extends SpecBase {

    // will use reflection to discover all type `() => String` method under this instance
    lazy val codeToName: Map[String, String] = {

      val methods = this.getClass.getDeclaredMethods.filter { method =>
        method.getParameterCount == 0 &&
        method.getReturnType == classOf[String]
//        method.isAccessible
      }

      val seq = methods.flatMap { method =>
        Try {
          val code = method.invoke(this).asInstanceOf[String]
          code -> method.getName
        }.toOption
      }.toSeq
      Map(seq: _*)
    }

    lazy val runner: DirectRunner = DirectRunner()

    def check(code: String): Unit = {

      val name = codeToName(code)
      val cc = DirectCase(code)

      val groundTruth = runner.groundTruths(runner.pointer.getAndIncrement())

      _it(name) {
        val error = cc.splainC.compileError()
        error must_== groundTruth
      }
    }

    def skip(code: String, numberOfBlocks: Int = 1): Unit = {

      val name = codeToName(code)

      runner.pointer.getAndAdd(numberOfBlocks)

      ignore(name) {}
    }
  }

  trait File extends SpecBase {

    def check(
        name: String,
        file: String = "",
        extra: String = defaultExtra
    )(
        check: CheckFile
    ): Unit = {

      val _file =
        if (file.isEmpty) name
        else file

      val testName = Seq(name, file).filter(_.nonEmpty).mkString(" - ")

      _it(testName) {

        check(FileCase(_file, extra))
      }
    }

    def skip(
        name: String,
        file: String = "",
        extra: String = defaultExtra
    )(
        check: CheckFile
    ): Unit = {

      val _file =
        if (file.isEmpty) name
        else file

      val testName = Seq(name, file).filter(_.nonEmpty).mkString(" - ")

      ignore(testName) {

        check(FileCase(_file, extra))
      }
    }
  }
}
