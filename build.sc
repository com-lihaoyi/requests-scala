import mill._
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}
import scalalib._

val dottyVersion = Option(sys.props("dottyVersion"))

object requests extends Cross[RequestsModule]((List("2.12.6", "2.13.0", "0.27.0-RC1") ++ dottyVersion): _*)
class RequestsModule(val crossScalaVersion: String) extends CrossScalaModule with PublishModule {
  def publishVersion = "0.6.5"
  def artifactName = "requests"
  def pomSettings = PomSettings(
    description = "Scala port of the popular Python Requests HTTP client",
    organization = "com.lihaoyi",
    url = "https://github.com/lihaoyi/requests",
    licenses = Seq(License.MIT),
    versionControl = VersionControl.github("lihaoyi", "requests-scala"),
    developers = Seq(
      Developer("lihaoyi", "Li Haoyi","https://github.com/lihaoyi")
    )
  )
  def ivyDeps = Agg(
    ivy"com.lihaoyi::geny::0.6.2"
  )
  object test extends Tests{
    def ivyDeps = Agg(
      ivy"com.lihaoyi::utest::0.7.4",
      ivy"com.lihaoyi::ujson::1.1.0"
    )
    def testFrameworks = Seq("utest.runner.Framework")
  }
}
