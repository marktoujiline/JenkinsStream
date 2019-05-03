package rally.jenkins.util.jenkins

import org.scalatest.{FutureOutcome, fixture}
import org.scalatest.Matchers._
import akka.actor.ActorSystem
import org.scalatest.concurrent.ScalaFutures
import rally.jenkins.util.enum.{BuildFailure, BuildSuccess}
import rally.jenkins.util.{JenkinsClient, JenkinsClientImpl, JenkinsConfig}

class JenkinsClientSpec extends fixture.AsyncFunSpec {

  case class FixtureParam(client: JenkinsClient)

  def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    val jenkinsConfig: JenkinsConfig = JenkinsConfig("", "", "")

    implicit val system: ActorSystem = ActorSystem()

    val jenkinsClient: JenkinsClient = new JenkinsClientImpl(jenkinsConfig, JenkinsServer.sendAndReceiveBasic)
    val theFixture = FixtureParam(jenkinsClient)

    try {
      withFixture(test.toNoArgAsyncTest(theFixture)) // "loan" the fixture to the test
    }
    finally system.terminate // clean up the fixture
  }

  describe("CreateTenant") {

    it("should return a description of a tenant when the job completes"){ f =>
      val description = "some-tenant"

      f.client.createTenant("", "", "").map {
        buildInfo => assert(buildInfo.description == description)
      }
    }

    it("should return a failure result if the job fails") { _ =>
      val jenkinsConfig: JenkinsConfig = JenkinsConfig("", "", "")
      val client: JenkinsClient = new JenkinsClientImpl(jenkinsConfig, JenkinsServer.sendAndReceiveFail)

      client.createTenant("", "", "").map {
        buildInfo => assert(buildInfo.result == BuildFailure)
      }
    }

    it("should return a success result after trying a few times") { _ =>
      val jenkinsConfig: JenkinsConfig = JenkinsConfig("", "", "")
      val client: JenkinsClient = new JenkinsClientImpl(jenkinsConfig, JenkinsServer.sendAndReceiveTakesTime)

      client.createTenant("", "", "").map {
        buildInfo => assert(buildInfo.result == BuildSuccess)
      }
    }

    it("should return a failed future if the job fails with a stop handler") { _ =>
      import JenkinsClient._
      val jenkinsConfig: JenkinsConfig = JenkinsConfig("", "", "")
      val client: JenkinsClient = new JenkinsClientImpl(jenkinsConfig, JenkinsServer.sendAndReceiveFail)

      val f = client.createTenant("", "", "")(stopOnNonSuccessfulBuild)
      ScalaFutures.whenReady(f.failed) { e =>
        e shouldBe a[Exception]
      }
    }

    it("should return a failed build if the location header wasn't found") { _ =>
      val jenkinsConfig: JenkinsConfig = JenkinsConfig("", "", "")
      val client: JenkinsClient = new JenkinsClientImpl(jenkinsConfig, JenkinsServer.sendAndReceiveNoLocationHeader)

      client.createTenant("", "", "").map {
        buildInfo => assert(buildInfo.result == BuildFailure)
      }
    }

    it("should return a successful build if there were no previous builds") { _ =>
      val jenkinsConfig: JenkinsConfig = JenkinsConfig("", "", "")
      val client: JenkinsClient = new JenkinsClientImpl(jenkinsConfig, JenkinsServer.sendAndReceiveNoJobsYet)

      client.createTenant("", "", "").map {
        buildInfo => assert(buildInfo.result == BuildSuccess)
      }
    }
  }

  describe("DeployStack") {

    it("should return a description of the tenant that was destroyed") { f =>
      f.client.deployStack("some-tenant", "some-stack").map {
        buildInfo => assert(buildInfo.result == BuildSuccess)
      }
    }
  }

  describe("DeployComponent") {

    it("should return a description of the tenant that was destroyed") { f =>
      f.client.deployComponent("some-component", "some-version", "some-tenant").map {
        buildInfo => assert(buildInfo.result == BuildSuccess)
      }
    }
  }

  describe("DestroyTenant") {

    it("should return a description of the tenant that was destroyed") { f =>
      f.client.destroyTenant("some-tenant").map {
        buildInfo => assert(buildInfo.result == BuildSuccess)
      }
    }
  }
}
