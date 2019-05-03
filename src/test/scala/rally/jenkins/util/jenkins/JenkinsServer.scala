package rally.jenkins.util.jenkins

import akka.http.scaladsl.model.HttpHeader.ParsingResult.{Error, Ok}
import akka.http.scaladsl.model._
import rally.jenkins.util.Context
import rally.jenkins.util.model.ModelJsonImplicits._
import rally.jenkins.util.model.RawBuildInfo
import spray.json._

import scala.concurrent.Future

object JenkinsServer extends Context {

  val sendAndReceiveBasic: HttpRequest => Future[HttpResponse] = (req: HttpRequest) => Future {
    req match {
      case HttpRequest(HttpMethods.GET, uri, _, _, _) => uri.path.toString match {
        case "/teams-deploys/job/deploys/job/CreateTenant/job/master/lastBuild/api/json" =>
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              RawBuildInfo(Some("some-tenant"), 10, 10, 1, 1, Some("SUCCESS")).toJson.toString
            )
          )
        case "/teams-deploys/job/deploys/job/DestroyTenant/job/master/lastBuild/api/json" =>
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              RawBuildInfo(Some("some-tenant"), 10, 10, 1, 1, Some("SUCCESS")).toJson.toString
            )
          )
        case "/teams-deploys/job/deploys/job/DeployStack/job/master/lastBuild/api/json" =>
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              RawBuildInfo(Some("some-tenant"), 10, 10, 1, 1, Some("SUCCESS")).toJson.toString
            )
          )
        case "/teams-deploys/job/deploys/job/DeployComponent/job/master/lastBuild/api/json" =>
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              RawBuildInfo(Some("some-tenant"), 10, 10, 1, 1, Some("SUCCESS")).toJson.toString
            )
          )
        case "/teams-deploys/job/deploys/job/CreateTenant/job/master/1/api/json" =>
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              RawBuildInfo(Some("some-tenant"), 10, 10, 1, 1, Some("SUCCESS")).toJson.toString
            )
          )
        case "/teams-deploys/job/deploys/job/DestroyTenant/job/master/1/api/json" =>
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              RawBuildInfo(Some("some-tenant"), 10, 10, 1, 1, Some("SUCCESS")).toJson.toString
            )
          )
        case "/teams-deploys/job/deploys/job/DeployStack/job/master/1/api/json" =>
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              RawBuildInfo(Some("some-tenant"), 10, 10, 1, 1, Some("SUCCESS")).toJson.toString
            )
          )
        case "/teams-deploys/job/deploys/job/DeployComponent/job/master/1/api/json" =>
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              RawBuildInfo(Some("some-tenant"), 10, 10, 1, 1, Some("SUCCESS")).toJson.toString
            )
          )
        case _ => HttpResponse(status = StatusCodes.NotFound)
      }
      case HttpRequest(HttpMethods.POST, _, _, _, _) =>
        HttpResponse(
          status = StatusCodes.Created
        ).addHeader(
          HttpHeader.parse("Location", "/some/path/to/build/1") match {
            case Ok(header, errors) => if (errors.isEmpty) header else throw new Exception(
              errors.map(_.detail)
                .mkString("")
            )
            case Error(error) => throw new Exception(error.detail)
          }
        )
      case _ => HttpResponse(status = StatusCodes.NotFound)
    }
  }

  val sendAndReceiveFail: HttpRequest => Future[HttpResponse] = (req: HttpRequest) => Future {
    req match {
      case HttpRequest(HttpMethods.GET, uri, _, _, _) => uri.path.toString match {
        case "/teams-deploys/job/deploys/job/CreateTenant/job/master/lastBuild/api/json" =>
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              RawBuildInfo(Some("some-tenant"), 10, 10, 1, 1, Some("SUCCESS")).toJson.toString
            )
          )
        case "/teams-deploys/job/deploys/job/CreateTenant/job/master/1/api/json" =>
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              RawBuildInfo(Some("some-tenant"), 10, 10, 1, 1, Some("FAILURE")).toJson.toString
            )
          )
        case _ => HttpResponse(status = StatusCodes.NotFound)
      }
      case HttpRequest(HttpMethods.POST, _, _, _, _) =>
        HttpResponse(
          status = StatusCodes.Created
        ).addHeader(
          HttpHeader.parse("Location", "/some/path/to/build/1") match {
            case Ok(header, errors) => if (errors.isEmpty) header else throw new Exception(
              errors.map(_.detail)
                .mkString("")
            )
            case Error(error) => throw new Exception(error.detail)
          }
        )
      case _ => HttpResponse(status = StatusCodes.NotFound)
    }
  }

  var attempt = 0
  val sendAndReceiveTakesTime: HttpRequest => Future[HttpResponse] = (req: HttpRequest) => Future {
    req match {
      case HttpRequest(HttpMethods.GET, uri, _, _, _) => uri.path.toString match {
        case "/teams-deploys/job/deploys/job/CreateTenant/job/master/lastBuild/api/json" =>
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              RawBuildInfo(Some("some-tenant"), 10, 10, 2, 2, Some("SUCCESS")).toJson.toString
            )
          )
        case "/teams-deploys/job/deploys/job/CreateTenant/job/master/1/api/json" =>
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              RawBuildInfo(Some("some-tenant"), 10, 10, 1, 1, Some("SUCCESS")).toJson.toString
            )
          )
        case "/teams-deploys/job/deploys/job/CreateTenant/job/master/2/api/json" if attempt <= 5 =>
          attempt += 1
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              RawBuildInfo(Some("some-tenant"), 10, 10, 2, 2, None).toJson.toString
            )
          )
        case "/teams-deploys/job/deploys/job/CreateTenant/job/master/2/api/json" if attempt > 5 =>
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              RawBuildInfo(Some("some-tenant"), 10, 10, 2, 2, Some("SUCCESS")).toJson.toString
            )
          )
        case _ => HttpResponse(status = StatusCodes.NotFound)
      }
      case HttpRequest(HttpMethods.POST, _, _, _, _) =>
        HttpResponse(
          status = StatusCodes.Created
        ).addHeader(
          HttpHeader.parse("Location", "/some/path/to/build/2") match {
            case Ok(header, errors) => if (errors.isEmpty) header else throw new Exception(
              errors.map(_.detail)
                .mkString("")
            )
            case Error(error) => throw new Exception(error.detail)
          }
        )
      case _ => HttpResponse(status = StatusCodes.NotFound)
    }
  }

  val sendAndReceiveNoLocationHeader: HttpRequest => Future[HttpResponse] = (req: HttpRequest) => Future {
    req match {
      case HttpRequest(HttpMethods.POST, _, _, _, _) => HttpResponse(status = StatusCodes.Created)
      case _ => HttpResponse(status = StatusCodes.NotFound)
    }
  }

  var attemptA = 0
  val sendAndReceiveNoJobsYet: HttpRequest => Future[HttpResponse] = (req: HttpRequest) => Future {
    attemptA += 1
    req match {
      case HttpRequest(HttpMethods.GET, uri, _, _, _) => uri.path.toString match {
        case "/teams-deploys/job/deploys/job/CreateTenant/job/master/lastBuild/api/json" if attemptA > 3 =>
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              RawBuildInfo(Some("some-tenant"), 10, 10, 1, 1, None).toJson.toString
            )
          )
        case "/teams-deploys/job/deploys/job/CreateTenant/job/master/1/api/json" if attemptA > 3 && attemptA < 5 =>
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              RawBuildInfo(Some("some-tenant"), 10, 10, 1, 1, None).toJson.toString
            )
          )
        case "/teams-deploys/job/deploys/job/CreateTenant/job/master/1/api/json" if attemptA >= 5 =>
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              RawBuildInfo(Some("some-tenant"), 10, 10, 1, 1, Some("SUCCESS")).toJson.toString
            )
          )
        case _ => HttpResponse(status = StatusCodes.NotFound)
      }
      case HttpRequest(HttpMethods.POST, _, _, _, _) if attemptA > 4 =>
        HttpResponse(
          status = StatusCodes.Created
        ).addHeader(
          HttpHeader.parse("Location", "/some/path/to/build/1") match {
            case Ok(header, errors) =>
              attemptA = 0
              if (errors.isEmpty) header else throw new Exception(
              errors.map(_.detail)
                .mkString("")
            )
            case Error(error) => throw new Exception(error.detail)
          }
        )
      case _ => HttpResponse(status = StatusCodes.NotFound)
    }
  }
}
