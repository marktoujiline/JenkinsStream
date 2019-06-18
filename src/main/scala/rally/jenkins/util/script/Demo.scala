package rally.jenkins.util.script

import akka.Done
import akka.http.scaladsl.Http
import com.typesafe.config.ConfigFactory
import rally.jenkins.util.enum.{BuildSuccess, CreateTenant}
import rally.jenkins.util.model.BuildInfo
import rally.jenkins.util.{Context, JenkinsClient, JenkinsClientImpl, JenkinsConfig}

import scala.concurrent.Future

class Demo extends Context  {
  private val config = ConfigFactory.load
  val jenkinsConfig = JenkinsConfig(config.getString("url"), config.getString("username"), config.getString("token"))
  val jenkinsClient = new JenkinsClientImpl(jenkinsConfig, Http().singleRequest(_))

  def run(tenant: Option[String]): Future[Done] = (for {
    createTenant <- if (tenant.isEmpty || tenant.get == "") jenkinsClient.createTenant("engage", "2 days", "dev", "master", "mark.toujiline@rallyhealth.com")(JenkinsClient.stopOnNonSuccessfulBuild) else Future(BuildInfo(CreateTenant, 0, BuildSuccess, tenant.get))
    tenant = if (createTenant.result == BuildSuccess) createTenant.description else throw new Exception("create tenant failed")
    incentives <- jenkinsClient.deployStack(tenant, "incentives")(JenkinsClient.stopOnNonSuccessfulBuild)
    component <- jenkinsClient.deployComponent("rewards-ui", "8.9.5-14-b5911ed-SNAPSHOT", tenant)(JenkinsClient.continueOnNonSuccessfulBuild)
  } yield {
    println(incentives)
    println(component)
    Done.done
  }).recover {
    case ex =>
      println(ex)
      Done.done
  }
}
