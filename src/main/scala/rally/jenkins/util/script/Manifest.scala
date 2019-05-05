package rally.jenkins.util.script
import akka.http.scaladsl.Http
import rally.jenkins.util.Context
import rally.jenkins.util.marathon.{MarathonClient, MarathonClientImpl}
import rally.jenkins.util.model.{AppInfo, ManifestInfo, MarathonApp}

import scala.concurrent.Future

class Manifest(tenant: String) extends Context {

  private val marathonClient: MarathonClient = new MarathonClientImpl(tenant, Http().singleRequest(_))
  private val lines = io.Source.fromResource("apps.txt").getLines.toList.map(_.replace(".yml", ""))

  private def toComponentWithoutTenant(rawId: String): String = rawId.split("/").tail.tail.mkString("/").replace("/", "-")
  private def toValidComponentName(rawComponentName: String): String = {
    if (rawComponentName == "marathon-lb-marathon-lb") "marathon-lb"
    else {
      val appName :: componentName :: Nil = rawComponentName
        .split("-", 2)
        .toList

      val validApps = lines

      if (validApps.contains(rawComponentName)) rawComponentName
      else if (validApps.contains(appName)) appName
      else if (validApps.contains(componentName)) componentName
      else if (validApps.exists(_.contains(appName))) validApps.find(_.contains(appName)) match {
        case Some(n) => n
        case None => throw new Exception("Something went terrible wrong")
      }
      else if (validApps.exists(_.contains(componentName))) validApps.find(_.contains(componentName)) match {
        case Some(n) => n
        case None => throw new Exception("Something else went terrible wrong")
      }
      else "unknown"
    }
  }

  private def toProperVersion(appInfo: AppInfo): AppInfo = {
    val badVersion = List("unknown", "latest").contains(appInfo.description)
    val knownVersion = missingVersions.toList.find(_._1 == appInfo.app)
    if (badVersion && knownVersion.nonEmpty) appInfo.copy(description = knownVersion.get._2)
    else if (badVersion) appInfo.copy(description = "FILTER")
    else appInfo
  }

  private val missingVersions = Map(
    "marathon-lb" -> "3.2.0",
    "core-doppelganger" -> "4.20.23",
    "core-dreamliner-eligibility-svc" -> "10.8.1",
    "core-kamaji" -> "2.4.0",
    "engage-banzai-buddy" -> "1.3.1"
  )

  def run: Future[ManifestInfo] = for {
    properSetupRaw <- marathonClient.properSetup(tenant)
    currentSetupRaw <- marathonClient.getEnv()
  } yield {

    def toAppInfo(mApp: MarathonApp): AppInfo = toProperVersion(AppInfo(mApp.id, "SUCCESS", mApp.env.getOrElse("VERSION", "unknown")))
    def toValidAppName(mApp: MarathonApp): MarathonApp = mApp.copy(id = toValidComponentName(toComponentWithoutTenant(mApp.id)))
    val properApps: Seq[MarathonApp] = properSetupRaw.map(toValidAppName)
    val currentApps: Seq[MarathonApp] = currentSetupRaw.map(toValidAppName)

    def isMissingApp(appInfo: MarathonApp): Boolean = !currentApps.map(_.id).contains(appInfo.id)

    def isBadEnvApp(properApp: MarathonApp): Boolean = {
      currentApps.find(_.id == properApp.id) match {
        case None => false
        case Some(currentApp) => (properApp.env.toSet diff currentApp.env.toSet).nonEmpty
      }
    }

    sealed trait AppType
    case object MissingApp extends AppType
    case object BadEnvApp extends AppType
    case object GoodApp extends AppType

    def appType(appInfo: MarathonApp): AppType = {
      if (isMissingApp(appInfo)) MissingApp
      else if (isBadEnvApp(appInfo)) BadEnvApp
      else GoodApp
    }

    val grouped = properApps.groupBy(appType)
    def group(appType: AppType): Seq[AppInfo] = grouped.getOrElse(appType, List.empty)
      .map(toAppInfo)
      .filter(_.description != "FILTER")

    ManifestInfo(group(GoodApp), group(MissingApp), group(BadEnvApp))
  }
}
