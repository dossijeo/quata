import {readFile,writeFile} from "node:fs/promises";
const file=process.argv[2]; if(!file)throw Error("index_path_required");
const values={
 "quata-web-registration-enabled":process.env.QUATA_WEB_REGISTRATION_ENABLED==="true"?"true":"false",
 "quata-web-registration-api-key":process.env.QUATA_WEB_REGISTRATION_API_KEY||"",
 "quata-turnstile-site-key":process.env.QUATA_TURNSTILE_SITE_KEY||"",
};
if(values["quata-web-registration-enabled"]==="true"&&
 (!values["quata-web-registration-api-key"]||!values["quata-turnstile-site-key"]))throw Error("registration_public_config_missing");
let html=await readFile(file,"utf8");
for(const [name,value] of Object.entries(values)){
 const escaped=value.replaceAll("&","&amp;").replaceAll('"',"&quot;").replaceAll("<","&lt;");
 const pattern=new RegExp(`(<meta name="${name}" content=")[^"]*(">)`);
 if(!pattern.test(html))throw Error(`meta_missing:${name}`);
 html=html.replace(pattern,`$1${escaped}$2`);
}
await writeFile(file,html);
