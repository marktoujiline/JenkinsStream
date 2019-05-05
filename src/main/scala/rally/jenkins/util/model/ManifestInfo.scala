package rally.jenkins.util.model

case class ManifestInfo(
  goodApps: Seq[AppInfo],
  missingApps: Seq[AppInfo],
  badEnvApps: Seq[AppInfo]
)
