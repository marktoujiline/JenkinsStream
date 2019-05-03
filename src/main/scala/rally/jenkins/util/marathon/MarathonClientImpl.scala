package rally.jenkins.util.marathon

import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse}
import rally.jenkins.util.model.{MarathonApp, MarathonApps}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import rally.jenkins.util.Context
import rally.jenkins.util.model.ModelJsonImplicits._

import scala.concurrent.Future
import scala.io.Source

class MarathonClientImpl(
  tenantParam: String,
  sendAndReceive: HttpRequest => Future[HttpResponse]
)extends MarathonClient with Context {

  def tenant: String = tenantParam

  def baseUrl: String = s"http://mesos.$tenantParam.rally-dev.com:8080"

  def getEnv(): Future[Seq[MarathonApp]] = {
    val url = baseUrl + "/v2/apps"
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = url,
    )

    (for {
      response <- sendAndReceive(request)
      marathonApps <- Unmarshal(response.entity).to[MarathonApps]
    } yield {
      marathonApps.apps.sortBy(_.id)
    }).recover {
      case ex =>
        println(ex.getMessage)
        Seq.empty[MarathonApp]
    }
  }

  def updateEnv(appId: String, env: Map[String, String]): Future[MarathonApp] = ???

  def properSetup(tenant: String): Future[Seq[MarathonApp]] = {
      val file = Source.fromFile("working-active-and-sync.json")
      val fileContents = file.getLines.mkString.replace("{{tenant}}", tenant)

    Unmarshal(fileContents).to[MarathonApps].map(_.apps.sortBy(_.id))
  }

  def appWithoutTenant(id: String): String = id.split("/").tail.tail.mkString("/")
}
