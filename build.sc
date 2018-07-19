import mill._, scalalib._

object requests extends ScalaModule {
  def scalaVersion = "2.12.6"
  object test extends Tests{
    def ivyDeps = Agg(ivy"com.lihaoyi::utest::0.6.3")
    def testFrameworks = Seq("utest.runner.Framework")
  }
}
