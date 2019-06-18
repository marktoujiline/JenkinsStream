package rally.jenkins.util.marathon

import rally.jenkins.util.model.MarathonApp

import scala.concurrent.Future

trait MarathonClient {

  def tenant: String

  def updateEnv(appId: String, env: Map[String, String]): Future[MarathonApp]

  def getEnv: Future[Seq[MarathonApp]]

  def getIntegrationEnv: Future[Seq[MarathonApp]]

  def properSetup(tenant: String): Future[Seq[MarathonApp]]
}
