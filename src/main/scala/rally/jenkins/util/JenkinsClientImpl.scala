package rally.jenkins.util

import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import rally.jenkins.util.model.{BuildInfo, RawBuildInfo}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import rally.jenkins.util.enum.{BuildAborted, BuildFailure, BuildResult, BuildSuccess, JobName}
import rally.jenkins.util.model.ModelJsonImplicits._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class JenkinsClientImpl(
  jenkinsConfig: JenkinsConfig,
  sendAndReceive: HttpRequest => Future[HttpResponse]
) extends JenkinsClient with Context with RallyLogger {

  private val credentials = BasicHttpCredentials(jenkinsConfig.username, jenkinsConfig.token)

  private val lastBuild = "lastBuild/api/json"
  private val buildInfo = "api/json"
  private val runBuildWithParameters = "buildWithParameters"

  def createTenant(stacks: String, lifespan: String, environment: String, branch: String = "master")
    (implicit handler: BuildInfo => BuildInfo): Future[BuildInfo] = {
    val parameters = buildParams(Map("stacks" -> stacks, "lifespan" -> lifespan, "environment" -> environment))
    runJob("CreateTenant", branch, parameters) map handler
  }

  def deployStack(tenant: String, stack: String, branch: String = "master")
    (implicit handler: BuildInfo => BuildInfo): Future[BuildInfo] = {
    val parameters = buildParams(Map("tenant" -> tenant, "stack" -> stack))
    runJob("DeployStack", branch, parameters) map handler
  }

  def deployComponent(component: String, version: String, tenant: String, branch: String = "master")
    (implicit handler: BuildInfo => BuildInfo): Future[BuildInfo] = {
    val parameters = buildParams(Map("component" -> component, "version" -> version, "tenant" -> tenant))
    runJob("DeployComponent", branch, parameters) map handler
  }

  def destroyTenant(tenant: String, branch: String = "master")(implicit handler: BuildInfo => BuildInfo): Future[BuildInfo] = {
    val parameters = buildParams(Map("tenant" -> tenant))
    runJob("DestroyTenant", branch, parameters) map handler
  }

  private def runJob(jobName: String, jobBranch: String, parameters: String): Future[BuildInfo] = {
    defaultLogger.debug(s"Running job $jobName with params: $parameters on branch $jobBranch")
    (for {
      queueId <- triggerJob(jobName, jobBranch, parameters, runBuildWithParameters)
      _ <- Future { defaultLogger.debug(s"Found queueId: $queueId") }
      buildInfo <- waitForJobToFinish(jobName, queueId, jobBranch)
      _ <- Future {defaultLogger.debug(s"Job Finished: ${buildInfo.toString}")}
    } yield buildInfo)
      .recover {
        case e: Exception =>
          restLogger.error(e.toString)
          restLogger.error(e.getStackTrace.map(_.toString).mkString("\n    "))
          BuildInfo(JobName.fromString(jobName), -1, BuildFailure, "")
      }
  }

  private def triggerJob(jobName: String, jobBranch: String, parameters: String, jobType: String): Future[String] = {
    val url = List(jenkinsConfig.baseURL, jobPath(jobName, jobBranch), jobType).mkString("/")
    val urlWithParams = s"$url?$parameters"
    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = urlWithParams,
      entity = FormData(
        "json" -> parameters
      ).toEntity
    ).addCredentials(credentials)

    for {
      response <- sendAndReceive(request)
    } yield {
      response.status match {
        case StatusCodes.Created =>
          response.headers.find(p => p.name() == "Location") match {
            case Some(header) => header.value
            case None => throw new Exception("")
          }
        case _ => throw new Exception("")
      }
    }
  }

  /*
  Warning for those that dare inspect the function below. This is some blasphemous code that goes against every
  good practice that exists. We have Awaits, vars, and while loops. I stopped just short of writing a goto...
  This can be rewritten nicely using Akka streams or even Futures, but I'll have to do that at some point
  when I'm not sleep deprived.
   */

  private def waitForJobToFinish(jobName: String, queueLink: String, branch: String): Future[BuildInfo] = {
    val maxAttempts = 360
    val timeBetweenAttempts = 1000
    // extract the number queueId from queueId link
    val splitQueueLink = queueLink.split("/")
    val queueId = splitQueueLink(splitQueueLink.length - 1).toInt

    var attempts = 0
    restLogger.debug("waiting for build to start")
    var rawBuildInfo: Option[RawBuildInfo] = None
    while (attempts < maxAttempts && rawBuildInfo.isEmpty) {
      val last10JobInfos = Await.result(getLastNJobInfos(jobName, 10, branch), 10.seconds)
      rawBuildInfo = last10JobInfos.find(b => b.queueId == queueId)
      if (rawBuildInfo.isEmpty) {
        print(".")
        Thread.sleep(timeBetweenAttempts) // sleep for a second
        attempts += 1
      }
    }

    attempts = 0
    restLogger.debug("waiting for build to finish")
    var buildInfo: Option[BuildInfo] = None
    while (attempts < maxAttempts && buildInfo.isEmpty) {
      rawBuildInfo match {
        case Some(RawBuildInfo(_, _, _, buildId, _, _)) =>
          val rawBuildInfo: RawBuildInfo = Await.result(getJobInfo(jobName, buildId, branch), 10.seconds)
          rawBuildInfo.result match {
            case Some(result) if List(BuildSuccess, BuildFailure, BuildAborted).map(_.toString)
              .contains(result) => buildInfo = Some(
              BuildInfo(
                JobName.fromString(jobName),
                buildId,
                BuildResult.toBuildResult(result),
                rawBuildInfo.description.getOrElse("")
              )
            )
            case None =>
              print(".")
              Thread.sleep(timeBetweenAttempts) // sleep for a second
              attempts += 1
          }
        case None =>
          print(".")
          Thread.sleep(timeBetweenAttempts) // sleep for a second
          attempts += 1
      }
    }

    Future(buildInfo.get)
  }

  private def getLastJobInfo(jobName: String, branch: String): Future[BuildInfo] = {
    val url = List(jenkinsConfig.baseURL, jobPath(jobName, branch), lastBuild).mkString("/")
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = url
    ).addCredentials(credentials)

    for {
      response <- sendAndReceive(request)
      jobInfo <- Unmarshal(response.entity).to[RawBuildInfo]
    } yield jobInfo match {
      case RawBuildInfo(description, _, _, buildId, _, result) => BuildInfo(JobName.fromString(jobName), buildId, BuildResult.toBuildResult(result.getOrElse("")), description.getOrElse(""))
      case _ => throw new Exception("TODO")
    }
  }

  private def getLastNJobInfos(jobName: String, n: Int, branch: String): Future[Seq[RawBuildInfo]] = {
    for {
      lastJobInfo <- getLastJobInfo(jobName, branch)
      lastNJobsInfos <- getJobsInfos(jobName, if (lastJobInfo.buildId - n <= 0) 1 else lastJobInfo.buildId - n, lastJobInfo.buildId, branch)
    } yield lastNJobsInfos
  }

  private def getJobInfo(jobName: String, buildNumber: Int, branch: String): Future[RawBuildInfo] = {
    val url = List(jenkinsConfig.baseURL, jobPath(jobName, branch), buildNumber.toString, buildInfo).mkString("/")
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = url
    ).addCredentials(credentials)

    for {
      response <- sendAndReceive(request)
      jobInfo <- Unmarshal(response.entity).to[RawBuildInfo]
    } yield jobInfo
  }

  private def getJobsInfos(jobName: String, startBuildNumber: Int, endBuildNumber: Int, branch: String): Future[Seq[RawBuildInfo]] = {
    val jobInfo = (number: Int) => getJobInfo(jobName, number, branch)
    val jobInfos = startBuildNumber to endBuildNumber map jobInfo
    Future.sequence(jobInfos)
  }

  private def jobPath(job: String, branch: String) = s"teams-deploys/job/deploys/job/$job/job/$branch"

  private def buildParams(params: Map[String, String]): String = {
    (params.toList map { case (key, value) => s"$key=$value"}).mkString("&").replace(" ", "%20")
  }
}
