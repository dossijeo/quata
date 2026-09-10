import {spawn} from "node:child_process";
const simulator="F2E1EA50-FBAD-443C-A98F-2A576C14C70B";
const failure=()=>Error("deep_link_ios_channel_unresolved");
const same=(value,expected)=>value&&Object.keys(value).sort().join(",")===Object.keys(expected).sort().join(",")&&
  Object.keys(expected).every(key=>value[key]===expected[key]);

// SSH stdout is a private protocol, never a log. A local SSH exit alone does
// not prove remote shutdown; settled needs the exact close receipt AND exit 0.
export async function openIosDeepLinkChannel({root,products,spawnImpl=spawn,timeoutMs=1200000}) {
  if(![root,products].every(value=>typeof value==="string"&&/^\/Users\/gabriel\/[A-Za-z0-9_./-]+$/.test(value)&&
      !value.split("/").includes(".."))||!products.startsWith(root+"/")||!Number.isSafeInteger(timeoutMs)||timeoutMs<1)throw failure();
  const child=spawnImpl("ssh",["-o","BatchMode=yes","-o","ConnectTimeout=15","quata-mac",
    "python3",root+"/scripts/flow-deep-links-ios-worker.py","--root",root,"--products",products],
    {windowsHide:true,stdio:["pipe","pipe","pipe"],shell:false});
  let pending,buffer="",broken=false,closedReceipt=false,terminal=false,exitCode;
  let resolveExit;
  const exited=new Promise(resolve=>{resolveExit=resolve;});
  const fail=()=>{
    if(broken)return;
    broken=true;
    if(pending){clearTimeout(pending.timer);pending.reject(failure());pending=undefined;}
    // Stop the local pipe only; remote cleanup remains unproven after failure.
    if(!terminal)child.kill();
  };
  function waitFor(expected) {
    if(broken||pending||terminal)throw failure();
    return new Promise((resolve,reject)=>{
      pending={expected,resolve,reject,timer:setTimeout(fail,timeoutMs)};
    });
  }
  const ready=waitFor({ready:true,simulator});
  child.on("error",fail);
  child.stdin.on("error",fail);
  child.stderr.on("data",()=>{});
  child.stdout.setEncoding("utf8");
  child.stdout.on("data",chunk=>{
    buffer+=chunk;
    if(Buffer.byteLength(buffer)>4096){fail();return;}
    let index;
    while((index=buffer.indexOf("\n"))!==-1){
      const line=buffer.slice(0,index);buffer=buffer.slice(index+1);
      let value;
      try {value=JSON.parse(line);}catch {fail();return;}
      if(!pending||!same(value,pending.expected)){fail();return;}
      const current=pending;pending=undefined;clearTimeout(current.timer);
      if(value.closed===true)closedReceipt=true;
      current.resolve(value);
    }
  });
  child.on("close",code=>{
    terminal=true;exitCode=code;
    if(pending||buffer.trim()||!closedReceipt||code!==0)fail();
    resolveExit();
  });
  await ready;
  async function request(message,expected) {
    if(closedReceipt)throw failure();
    const text=JSON.stringify(message)+"\n";
    if(Buffer.byteLength(text)>32768)throw failure();
    const response=waitFor(expected);
    child.stdin.write(text);
    return response;
  }
  return {
    probe:({runId,stepId})=>request({action:"probe",runId,stepId},{runId,stepId,probe:true,verified:true}),
    sessionStep:input=>request({action:"session",input},{runId:input.runId,stepId:input.stepId,stage:input.stage,verified:true}),
    async close(){
      await request({action:"close"},{closed:true});
      child.stdin.end();
      let timer;
      // A kill or timeout must not turn an unresolved remote close into success.
      await Promise.race([exited,new Promise(resolve=>{timer=setTimeout(()=>{fail();resolve();},15000);})]);
      clearTimeout(timer);
      if(broken||!terminal||exitCode!==0||!closedReceipt)throw failure();
    },
    settled:()=>!broken&&terminal&&exitCode===0&&closedReceipt,
    abort:fail,
  };
}
