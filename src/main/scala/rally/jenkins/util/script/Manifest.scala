package rally.jenkins.util.script
import akka.Done
import akka.http.scaladsl.Http
import rally.jenkins.util.Context
import rally.jenkins.util.marathon.{MarathonClient, MarathonClientImpl}
import rally.jenkins.util.model.MarathonApp

import scala.concurrent.Future

class Manifest(tenant: String) extends Script with Context {

  private val marathonClient: MarathonClient = new MarathonClientImpl(tenant, Http().singleRequest(_))

  private def appWithoutTenant(id: String): String = id.split("/").tail.tail.mkString("/")

  private def semver(s: String): Int = {
    if (s == "latest") {
      99999999
    } else {
      val split = s.split("\\.")
      val hundred = split(0).toInt * 100
      val ten = split(1).toInt * 10
      val one = split(2).charAt(0).toInt * 1

      hundred + ten + one
    }
  }

  override def run: Future[Done] = for {
    // check if any apps are missing
    properSetup <- marathonClient.properSetup(tenant)
    currentSetup <- marathonClient.getEnv()
    missingApps: Seq[MarathonApp] = properSetup.filter(properApp => !currentSetup.exists(_.id == properApp.id))
  } yield {
    println(s"${missingApps.length} apps are missing:")
    missingApps.foreach(
      missingApp => {
        println(
          s"${appWithoutTenant(missingApp.id)} : ${
            properSetup.find(app => app.id == missingApp.id).get.env
              .getOrElse("VERSION", "unknown")
          }"
        )
      }
    )

    properSetup.foreach(
      properApp => {
        if (!missingApps.map(_.id).contains(properApp.id)) {
          properApp.env.foreach(
            (keyValue: (String, String)) => {
              val currentApp = currentSetup.find(_.id == properApp.id).get
              val currentAppValue = currentApp.env.getOrElse(keyValue._1, "")
              if (keyValue._1 == "VERSION") {
                val properAppVersion = properApp.env.getOrElse("VERSION", "0.0.0")
                val currentAppVersion = currentApp.env.getOrElse("VERSION", "0.0.0")
                val isCorrectVersion = semver(properAppVersion) <= semver(currentAppVersion)
                if (!isCorrectVersion) {
                  println(s"app: ${properApp.id} Wrong version: $currentAppValue does not match ${keyValue._2}")
                }
              }
              else if (currentAppValue != keyValue._2) {
                println(s"app: ${properApp.id} key ${keyValue._1}: $currentAppValue does not match ${keyValue._2}")
              }
            }
          )
        }
      }
    )

    Done.done
  }
}
