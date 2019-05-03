package rally.jenkins.util.marathon

import akka.http.scaladsl.model._
import rally.jenkins.util.Context
import rally.jenkins.util.model.{MarathonApp, MarathonApps}
import spray.json._

import scala.concurrent.Future

object MarathonServer extends Context {
  import rally.jenkins.util.model.ModelJsonImplicits._

  val sendAndReceiveBasic: HttpRequest => Future[HttpResponse] = (req: HttpRequest) => Future {
    req match {
      case HttpRequest(HttpMethods.GET, _, _, _, _) =>
        HttpResponse(
          status = StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            MarathonApps(MarathonApp(
              "id",
              Map.empty,
              0, 0 ,0, 0
            ) :: Nil).toJson.toString
          )
        )
      case _ => HttpResponse(status = StatusCodes.NotFound)
    }
  }
}
