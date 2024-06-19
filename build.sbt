ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.12.19"

lazy val cats = Seq(
  "org.typelevel" %% "cats-core" % "2.12.0",
  "org.typelevel" %% "cats-effect" % "3.4.5",
)

lazy val telegram = Seq(
  "io.github.apimorphism" %% "telegramium-core" % "8.74.0",
  "io.github.apimorphism" %% "telegramium-high" % "8.74.0"
)

val circe = Seq(
  "io.circe" %% "circe-core"   % "0.14.7",
  "io.circe" %% "circe-parser" % "0.14.7",
  "io.circe" %% "circe-generic" % "0.14.7"
)

lazy val http4s: Seq[ModuleID] = Seq(
  "org.http4s" %% "http4s-client" % "0.23.18",
  "org.http4s" %% "http4s-dsl" % "0.23.18",
  "org.http4s" %% "http4s-ember-server" % "0.23.18",
  "org.http4s" %% "http4s-ember-client" % "0.23.18",
)

lazy val root = (project in file("."))
  .aggregate(
    backend,
    dao,
    api
  )
  .settings(
    name := "new-bot"
  )

lazy val dao = (project in file("dao"))
  .settings(
    libraryDependencies += "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC1",
    libraryDependencies += "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC1",
    libraryDependencies += "org.tpolecat" %% "doobie-core" % "1.0.0-RC1",
    libraryDependencies += "org.tpolecat" %% "doobie-postgres-circe" % "1.0.0-RC1",
    libraryDependencies += "com.zaxxer" % "HikariCP" % "5.1.0",
    libraryDependencies ++= circe
  ).dependsOn(api)

lazy val backend = (project in file("backend"))
  .settings(
    libraryDependencies ++= cats,
    libraryDependencies ++= telegram,
    libraryDependencies ++= http4s,
    libraryDependencies ++= circe
  ).dependsOn(api)

lazy val api = (project in file("api"))
  .settings(
    libraryDependencies ++= cats
  )

lazy val main = (project in file("main"))
  .settings(
    libraryDependencies ++= cats
  ).dependsOn(api, backend, dao)
