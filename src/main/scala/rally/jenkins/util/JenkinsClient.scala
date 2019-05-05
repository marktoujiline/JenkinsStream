package rally.jenkins.util

import rally.jenkins.util.enum.BuildSuccess
import rally.jenkins.util.model.BuildInfo

import scala.concurrent.Future

trait JenkinsClient {

  /** https://ci.rally-dev.com/teams-deploys/job/deploys/job/CreateTenant/job/<branch>/
    *
    * @param stacks "engage" | "arcade" | "core" | "vault" | ...
    * @param lifespan "12 hours" | "3 hours" | "2 days" | "5 days" | "2 weeks"
    * @param environment "dev" | "ci" | "cje-jenkins" | "int" | ...
    * @param branch neptune-deploy branch to use
    * @return information about how build went. Description field contains tenant-name
    */
  def createTenant(stacks: String, lifespan: String, environment: String, branch: String = "master", email: String = "")
    (implicit handler: BuildInfo => BuildInfo): Future[BuildInfo]

  /** https://ci.rally-dev.com/teams-deploys/job/deploys/job/DestroyTenant/job/<branch>/
    *
    * @param tenant Neptune tenant that was created by CreateTenant job
    * @param stack "engage" | "engine" | "arcade" | ...
    * @param branch neptune-deploy branch to use
    * @return information about how build went
    */
  def deployStack(tenant: String, stack: String, branch: String = "master")(implicit handler: BuildInfo => BuildInfo): Future[BuildInfo]

  /** https://ci.rally-dev.com/teams-deploys/job/deploys/job/DeployComponent/job/<branch>/
    *
    * @param component name of service to deploy
    * @param version of service to deploy
    * @param tenant Neptune tenant that was created by CreateTenant job
    * @param branch neptune-deploy branch to use
    * @return information about how build went
    */
  def deployComponent(component: String, version: String, tenant: String, branch: String = "master")
    (implicit handler: BuildInfo => BuildInfo): Future[BuildInfo]

  /** https://ci.rally-dev.com/teams-deploys/job/deploys/job/DestroyTenant/job/<branch>/
    *
    * @param tenant Neptune tenant that was created by CreateTenant job
    * @param branch neptune-deploy branch to use
    * @return information about how build went
    */
  def destroyTenant(tenant: String, branch: String = "master")(implicit handler: BuildInfo => BuildInfo): Future[BuildInfo]
}

object JenkinsClient {

  val stopOnNonSuccessfulBuild: BuildInfo => BuildInfo = (buildInfo: BuildInfo) => {
    buildInfo.result match {
      case BuildSuccess => buildInfo
      case _ => throw new Exception(s"$buildInfo was not successful. Stopping execution")
    }
  }

  val continueOnNonSuccessfulBuild: BuildInfo => BuildInfo = (buildInfo: BuildInfo) => buildInfo
}
