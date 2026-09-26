addSbtPlugin("com.eed3si9n"       % "sbt-projectmatrix"             % "0.11.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "1.4.0")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.4.0")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"                   % "1.22.0")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.5.12")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"                  % "2.6.2")
addSbtPlugin("com.github.sbt"     % "sbt-ci-release"                % "1.12.1")
addSbtPlugin("com.eed3si9n"       % "sbt-buildinfo"                 % "0.13.2")
addSbtPlugin("org.scalameta"      % "sbt-mdoc"                      % "2.9.2")
addSbtPlugin("com.github.sbt"     % "sbt-github-actions"            % "0.32.1")
addSbtPlugin("de.heikoseeberger"  % "sbt-header"                    % "5.10.0")
addSbtPlugin("io.chrisdavenport"  % "sbt-no-publish"                % "0.1.0")

ThisBuild / libraryDependencySchemes ++= Vector(
  "org.scala-native" % "sbt-scala-native" % VersionScheme.Always
)
