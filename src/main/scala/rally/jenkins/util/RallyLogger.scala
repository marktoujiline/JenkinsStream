package rally.jenkins.util

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

trait RallyLogger {
  val defaultLogger = Logger(LoggerFactory.getLogger("default"))
  val restLogger = Logger(LoggerFactory.getLogger("rest"))
}
