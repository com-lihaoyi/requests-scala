import mill._
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}
import scalalib._

val dottyVersion = Option(sys.props("dottyVersion"))

object requests extends Cross[RequestsModule]((List("2.12.6", "2.13.0", "3.0.0-RC1") ++ dottyVersion): _*)
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
    ivy"com.lihaoyi::geny::0.6.5"
  )
  object test extends Tests{
    def ivyDeps = Agg(
      ivy"com.lihaoyi::utest::0.7.7",
      ivy"com.lihaoyi::ujson::1.2.3"
    )
    def testFrameworks = Seq("utest.runner.Framework")
  }
  // FIXME: scaladoc 3 is not supported by mill yet. Remove the override
  // once it is.
  override def docJar =
    if (crossScalaVersion.startsWith("2")) super.docJar
    else T {
      val outDir = T.ctx().dest
      val javadocDir = outDir / 'javadoc
      os.makeDir.all(javadocDir)
      mill.api.Result.Success(mill.modules.Jvm.createJar(Agg(javadocDir))(outDir))
    }
}
