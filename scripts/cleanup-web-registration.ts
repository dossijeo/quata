// Operator-only scanner. Claims one quarantined saga atomically; rerun to drain.
import { createClient } from "npm:@supabase/supabase-js@2";
const url=Deno.env.get("SUPABASE_URL"), key=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
const actor=Deno.env.get("QUATA_CLEANUP_ACTOR")||"manual-operator";
if(!url||!key) throw Error("cleanup_configuration_missing");
const admin=createClient(url,key,{auth:{persistSession:false}});
const token=crypto.randomUUID();
const claim=await admin.rpc("quata_claim_web_registration_cleanup",{p_token:token,p_actor:actor});
if(claim.error) throw claim.error;
if(claim.data?.kind==="empty"){console.log(JSON.stringify({event:"cleanup_scan_empty"}));Deno.exit(0);}
const row=claim.data.request, counts={web_sessions:0,profile:0,auth:0};
let failure:string|null=null;
try{
 const sessions=await admin.from("web_client_sessions").delete().eq("profile_id",row.profile_id).select("id");
 if(sessions.error)throw sessions.error; counts.web_sessions=sessions.data?.length||0;
 const profile=await admin.from("community_profiles").delete().eq("id",row.profile_id)
   .eq("auth_user_id",row.auth_user_id).select("id");
 if(profile.error)throw profile.error; counts.profile=profile.data?.length||0;
 const auth=await admin.auth.admin.getUserById(row.auth_user_id);
 if(auth.error)throw auth.error;
 if(auth.data.user?.user_metadata?.registration_request_id!==row.id)throw Error("auth_ownership_mismatch");
 const deleted=await admin.auth.admin.deleteUser(row.auth_user_id);
 if(deleted.error)throw deleted.error; counts.auth=1;
 const verifyProfile=await admin.from("community_profiles").select("id",{count:"exact",head:true}).eq("id",row.profile_id);
 const verifySessions=await admin.from("web_client_sessions").select("id",{count:"exact",head:true}).eq("profile_id",row.profile_id);
 if(verifyProfile.error||verifySessions.error||verifyProfile.count!==0||verifySessions.count!==0)throw Error("cleanup_absence_check_failed");
}catch(error){failure=error instanceof Error&&["auth_ownership_mismatch","cleanup_absence_check_failed"].includes(error.message)?error.message:"cleanup_operation_failed";}
const finish=await admin.rpc("quata_finish_web_registration_cleanup",{
 p_id:row.id,p_token:token,p_actor:actor,p_success:failure===null,
 p_details:failure?{error_code:failure}:{row_counts:counts},
});
if(finish.error)throw finish.error;
const alert={event:failure?"cleanup_retry_scheduled":"cleanup_completed",request_id:row.id,row_counts:counts};
console.log(JSON.stringify(alert));
const webhook=Deno.env.get("QUATA_CLEANUP_ALERT_WEBHOOK");
if(webhook){
 const response=await fetch(webhook,{method:"POST",headers:{"content-type":"application/json"},body:JSON.stringify(alert)});
 if(!response.ok)throw Error("cleanup_alert_delivery_failed");
}
if(failure)throw Error(failure);
