inThisBuild(
  List(
    scalaVersion             := V.scala3LTS,
    crossScalaVersions       := V.scalaAll,
    organization             := "com.yoohaemin",
    homepage                 := Some(url("https://github.com/yoohaemin/decrel")),
    licenses                 := List("MPL-2.0" -> url("https://www.mozilla.org/MPL/2.0/")),
    Test / parallelExecution := true,
    scmInfo                  := Some(
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
lazy val stableScalaVersions                   = Seq(V.scala213, V.scala3LTS)
lazy val nextScalaVersions                     = Seq(V.scala3Next)
lazy val rootAggregates: Seq[ProjectReference] =
  core.projectRefs ++
    kyo.projectRefs ++
    kyoBatch.projectRefs ++
    zquery.projectRefs ++
    zqueryNext.projectRefs ++
    fetch.projectRefs ++
    ziotest.projectRefs ++
    scalacheck.projectRefs ++
    cats.projectRefs ++
    Seq(LocalProject("docs"))

lazy val root = project
  .in(file("."))
  .aggregate(rootAggregates: _*)
  .settings(
    crossScalaVersions := Nil
  )
  .enablePlugins(NoPublishPlugin)

lazy val core = (projectMatrix in file("core"))
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
  .jvmPlatform(scalaVersions = stableScalaVersions)
  .jsPlatform(scalaVersions = stableScalaVersions)

///////////////////////// Haxl based datatypes

lazy val zquery = (projectMatrix in file("zquery"))
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
  .jvmPlatform(scalaVersions = stableScalaVersions)
  .jsPlatform(scalaVersions = stableScalaVersions)

lazy val zqueryNext = (projectMatrix in file("zquery-next"))
  .settings(name := "decrel-zquery-next")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % V.zio % Test,
      "dev.zio" %%% "zio-test-sbt" % V.zio % Test
    )
  )
  .jvmPlatform(
    scalaVersions = nextScalaVersions,
    axisValues = Nil,
    configure = _.dependsOn(zquery.jvm(V.scala3LTS))
  )
  .jsPlatform(
    scalaVersions = nextScalaVersions,
    axisValues = Nil,
    configure = _.dependsOn(zquery.js(V.scala3LTS))
  )

lazy val fetch = (projectMatrix in file("fetch"))
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
  .jvmPlatform(scalaVersions = stableScalaVersions)
  .jsPlatform(scalaVersions = Seq(V.scala213))

lazy val kyoBatch = (projectMatrix in file("kyo-batch"))
  .enablePlugins(BuildInfoPlugin)
  .settings(name := "decrel-kyo-batch")
  .settings(commonSettings)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      "scalaPartialVersion" -> CrossVersion.partialVersion(scalaVersion.value)
    ),
    buildInfoPackage := "decrel.kyo.batch",
    buildInfoObject  := "BuildInfo"
  )
  .settings(
    libraryDependencies ++= Seq(
      "io.getkyo" %%% "kyo-prelude" % V.kyo
    )
  )
  .dependsOn(kyo)
  .jvmPlatform(scalaVersions = nextScalaVersions)
  .jsPlatform(scalaVersions = nextScalaVersions)

///////////////////////// Generator datatypes

lazy val ziotest = (projectMatrix in file("ziotest"))
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
  .jvmPlatform(scalaVersions = stableScalaVersions)
  .jsPlatform(scalaVersions = stableScalaVersions)

lazy val scalacheck = (projectMatrix in file("scalacheck"))
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
  .jvmPlatform(scalaVersions = stableScalaVersions)
  .jsPlatform(scalaVersions = stableScalaVersions)

///////////////////////// General purpose datatypes

lazy val cats = (projectMatrix in file("cats"))
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
  .jvmPlatform(scalaVersions = stableScalaVersions)
  .jsPlatform(scalaVersions = stableScalaVersions)

lazy val kyo = (projectMatrix in file("kyo"))
  .enablePlugins(BuildInfoPlugin)
  .settings(name := "decrel-kyo")
  .settings(commonSettings)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      "scalaPartialVersion" -> CrossVersion.partialVersion(scalaVersion.value)
    ),
    buildInfoPackage := "decrel.kyo",
    buildInfoObject  := "BuildInfo"
  )
  .settings(
    libraryDependencies ++= Seq(
      "io.getkyo" %%% "kyo-prelude" % V.kyo
    )
  )
  .jvmPlatform(
    scalaVersions = nextScalaVersions,
    axisValues = Nil,
    configure = _.dependsOn(core.jvm(V.scala3LTS))
  )
  .jsPlatform(
    scalaVersions = nextScalaVersions,
    axisValues = Nil,
    configure = _.dependsOn(core.js(V.scala3LTS))
  )

///////////////////////// docs

lazy val jsdocs = project
  .settings(
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % V.scalajsDom,
    scalaVersion                           := V.scala213,
    crossScalaVersions                     := List(V.scala213)
  )
  .dependsOn(
    core.js(V.scala213),
    zquery.js(V.scala213),
    fetch.js(V.scala213),
    ziotest.js(V.scala213),
    scalacheck.js(V.scala213)
  )
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
    scalaVersion       := V.scala213,
    crossScalaVersions := List(V.scala213),
    mdocVariables      := Map(
      "SNAPSHOTVERSION" -> version.value,
      "RELEASEVERSION"  -> version.value.takeWhile(_ != '+')
    )
  )
  .dependsOn(
    core.jvm(V.scala213),
    zquery.jvm(V.scala213),
    fetch.jvm(V.scala213),
    ziotest.jvm(V.scala213),
    scalacheck.jvm(V.scala213)
  )
  .enablePlugins(NoPublishPlugin)

lazy val commonSettings = Def.settings(
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:higherKinds",
    "-language:existentials",
    "-unchecked"
  ) ++ (scalaVersion.value match {
    case V.scala213 =>
      Seq(
        "-Xsource:3",
        "-Xlint:-byname-implicit",
        "-explaintypes",
        "-Vimplicits",
        "-Vtype-diffs",
        "-P:kind-projector:underscore-placeholders",
        "-Xfatal-warnings"
      )
    case V.scala3LTS =>
      Seq(
        "-no-indent",
        "-Ykind-projector",
        "-Xfatal-warnings"
      )
    case V.scala3Next =>
      Seq(
        "-no-indent",
        "-Xkind-projector",
        "-Werror"
      )
  }),
  Test / fork := false,
  run / fork  := true,
  libraryDependencies ++= {
    if (scalaVersion.value.startsWith("2.13"))
      List(
        compilerPlugin("org.typelevel" % "kind-projector"     % "0.13.4" cross CrossVersion.full),
        compilerPlugin("com.olegpy"   %% "better-monadic-for" % "0.3.1")
      )
    else
      Nil
  }
)

lazy val V = new {
  val scala213   = "2.13.18"
  val scala3LTS  = "3.3.7"
  val scala3Next = "3.8.2"
  val scalaAll   = scala213 :: scala3LTS :: scala3Next :: Nil

  val cats         = "2.13.0"
  val kyo          = "0.19.0"
  val zio          = "2.1.24"
  val zioQuery     = "0.7.7"
  val fetch        = "3.2.1"
  val izumiReflect = "3.0.9"
  val scalacheck   = "1.19.0"
  val scalajsDom   = "2.4.0"
}

lazy val ciSettings = List(
  githubWorkflowPublishTargetBranches := List(RefPredicate.Equals(Ref.Branch("master"))),
  githubWorkflowJavaVersions          := Seq(JavaSpec.zulu("17")),
  githubWorkflowUseSbtThinClient      := false,
  // Avoid flaky GitHub Actions artifact handoffs between build and publish jobs.
  githubWorkflowArtifactUpload := false,
  githubWorkflowBuild          := Seq(WorkflowStep.Sbt(List("++${{ matrix.scala }} test"))),
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
    val skipCache =
      List("fetch/.js", "jsdocs", "mdoc", "kyo/.js", "kyo/.jvm", "kyo-batch/.js", "kyo-batch/.jvm")

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
  }
)
