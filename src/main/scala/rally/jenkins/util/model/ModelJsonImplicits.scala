package rally.jenkins.util.model

import spray.json.DefaultJsonProtocol.jsonFormat6
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

object ModelJsonImplicits {

  implicit val jobNumberFormat: RootJsonFormat[JobNumber] = jsonFormat1(JobNumber.apply)
  implicit val rawBuildInfoJson: RootJsonFormat[RawBuildInfo] = jsonFormat6(RawBuildInfo)

}
