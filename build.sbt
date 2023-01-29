inThisBuild(
  List(
    scalaVersion := "3.3.0-RC2"
  )
)

name := "decrel"

lazy val root = project
  .in(file("."))
  .aggregate(
    coreJVM,
    coreJS
  )
  .settings(
    crossScalaVersions := Nil
  )

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(name := "decrel-core")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++=
      Seq(
        "dev.zio" %%% "izumi-reflect" % V.izumiReflect,
        "dev.zio" %%% "zio-test"      % V.zio % Test,
        "dev.zio" %%% "zio-test-sbt"  % V.zio % Test
      )
  )

lazy val coreJVM = core.jvm
lazy val coreJS  = core.js

lazy val commonSettings = Def.settings(
  scalacOptions ++= Seq(
    "-no-indent",
    "-old-syntax",
    "-Ykind-projector",
    "-Xprint:typer"
  )
)

lazy val V = new {
  val scala3   = "3.2.1"

  val zio          = "2.0.5"
  val izumiReflect = "2.2.2"
}
