package rally.jenkins.util.jenkins

import org.scalatest.{FutureOutcome, fixture}
import akka.actor.ActorSystem
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
  }

  describe("DestroyTenant") {

    it("should return a description of the tenant that was destroyed") { f =>
      val description = "some-tenant"

      f.client.destroyTenant("some-tenant").map {
        buildInfo => assert(buildInfo.description == description)
      }
    }
  }
}
