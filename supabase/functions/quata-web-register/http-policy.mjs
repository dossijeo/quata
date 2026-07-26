export function parseRegistrationConfig(env) {
  const value=(name)=>env(name)||null;
  const config={
    supabaseUrl:value("SUPABASE_URL"), serviceRoleKey:value("SUPABASE_SERVICE_ROLE_KEY"),
    publicApiKey:value("QUATA_WEB_REGISTRATION_API_KEY"), pepper:value("QUATA_WEB_REGISTRATION_PEPPER"),
    internalAuthPasswordSecret:value("QUATA_INTERNAL_AUTH_PASSWORD_SECRET"),
    internalAuthPasswordSecretVersion:value("QUATA_INTERNAL_AUTH_PASSWORD_SECRET_VERSION"),
    enabled:value("QUATA_WEB_REGISTRATION_ENABLED")==="true",
    turnstileSecret:value("QUATA_WEB_REGISTRATION_TURNSTILE_SECRET"),
    allowedOrigins:(value("QUATA_WEB_REGISTRATION_ALLOWED_ORIGINS")||"").split(",").map(v=>v.trim()).filter(Boolean),
    turnstileAllowedHostnames:(value("QUATA_TURNSTILE_ALLOWED_HOSTNAMES")||"").split(",").map(v=>v.trim()).filter(Boolean),
  };
  if(!config.supabaseUrl||!config.serviceRoleKey||!config.publicApiKey||!config.pepper||config.pepper.length<32||
    !config.internalAuthPasswordSecret||config.internalAuthPasswordSecret.length<32||
    !config.internalAuthPasswordSecretVersion||(config.enabled&&(!config.turnstileSecret||!config.turnstileAllowedHostnames.length)))
    throw Error("server_not_configured");
  return config;
}
export const isAllowedOrigin=(origin,allowed)=>Boolean(origin)&&allowed.includes(origin);
export async function verifyTurnstileChallenge(secret,token,remoteIp,expectedAction,allowedHostnames,fetcher=fetch){
  if(!token)return false; const body=new URLSearchParams({secret,response:token});
  if(remoteIp&&!remoteIp.startsWith("untrusted-"))body.set("remoteip",remoteIp);
  const response=await fetcher("https://challenges.cloudflare.com/turnstile/v0/siteverify",{method:"POST",body});
  if(!response.ok)return false; const result=await response.json();
  return result.success===true&&result.action===expectedAction&&allowedHostnames.includes(result.hostname);
}
