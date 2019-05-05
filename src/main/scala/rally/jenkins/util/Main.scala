package rally.jenkins.util

import akka.http.scaladsl.Http
import script.{ActiveAndSyncSetup, Manifest => Man}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import rally.jenkins.util.model.ManifestInfo

import scala.concurrent.Future
import scala.io.StdIn
import rally.jenkins.util.model.ModelJsonImplicits._
import spray.json._

import scala.util.{Failure, Success}

object Main extends App with Context {

  val route =
    path("manifest") {
      post {
        entity(as[String]) { tenant =>
          val saved: Future[ManifestInfo] = new Man(tenant).run
          onComplete(saved) {
            case Success(manifestInfo) => complete(
              HttpEntity(
                ContentTypes.`application/json`,
                manifestInfo.toJson.toString
              )
            )
            case Failure(ex) => complete(StatusCodes.OK)
          }
        }
      }
    } ~
    path("activeAndSync") {
      post {
        entity(as[Option[String]]) { tenant =>
          ActiveAndSyncSetup.run(tenant)
          complete(StatusCodes.OK)
        }
      }
    }

  val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}
