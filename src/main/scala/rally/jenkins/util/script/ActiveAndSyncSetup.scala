package rally.jenkins.util.script

import akka.Done
import akka.http.scaladsl.Http
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.ConfigFactory
import rally.jenkins.util.enum.{BuildSuccess, CreateTenant}
import rally.jenkins.util.model.{AppInfo, BuildInfo}
import rally.jenkins.util.{Context, JenkinsClient, JenkinsClientImpl, JenkinsConfig}

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.concurrent.duration._

object ActiveAndSyncSetup extends Context {

  private val config = ConfigFactory.load
  val jenkinsConfig = JenkinsConfig(config.getString("url"), config.getString("username"), config.getString("token"))
  val jenkinsClient = new JenkinsClientImpl(jenkinsConfig, Http().singleRequest(_))

  private def isDependentOn (app: String, otherApp: String): Boolean = {
    (app, otherApp) match {
      case (a, b) if a == b  => false
      case (_, "sec-mates") => true
      case (a, "marathon-lb") if !List("sec-mates").contains(a) => true
      case (a, "authn-api") if !List("marathon-lb", "sec-mates").contains(a)=> true
      case ("engine-base", "sortinghat-web") => true
      case (a, "engine-base") if a.startsWith("engine-") => true
      case (_, _) => false
    }
  }

  type Edges[T] = Traversable[(T, T)]

  private def buildMatrix(apps: Seq[AppInfo], isDependentOn: (String, String) => Boolean): Edges[AppInfo] = {
    val allCombos = for (x <- apps; y <- apps) yield (x, y)
    allCombos.collect{ case combo if isDependentOn(combo._1.app, combo._2.app) => (combo._1, combo._2)}
  }

  private def topologicalSort(apps: Seq[AppInfo], function: (String, String) => Boolean): Seq[AppInfo] = {
    val matrix = buildMatrix(apps, function)
    val sorted = tsort(matrix).toList
    sorted
  }

  private def tsort[A](edges: Traversable[(A, A)]): Iterable[A] = {
    @tailrec
    def tsort(toPreds: Map[A, Set[A]], done: Iterable[A]): Iterable[A] = {
      val (noPreds, hasPreds) = toPreds partition {
        _._2.isEmpty
      }
      if (noPreds.isEmpty)
        if (hasPreds.isEmpty) done else sys.error(hasPreds.toString)
      else {
        val found = noPreds.keys
        tsort(
          hasPreds.mapValues {
            _ -- found
          }, done ++ found
        )
      }
    }

    val toPred = edges.foldLeft(Map[A, Set[A]]()) { (acc, e) =>
      acc + (e._1 -> acc.getOrElse(e._1, Set())) + (e._2 -> (acc.getOrElse(e._2, Set()) + e._1))
    }
    tsort(toPred, Seq())
  }

  def run(tenant: Option[String]): Future[Done] = for {
    createTenant <- if (tenant.isEmpty || tenant.get == "") jenkinsClient.createTenant("engage", "2 days", "dev", "master", "mark.toujiline@rallyhealth.com")(JenkinsClient.stopOnNonSuccessfulBuild) else Future(BuildInfo(CreateTenant, 0, BuildSuccess, tenant.get))
    tenant = if (createTenant.result == BuildSuccess) createTenant.description else throw new Exception("create tenant failed")
    manifestInfo <- new Manifest(tenant).run
  } yield {
    val missingApps = manifestInfo.missingApps
      .toList

    val runJob = (appInfo: AppInfo) => jenkinsClient.deployComponent(
      appInfo.app,
      appInfo.description,
      tenant,
      "active-and-sync"
    )(JenkinsClient.continueOnNonSuccessfulBuild).map(_ => println(s"Done deploying $appInfo"))
    val sorted = topologicalSort(
      missingApps,
      isDependentOn
    ).toList.reverse

    val source: List[AppInfo] = sorted ++ missingApps.toSet[AppInfo].filterNot(sorted.toSet[AppInfo]).toList
    val cost: AppInfo => Int = (o: AppInfo) => if (source.exists(a => isDependentOn(a.app, o.app))) 5 else 1

    Source(source)
      .throttle(3, 1.minute, cost)
      .to(Sink.foreachAsync(5)(runJob))
      .run()

    Done.done
  }
}
