import scala.collection.immutable._
import mill._, scalalib._
import D._

////// Modules //////////////////////////////////////////////////////

object core extends PureCrossModule {

  override def ivyDeps = Agg(
    izumiReflect
  )

}

////// Dependencies /////////////////////////////////////////////////

object D {
  def izumiReflect   = ivy"dev.zio::izumi-reflect::${V.izumiReflect}"
  def zio(m: String) = ivy"dev.zio::zio-$m::${V.zio}"
  def zioTest        = zio("test")
  def zioTestSbt     = zio("test-sbt")
  def kindProjector  = ivy"org.typelevel:::kind-projector:${V.kindProjector}"
}

object V {
  val scala213 = "2.13.10"
  val scala3   = "3.2.2"
  val scalaAll = scala213 :: scala3 :: Nil
  val scalaJS  = "1.12.0"

  def cats         = "2.9.0"
  def zio          = "2.0.6"
  def zioQuery     = "0.3.4"
  def fetch        = "3.1.0"
  def izumiReflect = "2.2.2"
  def scalacheck   = "1.17.0"
  def scalajsDom   = "2.3.0"

  def kindProjector = "0.13.2"
}

////// Module definition helpers //////////////////////////////////////

trait PureCrossModule extends Module { outer =>

  def ivyDeps: T[Agg[Dep]]

  object jvm extends Cross[JvmModule](V.scalaAll: _*)
  class JvmModule(val crossScalaVersion: String) extends DecrelModuleBase {
    override def millSourcePath = outer.millSourcePath
    override def ivyDeps        = outer.ivyDeps

    object test extends Tests
  }

  import scalajslib._, scalajslib.api._

  object js extends Cross[JsModule](V.scalaAll: _*)
  class JsModule(val crossScalaVersion: String) extends ScalaJSModule with DecrelModuleBase {
    override def millSourcePath = outer.millSourcePath
    override def ivyDeps        = outer.ivyDeps
    override def scalaJSVersion = V.scalaJS
    override def moduleKind     = T(ModuleKind.CommonJSModule)

    object test extends Tests with ScalaJSModuleTests {
      override def allScalacOptions = T(super.allScalacOptions().distinct)
    }
  }
}

trait DecrelModuleBase extends CrossSbtModule {

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
      case V.scala3   => Agg[Dep]()
      case V.scala213 => Agg(kindProjector)
    }

  trait Tests extends super.Tests {
    override def ivyDeps       = Agg(zioTest, zioTestSbt)
    override def testFramework = "zio.test.sbt.ZTestFramework"
  }

}
