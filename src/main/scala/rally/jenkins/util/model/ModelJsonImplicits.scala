package rally.jenkins.util.model

import spray.json.DefaultJsonProtocol.jsonFormat6
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

object ModelJsonImplicits {

  implicit val marathonAppFormat: RootJsonFormat[MarathonApp] = jsonFormat6(MarathonApp)
  implicit val marathonAppsFormat: RootJsonFormat[MarathonApps] = jsonFormat1(MarathonApps)
  implicit val jobNumberFormat: RootJsonFormat[JobNumber] = jsonFormat1(JobNumber.apply)
  implicit val rawBuildInfoFormat: RootJsonFormat[RawBuildInfo] = jsonFormat6(RawBuildInfo)
  implicit val appInfoFormat: RootJsonFormat[AppInfo] = jsonFormat3(AppInfo)
  implicit val manifestInfoFormat: RootJsonFormat[ManifestInfo] = jsonFormat3(ManifestInfo)

}
