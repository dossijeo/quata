export function accountDeactivateDefinitionMd5(installedVersions) {
  return installedVersions.includes("20260928013000")
    ? "290fcd85f9a57e8c999f3132235fdbe4"
    : "d2504acfb2095176289fb99a939f7621";
}
