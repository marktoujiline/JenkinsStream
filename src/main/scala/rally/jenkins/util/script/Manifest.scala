package rally.jenkins.util.script
import akka.http.scaladsl.Http
import rally.jenkins.util.Context
import rally.jenkins.util.marathon.{MarathonClient, MarathonClientImpl}
import rally.jenkins.util.model.{AppInfo, ManifestInfo, MarathonApp}

import scala.concurrent.Future

class Manifest(tenant: String) extends Context {

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

  def run: Future[ManifestInfo] = for {
    properSetup <- marathonClient.properSetup(tenant)
    currentSetup <- marathonClient.getEnv()
  } yield {
    val missingApps: Seq[MarathonApp] = properSetup.filter(properApp => !currentSetup.exists(_.id == properApp.id))

    val missingAppsList = missingApps.map(a => AppInfo(a.id, "Missing", a.env.getOrElse("VERSION", "unknown")))
    val lowVersionAppsList = Seq.empty[AppInfo]
    val badEnvAppsList = Seq.empty[AppInfo]
    val goodAppsList = properSetup.intersect(missingApps).map(a => AppInfo(a.id, "Good",
      a.env.getOrElse("VERSION", "unknown")))

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
                  lowVersionAppsList :+ AppInfo(properApp.id, "LowVersion", s"Wrong version: $currentAppValue does not match ${keyValue._2}")
                }
              }
              else if (currentAppValue != keyValue._2) {
                badEnvAppsList :+
                  AppInfo(properApp.id, "BadEnv", s"Bad env: $currentAppValue does not match ${keyValue._2}")
              }
            }
          )
        }
      }
    )

    ManifestInfo(
      goodAppsList,
      missingAppsList,
      lowVersionAppsList,
      badEnvAppsList
    )
  }
}
