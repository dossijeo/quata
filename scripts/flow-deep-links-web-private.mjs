import {readFile,writeFile} from "node:fs/promises";
import {createRequire} from "node:module";
import {executeDeepLinkWebTrial} from "./flow-deep-links-web.mjs";
const require=createRequire(import.meta.url);
let client;
try {
  let size=0;const chunks=[];
  for await(const chunk of process.stdin) {
    size+=chunk.length;if(size>1024*1024)throw Error("input_too_large");chunks.push(chunk);
  }
  const input=JSON.parse(Buffer.concat(chunks).toString("utf8"));
  for(const chunk of chunks)chunk.fill(0);
  const {Client}=require(input.pgModule);
  const {chromium}=require(input.playwrightModule);
  const url=new URL((await readFile(input.databaseUrlFile,"utf8")).trim());
  for(const key of ["sslmode","sslrootcert","sslcert","sslkey"])url.searchParams.delete(key);
  client=new Client({connectionString:url.toString(),ssl:{ca:await readFile(input.databaseCaFile,"utf8"),
    rejectUnauthorized:true,servername:url.hostname},connectionTimeoutMillis:10000,statement_timeout:15000});
  await client.connect();
  const expected=JSON.parse(await readFile(input.manifestFile,"utf8"));
  const report=await executeDeepLinkWebTrial({...input,client,chromium,expected});
  await writeFile(input.reportFile,JSON.stringify(report,null,2)+"\n");
  process.stdout.write(JSON.stringify(report)+"\n");
  process.exitCode=report.status==="passed"&&report.cleanupComplete?0:1;
} catch {
  // Never serialize transport/SQL/config errors that may contain private input.
  process.stdout.write(JSON.stringify({status:"runner_failed_before_report"})+"\n");
  process.exitCode=1;
} finally {await client?.end().catch(()=>{});}
