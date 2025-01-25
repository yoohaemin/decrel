addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "1.3.2")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"                   % "1.18.2")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.5.6")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"                  % "2.5.2")
addSbtPlugin("com.github.sbt"     % "sbt-ci-release"                % "1.9.0")
addSbtPlugin("com.eed3si9n"       % "sbt-buildinfo"                 % "0.13.1")
addSbtPlugin("org.scalameta"      % "sbt-mdoc"                      % "2.6.1")
addSbtPlugin("com.github.sbt"     % "sbt-github-actions"            % "0.24.0")
addSbtPlugin("de.heikoseeberger"  % "sbt-header"                    % "5.10.0")
addSbtPlugin("io.chrisdavenport"  % "sbt-no-publish"                % "0.1.0")

ThisBuild / libraryDependencySchemes ++= Vector(
  "org.scala-native" % "sbt-scala-native" % VersionScheme.Always
)
