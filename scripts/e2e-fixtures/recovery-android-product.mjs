// Connected only to this run's adb-forwarded instrumentation socket.
// No credentials in files, argv or logs. The caller owns instrumentation/forward cleanup.
export function createRecoveryAndroidProduct({socket,record,verifyActor,closeResources,timeout=45000}) {
  if(!socket || typeof verifyActor!=="function" || typeof closeResources!=="function")throw Error("recovery_android_dependencies_required");
  let sequence=0,pending,buffer="",uncertain=false,closed=false,closeResult=false,saveCount=0,failurePhase;
  const fail=()=>{uncertain=true;buffer="";if(pending){clearTimeout(pending.timer);pending.reject(Error("recovery_android_channel_uncertain"));pending=undefined;}};
  socket.setEncoding("utf8");
  socket.on("error",fail);
  socket.on("close",()=>{if(!closed)fail();});
  socket.on("data",chunk=>{
    if(closed || uncertain)return;
    buffer+=chunk;
    if(Buffer.byteLength(buffer)>65536)return fail();
    const newline=buffer.indexOf("\n");
    if(newline<0)return;
    const line=buffer.slice(0,newline);buffer=buffer.slice(newline+1);
    try {
      const reply=JSON.parse(line);
      if(!pending || buffer || reply.id!==pending.id)throw Error();
      if(reply.ok!==true){
        if(reply.ok===false && ["read_opening","read_details","read_verification","recovery_opening","recovery_login","recovery_form","recovery_question","reset_started","recovery_return","recovery_still_open","recovery_destination_missing"].includes(reply.phase))failurePhase=reply.phase;
        throw Error();
      }
      const current=pending;pending=undefined;clearTimeout(current.timer);current.resolve(reply.result);
    }catch{fail();}
  });
  const command=(action,args={})=>{
    if(closed || uncertain || pending)return Promise.reject(Error("recovery_android_channel_unavailable"));
    return new Promise((resolve,reject)=>{
      const id=++sequence;
      pending={id,resolve,reject,timer:setTimeout(fail,timeout)};
      socket.write(JSON.stringify({id,action,args})+"\n",error=>{if(error)fail();});
    });
  };
  const requireTrue=value=>{if(value!==true){uncertain=true;throw Error("recovery_android_operation_unverified");}return true;};
  return Object.freeze({
    async login(password,ticket){
      if(ticket?.kind!=="native" || typeof ticket.ticketId!=="string")throw Error("recovery_android_native_ticket_required");
      const credentials=await command("login",{profileId:record.profileId,authUserId:record.authUserId,
        countryCode:record.countryCode,phone:record.phone,password});
      try{return requireTrue(await verifyActor(record,ticket,credentials));}
      catch{uncertain=true;throw Error("recovery_android_actor_unverified");}
      finally{if(credentials)credentials.accessToken=null;}
    },
    async openAccount(){return requireTrue(await command("open"));},
    async configureSecret(question,answer){return requireTrue(await command("configure",{question,answer}));},
    async saveSecret(){if(saveCount++)throw Error("recovery_android_save_once");return requireTrue(await command("save"));},
    async readPermittedState(){return command("read");},
    async logout(){return requireTrue(await command("logout"));},
    async recoverPassword(answer,password){return requireTrue(await command("recover",{answer,password,phone:record.phone,countryCode:record.countryCode}));},
    operationsSettled:()=>!uncertain && !pending,
    failurePhase:()=>failurePhase,
    async close(){
      if(closed)return closeResult;
      let acknowledged=false;
      try{acknowledged=await command("close")===true;}catch{/* A timeout never establishes remote cancellation. */}
      closed=true;socket.destroy();
      const resourcesClosed=await closeResources();
      closeResult=acknowledged && resourcesClosed===true;
      return closeResult;
    },
  });
}
