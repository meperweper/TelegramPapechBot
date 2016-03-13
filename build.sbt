name := "TelegramPapechBot"

version := "1.0"

scalaVersion := "2.11.7"

resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"
resolvers += Resolver
  .url("Typesafe Ivy Snapshots Repository", url("https://repo.typesafe.com/typesafe/ivy-snapshots"))(Resolver
  .ivyStylePatterns)

libraryDependencies += "org.http4s" %% "http4s-blaze-client" % "0.9.2"  // to use the blaze client
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11"
)
libraryDependencies += ("org.scala-stm" %% "scala-stm" % "0.7")
libraryDependencies += ("org.json4s" %% "json4s-jackson" % "3.2.11")
libraryDependencies += ("org.json4s" %% "json4s-native" % "3.2.11")