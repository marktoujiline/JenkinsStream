package rally.jenkins.util.model

case class ManifestInfo(
  goodApps: Seq[AppInfo],
  missingApps: Seq[AppInfo],
  lowVersionApps: Seq[AppInfo],
  badEnvApps: Seq[AppInfo]
)
