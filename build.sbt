import sbt.Compile
import sbt.Keys.libraryDependencies

val doobieVersion = "0.12.1"

val core =
  project.in(file("core"))
    .settings(commonSettings)
    .settings(
      libraryDependencies ++= Seq(
        "org.typelevel" %% "cats-tagless-macros" % "0.12",
        "com.github.julien-truffaut" %% "monocle-core" % "3.0.0-M3"
      ),

    )

def commonSettings = Seq(
  scalaVersion := "2.13.5",
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, n)) if n >= 13 => "-Ymacro-annotations" :: Nil
      case _ => Nil
    }
  },
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, n)) if n >= 13 => Nil
      case _ => compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full) :: Nil
    }
  },
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.11.3" cross CrossVersion.full)
)


val example =
  project.in(file("example"))
    .settings(commonSettings)
    .settings(
      scalacOptions += "-Ymacro-annotations",
      libraryDependencies ++= Seq(
        "org.tpolecat"      %% "doobie-core"            % doobieVersion,
        "org.tpolecat"      %% "doobie-postgres"        % doobieVersion,
        "org.tpolecat"      %% "doobie-hikari"          % doobieVersion,
        "com.github.julien-truffaut" %% "monocle-macro" % "3.0.0-M3",
        "org.testcontainers" % "testcontainers" % "1.15.2" % Test,
        "com.dimafeng" %% "testcontainers-scala" % "0.39.3" % Test,
        "com.spotify" %% "magnolify-scalacheck" % "0.4.3" % Test,
        "com.softwaremill.diffx" %% "diffx-specs2" % "0.4.4" % Test,
        "org.specs2" %% "specs2-scalacheck" % "4.10.6" % Test,
        "com.codecommit" %% "cats-effect-testing-specs2" % "0.5.2" % Test

      )
    )
    .dependsOn(core)