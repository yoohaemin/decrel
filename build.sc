import $ivy.`com.yoohaemin::mill-mdoc::0.0.3`
import $ivy.`io.chris-kipp::mill-ci-release::0.1.9`
import mill._, scalalib._, scalafmt._, publish._, define.Command
import de.tobiasroeser.mill.vcs.version.VcsVersion
import de.wayofquality.mill.mdoc.MDocModule
import io.kipp.mill.ci.release.CiReleaseModule
import io.kipp.mill.ci.release.SonatypeHost

////// Modules //////////////////////////////////////////////////////

object core extends PureCrossModule {

  override def ivyDeps = Agg(
    D.izumiReflect
  )

}

object zquery extends PureCrossModule {

  override def moduleDeps = Seq(core)

  override def ivyDeps = Agg(
    D.zioQuery
  )

}

object fetch extends PureCrossModule {

  override def crossScalaVersionsJS: Seq[String] =
    List(V.scala213)

  override def moduleDeps = Seq(cats)

  override def ivyDeps = Agg(
    D.fetch
  )

}

object scalacheck extends PureCrossModule {

  override def moduleDeps = Seq(core)

  override def ivyDeps = Agg(
    D.scalacheck
  )

  override def testFramework = "org.scalacheck.ScalaCheckFramework"
}

object ziotest extends PureCrossModule {

  override def moduleDeps = Seq(core)

  override def ivyDeps = Agg(
    D.zioTest
  )

}

object cats extends PureCrossModule {

  override def moduleDeps = Seq(core)

  override def ivyDeps = Agg(
    D.cats
  )

}

object mdoc extends MDocModule {

  override def scalaVersion = V.scala213

  override def scalaMdocVersion = "2.3.7"

  override def millOuterCtx = super.millOuterCtx

  override def mdocSources = T.sources {
    T.workspace / "mdoc"
  }

  override def watchedMDocsDestination = T {
    Some(dest())
  }

  private def dest = T {
    T.workspace / "vuepress"
  }

  override def mdoc: T[PathRef] = T {
    val out = super.mdoc()
    os.copy.into(
      from = out.path / "docs",
      to = dest(),
      replaceExisting = true,
      createFolders = true,
      mergeFolders = true
    )
    PathRef(dest() / "docs")
  }

  override def mdocVariables: T[Map[String, String]] = T {
    val currentVcsState = VcsVersion.vcsState()
    val releaseVersion  = currentVcsState.stripV(currentVcsState.lastTag.get)
    val snapshotVersion = currentVcsState.format(dirtySep = "", dirtyHashDigits = 0) + "-SNAPSHOT"
    Map(
      "SNAPSHOTVERSION" ->
        (if (currentVcsState.commitsSinceLastTag == 0)
           releaseVersion
         else
           snapshotVersion),
      "RELEASEVERSION" -> releaseVersion
    )
  }
}

////// Dependencies /////////////////////////////////////////////////

object D {
  def izumiReflect   = ivy"dev.zio::izumi-reflect::${V.izumiReflect}"
  def zio(m: String) = ivy"dev.zio::zio-$m::${V.zio}"
  def zioTest        = zio("test")
  def zioTestSbt     = zio("test-sbt")
  def zioQuery       = ivy"dev.zio::zio-query::${V.zioQuery}"
  def fetch          = ivy"com.47deg::fetch::${V.fetch}"
  def scalacheck     = ivy"org.scalacheck::scalacheck::${V.scalacheck}"
  def cats           = ivy"org.typelevel::cats-core::${V.cats}"

  def kindProjector = ivy"org.typelevel:::kind-projector:${V.kindProjector}"
}

object V {
  val scala213 = "2.13.11"
  val scala3   = "3.3.0"
  val scalaAll = scala213 :: scala3 :: Nil
  val scalaJS  = "1.13.0"

  def cats         = "2.9.0"
  def zio          = "2.0.15"
  def zioQuery     = "0.5.0"
  def fetch        = "3.1.2"
  def izumiReflect = "2.3.8"
  def scalacheck   = "1.17.0"

  def kindProjector = "0.13.2"
}

////// Module definition helpers //////////////////////////////////////

trait PureCrossModule extends Module { outer =>

  def ivyDeps: T[Agg[Dep]]

  def moduleDeps: Seq[PureCrossModule] = Nil

  def testIvyDeps: T[Agg[Dep]] = Agg[Dep]()

  def crossScalaVersionsJVM: Seq[String] = V.scalaAll

  def crossScalaVersionsJS: Seq[String] = V.scalaAll

  def testFramework: String = "zio.test.sbt.ZTestFramework"

  object jvm extends Cross[JvmModule](crossScalaVersionsJVM: _*)
  class JvmModule(val crossScalaVersion: String) extends DecrelModuleBase { inner =>
    override def millSourcePath = outer.millSourcePath
    override def ivyDeps        = outer.ivyDeps

    override def moduleDeps: Seq[PublishModule] =
      (outer.moduleDeps.map(_.jvm(crossScalaVersion)))

    object test extends Tests with DecrelModuleActionBase {
      override def crossScalaVersion: String = inner.crossScalaVersion
      override def skipIdea: Boolean         = super.skipIdea

      override def ivyDeps: T[Agg[Dep]] = T {
        Agg(D.zioTest, D.zioTestSbt) ++ testIvyDeps() ++ inner.ivyDeps()
      }

      override def allScalacOptions = T {
        super.allScalacOptions().distinct
      }

      override def testFramework: T[String] =
        outer.testFramework
    }
  }

  import scalajslib._, scalajslib.api._

  object js extends Cross[JsModule](crossScalaVersionsJS: _*)
  class JsModule(val crossScalaVersion: String) extends DecrelModuleBase with ScalaJSModule {
    inner =>
    override def millSourcePath = outer.millSourcePath
    override def ivyDeps        = outer.ivyDeps
    override def scalaJSVersion = V.scalaJS
    override def moduleKind     = T(ModuleKind.CommonJSModule)

    override def moduleDeps: Seq[PublishModule] =
      outer.moduleDeps.map(_.js(crossScalaVersion))

    override def scalacPluginIvyDeps = super.scalacPluginIvyDeps()

    object test extends Tests with TestScalaJSModule with DecrelModuleActionBase {
      override def crossScalaVersion: String = inner.crossScalaVersion
      override def skipIdea: Boolean         = super.skipIdea

      override def ivyDeps: T[Agg[Dep]] = T {
        Agg(D.zioTest, D.zioTestSbt) ++ testIvyDeps() ++ inner.ivyDeps()
      }

      override def scalacPluginIvyDeps =
        inner.scalacPluginIvyDeps() ++ super.scalacPluginIvyDeps()

      override def allScalacOptions = T {
        (inner.allScalacOptions() ++ super.allScalacOptions()).distinct
      }

      override def testFramework: T[String] =
        outer.testFramework
    }
  }
}

trait DecrelModuleBase
    extends ScalafmtModule
    with CrossScalaModule
    with CiReleaseModule
    with DecrelModuleActionBase {

  override def skipIdea: Boolean = DecrelModuleBase.super.skipIdea

  override def checkFormat() =
    if (runActions)
      super.checkFormat()
    else
      T.command(mill.api.Result.Success(()))

  override def reformat() =
    if (runActions)
      super.reformat()
    else
      T.command(mill.api.Result.Success(()))

  private val scalacOptionsCommon = List(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:_",
    "-unchecked",
    "-Xfatal-warnings"
  )

  private val scalacOptions213 = scalacOptionsCommon ::: List(
    "-Xsource:3",
    "-Xlint:-byname-implicit",
    "-explaintypes",
    "-Vimplicits",
    "-Vtype-diffs",
    "-P:kind-projector:underscore-placeholders"
  )

  private val scalacOptions3 = scalacOptionsCommon ::: List(
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
      case V.scala213 => Agg(D.kindProjector)
    }

  override def sonatypeHost = Some(SonatypeHost.s01)

  override def artifactName = T {
    ("decrel" :: millModuleSegments.parts.dropRight(2)).mkString("-")
  }

  override def pomSettings: T[PomSettings] = T {
    PomSettings(
      description = "Composable Relations for Scala",
      organization = "com.yoohaemin",
      url = "https://github.com/yoohaemin/decrel",
      licenses = List(License.`MPL-2.0`),
      versionControl = VersionControl(
        browsableRepository = Some("https://github.com/yoohaemin/decrel"),
        connection = Some("scm:git:git://github.com/yoohaemin/decrel.git"),
        developerConnection = Some("scm:git:git@github.com:yoohaemin/decrel.git"),
        tag = Some(VcsVersion.vcsState().currentRevision)
      ),
      developers = List(
        Developer(
          id = "yoohaemin",
          name = "Haemin Yoo",
          url = "https://yoohaemin.com/"
        )
      ),
      packaging = "jar"
    )
  }

}

trait DecrelModuleActionBase {

  def crossScalaVersion: String

  protected final val is213 = crossScalaVersion == V.scala213

  protected final val isJs = this.isInstanceOf[scalajslib.ScalaJSModule]

  protected def runActions = is213 && !isJs

  def skipIdea: Boolean = !runActions

}
