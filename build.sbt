val projectName = "lib-persistence-scala"

val settings = Seq(
  organization  := "com.artemistechnica",
  name          := projectName,
  version       := "0.1.0-SNAPSHOT",
  description   := "Database shim.",
  scalaVersion  := "2.13.3",

  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-language:higherKinds",
    "-language:implicitConversions",
//    "-Xfatal-warnings", // Reactivemongo's Macros.handler call is raising a deprecation warning
    "-Xlint:adapted-args",
    "-Wunused:imports",
    "-Ywarn-dead-code",
    "-Ywarn-value-discard",
  ),

  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases")
  ),

  libraryDependencies ++= Seq(
    "org.typelevel"       %%  "cats-core"                 % "2.0.0",
    "org.typelevel"       %%  "cats-free"                 % "2.0.0",
    "com.typesafe.slick"  %%  "slick"                     % "3.3.2",
    "com.typesafe.slick"  %%  "slick-hikaricp"            % "3.3.2",
    "com.github.tminglei" %%  "slick-pg"                  % "0.18.0",
    "com.github.tminglei" %%  "slick-pg_play-json"        % "0.18.0",
    "com.typesafe.akka"   %%  "akka-stream"               % "2.6.0",
    "org.postgresql"      %   "postgresql"                % "42.2.8",
    "org.reactivemongo"   %%  "reactivemongo"             % "0.20.11",
    "org.reactivemongo"   %%  "reactivemongo-akkastream"  % "0.20.11",
    "net.debasishg"       %%  "redisclient"               % "3.30",
    "org.scala-stm"       %%  "scala-stm"                 % "0.9.1",
    "org.scalatest"       %%  "scalatest"                 % "3.0.8"     % Test
  )
)

val testing = {
  Seq(
    testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oD")
  )
}

lazy val root = Project(projectName, file("."))
  .settings(settings)
  .settings(inConfig(Test)(testing))
  .settings(inConfig(Compile)(inTask(doc)(sources := Seq.empty) ++
    inTask(packageDoc)(publishArtifact := false)))
