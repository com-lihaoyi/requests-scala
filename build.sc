import mill._
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}
import scalalib._
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.1.4`
import de.tobiasroeser.mill.vcs.version.VcsVersion
import $ivy.`com.github.lolgab::mill-mima::0.0.10`
import com.github.lolgab.mill.mima._

val dottyVersion = Option(sys.props("dottyVersion"))

object requests extends Cross[RequestsModule]((List("2.12.13", "2.13.5", "2.11.12", "3.0.0") ++ dottyVersion): _*)
class RequestsModule(val crossScalaVersion: String) extends CrossScalaModule with PublishModule with Mima {
  def publishVersion = VcsVersion.vcsState().format()
  def mimaPreviousVersions = Seq("0.7.0") ++ VcsVersion.vcsState().lastTag.toSeq
  override def mimaBinaryIssueFilters = Seq(
    ProblemFilter.exclude[ReversedMissingMethodProblem]("requests.BaseSession.send")
  )
  def artifactName = "requests"
  def pomSettings = PomSettings(
    description = "Scala port of the popular Python Requests HTTP client",
    organization = "com.lihaoyi",
    url = "https://github.com/com-lihaoyi/requests-scala",
    licenses = Seq(License.MIT),
    versionControl = VersionControl.github("com-lihaoyi", "requests-scala"),
    developers = Seq(
      Developer("lihaoyi", "Li Haoyi","https://github.com/lihaoyi")
    )
  )
  def ivyDeps = Agg(
    ivy"com.lihaoyi::geny::0.6.11"
  )
  object test extends Tests with TestModule.Utest {
    def ivyDeps = Agg(
      ivy"com.lihaoyi::utest::0.7.10",
      ivy"com.lihaoyi::ujson::1.3.13"
    )
  }
}
