addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "1.2.0")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.2.0")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"                   % "1.13.0")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.4.8")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"                  % "2.4.6")
addSbtPlugin("com.github.sbt"     % "sbt-ci-release"                % "1.5.11")
addSbtPlugin("com.eed3si9n"       % "sbt-buildinfo"                 % "0.11.0")
addSbtPlugin("org.scalameta"      % "sbt-mdoc"                      % "2.3.7")
addSbtPlugin("com.codecommit"     % "sbt-github-actions"            % "0.14.2")
addSbtPlugin("de.heikoseeberger"  % "sbt-header"                    % "5.9.0")
addSbtPlugin("io.chrisdavenport"  % "sbt-no-publish"                % "0.1.0")

ThisBuild / libraryDependencySchemes ++= Vector(
  "org.scala-native" % "sbt-scala-native" % VersionScheme.Always
)
