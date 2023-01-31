import scala.collection.immutable._
import mill._, scalalib._
import D._

////// Modules //////////////////////////////////////////////////////

import mill._
import mill.scalalib._
import mill.scalajslib._
import mill.scalajslib.api._

object core extends ScalaJSModule {
  def scalaVersion   = "2.13.10"
  def scalaJSVersion = "1.13.0"
  def ivyDeps        = Agg(ivy"org.scala-js::scalajs-dom::2.2.0", izumiReflect)
  def moduleKind     = T(ModuleKind.CommonJSModule)
  object test extends Tests {
    override def ivyDeps          = Agg(zioTest, zioTestSbt)
    override def testFramework    = "zio.test.sbt.ZTestFramework"
    override def allScalacOptions = T(super.allScalacOptions().distinct)
  }

  private val scalacOptionsCommon = Vector(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:_",
    "-unchecked",
    "-Xfatal-warnings"
  )

  private val scalacOptions213 = scalacOptionsCommon ++ Vector(
    "-Xsource:3",
    "-Xlint:-byname-implicit",
    "-explaintypes",
    "-Vimplicits",
    "-Vtype-diffs",
    "-P:kind-projector:underscore-placeholders"
  )

  private val scalacOptions3 = scalacOptionsCommon ++ Vector(
    "-no-indent",
    "-Ykind-projector"
  )

  override def scalacOptions = T {
    scalaVersion() match {
      case V.scala213 => scalacOptions213
      case V.scala3   => scalacOptions3
    }
  }

  override def scalacPluginIvyDeps: T[Agg[Dep]] = T {
    super.scalacPluginIvyDeps() ++
      (scalaVersion() match {
        case V.scala3 =>
          Agg[Dep]()
        case V.scala213 =>
          Agg(
            ivy"org.typelevel:::kind-projector:0.13.2"
          )
      })
  }
}
//
//object core extends PureCrossModule {
//
//  override def ivyDeps = Agg(
//    izumiReflect
//  )
//
//}

////// Versions //////////////////////////////////////////////////////

object D {
  val izumiReflect = ivy"dev.zio::izumi-reflect::${V.izumiReflect}"

  def zio(m: String) = ivy"dev.zio::zio-$m::${V.zio}"
  val zioTest        = zio("test")
  val zioTestSbt     = zio("test-sbt")

  val scalajsDom = ivy"org.scala-js::scalajs-dom::${V.scalajsDom}"
}

object V {
  val scala213 = "2.13.10"
  val scala3   = "3.2.2"
  val scalaAll = scala213 :: scala3 :: Nil
  val scalaJS  = "1.13.0"

  val cats         = "2.9.0"
  val zio          = "2.0.6"
  val zioQuery     = "0.3.4"
  val fetch        = "3.1.0"
  val izumiReflect = "2.2.2"
  val scalacheck   = "1.17.0"
  val scalajsDom   = "2.3.0"
}

////// Module definition helpers //////////////////////////////////////

trait PureCrossModule extends Module { outer =>

  def ivyDeps: T[Agg[Dep]]

//  object jvm extends Cross[JvmModule](V.scalaAll: _*)
//  class JvmModule(val crossScalaVersion: String) extends DecrelModuleBase {
//    override def millSourcePath = outer.millSourcePath
//    override def ivyDeps        = outer.ivyDeps
//
//    object test extends Tests {
//      def crossScalaVersion      = JvmModule.this.crossScalaVersion
//      override def ivyDeps       = Agg(zioTest, zioTestSbt)
//      override def testFramework = "zio.test.sbt.ZTestFramework"
//    }
//  }

  import scalajslib._, scalajslib.api._

  object js extends Cross[JsModule](V.scalaAll: _*)
  class JsModule(crossScalaVersion: String) extends ScalaJSModule {
    override def millSourcePath = outer.millSourcePath
    override def scalaJSVersion = V.scalaJS
    override def ivyDeps        = T(outer.ivyDeps())
    override def moduleKind     = T(ModuleKind.CommonJSModule)

    override def scalaVersion = crossScalaVersion

    private val scalacOptionsCommon = Vector(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-language:_",
      "-unchecked",
      "-Xfatal-warnings"
    )

    private val scalacOptions213 = scalacOptionsCommon ++ Vector(
      "-Xsource:3",
      "-Xlint:-byname-implicit",
      "-explaintypes",
      "-Vimplicits",
      "-Vtype-diffs",
      "-P:kind-projector:underscore-placeholders"
    )

    private val scalacOptions3 = scalacOptionsCommon ++ Vector(
      "-no-indent",
      "-Ykind-projector"
    )

    override def scalacOptions = T {
      scalaVersion() match {
        case V.scala213 => scalacOptions213
        case V.scala3   => scalacOptions3
      }
    }

    override def scalacPluginIvyDeps: T[Agg[Dep]] =
      crossScalaVersion match {
        case V.scala3 =>
          Agg[Dep]()
        case V.scala213 =>
          Agg(
            ivy"org.typelevel:::kind-projector:0.13.2"
          )
      }

    object test extends Tests {
      override def ivyDeps          = Agg(zioTest, zioTestSbt)
      override def testFramework    = "zio.test.sbt.ZTestFramework"
      override def allScalacOptions = T(super.allScalacOptions().distinct)
    }
  }

//  object js extends Cross[JsModule](V.scalaAll: _*)
//  class JsModule(val crossScalaVersion: String) extends ScalaJSModule with DecrelModuleBase {
//    override def millSourcePath = outer.millSourcePath
//    override def ivyDeps        = outer.ivyDeps
//    override def scalaJSVersion = V.scalaJS
//    override def moduleKind     = T(ModuleKind.CommonJSModule)
//    override def jsEnvConfig    = T(JsEnvConfig.JsDom())
//
//    override def scalaJSJsEnvIvyDeps: T[Agg[Dep]] = T {
//      val result = super.scalaJSJsEnvIvyDeps() ++
//        Agg(ivy"org.scala-js::scalajs-env-jsdom-nodejs::1.1.0")
//      println(result)
//      result
//    }
//
//    object test extends ScalaJSModuleTests with Tests {
//
//      override def allScalacOptions = T(super.allScalacOptions().distinct)
//    }
//  }
}

//trait DecrelModuleBase extends CrossModuleBase {
//
//}
