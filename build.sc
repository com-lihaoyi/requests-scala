import mill._
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}
import scalalib._
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.0`
import $ivy.`com.github.lolgab::mill-mima::0.0.23`

import de.tobiasroeser.mill.vcs.version.VcsVersion
import com.github.lolgab.mill.mima._

val dottyVersion = sys.props.get("dottyVersion")

val scalaVersions = List("2.12.17", "2.13.14", "2.11.12", "3.1.1") ++ dottyVersion

object requests extends Cross[RequestsModule](scalaVersions)
trait RequestsModule extends CrossScalaModule with PublishModule with Mima {
  def publishVersion = VcsVersion.vcsState().format()
  def mimaPreviousVersions =
    (
      Seq("0.7.0", "0.7.1", "0.8.2") ++
      Option.when(VcsVersion.vcsState().commitsSinceLastTag != 0)(VcsVersion.vcsState().lastTag).flatten
    ).distinct
  override def mimaBinaryIssueFilters = Seq(
    ProblemFilter.exclude[ReversedMissingMethodProblem]("requests.BaseSession.send"),
    ProblemFilter.exclude[DirectMissingMethodProblem]("requests.Response.string")
  )

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

  def ivyDeps = Agg(ivy"com.lihaoyi::geny::1.0.0")

  object test extends ScalaTests with TestModule.Utest {
    def ivyDeps = Agg(
      ivy"com.lihaoyi::utest::0.7.10",
      ivy"com.lihaoyi::ujson::1.3.13"
    )
  }
}
