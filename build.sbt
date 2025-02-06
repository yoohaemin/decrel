inThisBuild(
  List(
    scalaVersion             := V.scala213,
    crossScalaVersions       := V.scalaAll,
    organization             := "com.yoohaemin",
    homepage                 := Some(url("https://github.com/yoohaemin/decrel")),
    licenses                 := List("MPL-2.0" -> url("https://www.mozilla.org/MPL/2.0/")),
    Test / parallelExecution := true,
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/yoohaemin/decrel/"),
        "scm:git:git@github.com:yoohaemin/decrel.git"
      )
    ),
    developers := List(
      Developer(
        "yoohaemin",
        "Haemin Yoo",
        "haemin@zzz.pe.kr",
        url("https://github.com/yoohaemin")
      )
    ),
    ConsoleHelper.welcomeMessage,
    versionScheme    := Some("early-semver"),
    organizationName := "Haemin Yoo",
    startYear        := Some(2022)
  ) ::: ciSettings
)

name := "decrel"
addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias(
  "fmtCheck",
  "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck"
)

//TODO uncomment native crossbuilds when ZIO published against native 0.4.8+ is out
//Related: https://github.com/scala-native/scala-native/issues/2858
lazy val root = project
  .in(file("."))
  .aggregate(
    coreJVM,
    coreJS,
    // coreNative,
    zqueryJVM,
    zqueryJS,
    fetchJVM,
    fetchJS,
    ziotestJVM,
    ziotestJS,
    // ziotestNative,
    scalacheckJVM,
    scalacheckJS,
    // scalacheckNative,
    catsJVM,
    catsJS,
    // catsNative,
    docs
  )
  .settings(
    crossScalaVersions := Nil
  )
  .enablePlugins(NoPublishPlugin)

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

///////////////////////// Haxl based datatypes

lazy val zquery = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("zquery"))
  .enablePlugins(BuildInfoPlugin)
  .settings(name := "decrel-zquery")
  .settings(commonSettings)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      "scalaPartialVersion" -> CrossVersion.partialVersion(scalaVersion.value)
    ),
    buildInfoPackage := "decrel.zquery",
    buildInfoObject  := "BuildInfo"
  )
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-query"    % V.zioQuery,
      "dev.zio" %%% "zio-test"     % V.zio % Test,
      "dev.zio" %%% "zio-test-sbt" % V.zio % Test
    )
  )
  .dependsOn(core)

lazy val zqueryJVM = zquery.jvm
lazy val zqueryJS  = zquery.js

lazy val fetch = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .withoutSuffixFor(JVMPlatform)
  .in(file("fetch"))
  .enablePlugins(BuildInfoPlugin)
  .settings(name := "decrel-fetch")
  .settings(commonSettings)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      "scalaPartialVersion" -> CrossVersion.partialVersion(scalaVersion.value)
    ),
    buildInfoPackage := "decrel.fetch",
    buildInfoObject  := "BuildInfo"
  )
  .settings(
    libraryDependencies ++= Seq(
      "com.47deg" %%% "fetch" % V.fetch
    )
  )
  .dependsOn(cats)

lazy val fetchJVM = fetch.jvm.settings(crossScalaVersions := V.scalaAll)
lazy val fetchJS  = fetch.js.settings(crossScalaVersions := List(V.scala213))

///////////////////////// Generator datatypes

lazy val ziotest = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("ziotest"))
  .enablePlugins(BuildInfoPlugin)
  .settings(name := "decrel-ziotest")
  .settings(commonSettings)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      "scalaPartialVersion" -> CrossVersion.partialVersion(scalaVersion.value)
    ),
    buildInfoPackage := "decrel.ziotest",
    buildInfoObject  := "BuildInfo"
  )
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % V.zio,
      "dev.zio" %%% "zio-test-sbt" % V.zio % Test
    )
  )
  .dependsOn(core)

lazy val ziotestJVM = ziotest.jvm
lazy val ziotestJS  = ziotest.js

lazy val scalacheck = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("scalacheck"))
  .enablePlugins(BuildInfoPlugin)
  .settings(name := "decrel-scalacheck")
  .settings(commonSettings)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      "scalaPartialVersion" -> CrossVersion.partialVersion(scalaVersion.value)
    ),
    buildInfoPackage := "decrel.scalacheck",
    buildInfoObject  := "BuildInfo"
  )
  .settings(
    libraryDependencies ++= Seq(
      "org.scalacheck" %%% "scalacheck" % V.scalacheck
    )
  )
  .dependsOn(core)

lazy val scalacheckJVM = scalacheck.jvm
lazy val scalacheckJS  = scalacheck.js

///////////////////////// General purpose datatypes

lazy val cats = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("cats"))
  .enablePlugins(BuildInfoPlugin)
  .settings(name := "decrel-cats")
  .settings(commonSettings)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      "scalaPartialVersion" -> CrossVersion.partialVersion(scalaVersion.value)
    ),
    buildInfoPackage := "decrel.cats",
    buildInfoObject  := "BuildInfo"
  )
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % V.cats
    )
  )
  .dependsOn(core)

lazy val catsJVM = cats.jvm
lazy val catsJS  = cats.js

///////////////////////// docs

lazy val jsdocs = project
  .settings(
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % V.scalajsDom,
    crossScalaVersions                     := List(V.scala213)
  )
  .dependsOn(coreJS, zqueryJS, fetchJS, ziotestJS, scalacheckJS)
  .enablePlugins(ScalaJSPlugin)
  .enablePlugins(NoPublishPlugin)

lazy val docs = project
  .in(file("mdoc"))
  .enablePlugins(MdocPlugin)
  .settings(commonSettings)
  .settings(
    name       := "decrel-docs",
    moduleName := name.value,
    mdocIn     := (ThisBuild / baseDirectory).value / "mdoc" / "docs",
    mdocOut    := (ThisBuild / baseDirectory).value / "vuepress" / "docs",
    run / fork := false,
    scalacOptions -= "-Xfatal-warnings",
    mdocJS             := Some(jsdocs),
    crossScalaVersions := List(V.scala213),
    mdocVariables := Map(
      "SNAPSHOTVERSION" -> version.value,
      "RELEASEVERSION"  -> version.value.takeWhile(_ != '+')
    )
  )
  .dependsOn(coreJVM, zqueryJVM, fetchJVM, ziotestJVM, scalacheckJVM)
  .enablePlugins(NoPublishPlugin)

lazy val commonSettings = Def.settings(
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:higherKinds",
    "-language:existentials",
    "-unchecked",
    "-Xfatal-warnings"
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 13)) =>
      Seq(
        "-Xsource:3",
        "-Xlint:-byname-implicit",
        "-explaintypes",
        "-Vimplicits",
        "-Vtype-diffs",
        "-P:kind-projector:underscore-placeholders"
      )
    case Some((3, _)) =>
      Seq(
        "-no-indent",
        "-Ykind-projector"
      )
    case _ => Nil
  }),
  Test / fork := false,
  run / fork  := true,
  libraryDependencies ++= {
    if (scalaVersion.value.startsWith("2.13"))
      List(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.3" cross CrossVersion.full))
    else
      Nil
  }
)

lazy val V = new {
  val scala213 = "2.13.15"
  val scala3   = "3.3.4"
  val scalaAll = scala213 :: scala3 :: Nil

  val cats         = "2.12.0"
  val zio          = "2.1.15"
  val zioQuery     = "0.7.6"
  val fetch        = "3.1.2"
  val izumiReflect = "2.3.10"
  val scalacheck   = "1.18.1"
  val scalajsDom   = "2.3.0"
}

lazy val ciSettings = List(
  githubWorkflowPublishTargetBranches := List(RefPredicate.Equals(Ref.Branch("master"))),
  githubWorkflowJavaVersions          := Seq(JavaSpec.temurin("11")),
  githubWorkflowUseSbtThinClient      := false,
  githubWorkflowBuild                 := Seq(WorkflowStep.Sbt(List("++${{ matrix.scala }} test"))),
  githubWorkflowPublishTargetBranches += RefPredicate.StartsWith(Ref.Tag("v")),
  githubWorkflowTargetTags ++= Seq("v*"),
  githubWorkflowPublish := Seq(
    WorkflowStep.Sbt(
      List("ci-release"),
      env = Map(
        "PGP_PASSPHRASE"    -> "${{ secrets.PGP_PASSPHRASE }}",
        "PGP_SECRET"        -> "${{ secrets.PGP_SECRET }}",
        "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
        "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
      )
    )
  ),
  githubWorkflowGeneratedUploadSteps := {
    val skipCache = List("fetch/.js", "jsdocs", "mdoc")

    githubWorkflowGeneratedUploadSteps.value match {
      case (run: WorkflowStep.Run) :: t if run.commands.head.startsWith("tar cf") =>
        assert(run.commands.length == 1)
        run.copy(
          commands = List(
            skipCache.foldLeft(run.commands.head) { (acc, v) =>
              acc.replace(s"$v/target", "")
            }
          )
        ) :: t
      case l => l
    }
  },
  sonatypeCredentialHost := "s01.oss.sonatype.org",
  sonatypeRepository     := "https://s01.oss.sonatype.org/service/local"
)
