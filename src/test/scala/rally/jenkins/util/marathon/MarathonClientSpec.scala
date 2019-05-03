package rally.jenkins.util.marathon

import org.scalatest.{FutureOutcome, fixture}
import akka.actor.ActorSystem

class MarathonClientSpec extends fixture.AsyncFunSpec {

  case class FixtureParam(client: MarathonClient)

  def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    implicit val system: ActorSystem = ActorSystem()

    val marathonClient: MarathonClient = new MarathonClientImpl("some-tenant", MarathonServer.sendAndReceiveBasic)
    val theFixture = FixtureParam(marathonClient)

    try {
      withFixture(test.toNoArgAsyncTest(theFixture)) // "loan" the fixture to the test
    }
    finally system.terminate // clean up the fixture
  }

  describe("getEnv") {
    it("should return a list of MarathonApps") { f =>
      f.client.getEnv map {
        apps => assert(apps.nonEmpty)
      }
    }

  }
}
