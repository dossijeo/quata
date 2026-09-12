import test from 'node:test';
import assert from 'node:assert/strict';
import {selectAndroidRefreshRejection,readAndroidRefreshRejection} from './e2e-fixtures/chat-deep-link-android-rejection.mjs';
const scope={pid:'1234',startedAt:'1800000000.123456789',endedAt:'1800000002.123456789'};
const line=(time='1800000001.123456',pid='1234',status='400')=>`${time} ${pid} 5678 W SupabaseHttpClient: Supabase session refresh failed with status=${status}`;
test('selects only a numeric real-status witness in the delivered PID and window',()=>{
  const result=selectAndroidRefreshRejection({...scope,text:'private error body ignored\n'+line()+'\n'+line('1800000001.5','1234','401')});
  assert.deepEqual(result,{...scope,observed:true,status:400,timestamp:'1800000001.123456'});
  assert.equal(JSON.stringify(result).includes('private'),false);
});
for(const [name,text] of [
  ['old',line('1800000000.123456788')],['future',line('1800000002.123456790')],['foreign PID',line(undefined,'9999')],
  ['network error',line(undefined,undefined,'null')],['server failure',line(undefined,undefined,'500')],
  ['extra body',line()+' body=private'],['wrong tag',line().replace('SupabaseHttpClient:','Other:')],
])test(`rejects ${name} as rejection evidence`,()=>assert.throws(()=>selectAndroidRefreshRejection({...scope,text}),/deep_link_android_rejection_unverified/));
test('rejects reversed time and malformed PID before evidence selection',()=>{
  for(const extra of [{endedAt:'1800000000.0'},{pid:'1234 5678'},{startedAt:'not-time'}])
    assert.throws(()=>selectAndroidRefreshRejection({...scope,...extra,text:line()}));
});
for(const scenario of ['success','PID changed','transport failure'])test(`bounded logcat read ${scenario}`,async()=>{
  const calls=[];let pidReads=0;
  const execute=async(file,args,options)=>{
    calls.push(args);assert.equal(file,'synthetic-adb');assert.equal(options.windowsHide,true);
    if(args.includes('pidof'))return {stdout:++pidReads===2&&scenario==='PID changed'?'9999\n':'1234\n'};
    if(args.includes('date'))return {stdout:scope.endedAt+'\n'};
    if(args.includes('logcat')) {
      if(scenario==='transport failure')throw Error('private body must not escape');
      return {stdout:line()};
    }
    assert.fail('unexpected command');
  };
  const action=readAndroidRefreshRejection({adb:'synthetic-adb',serial:'emulator-5560',...scope,execute});
  if(scenario==='success')assert.equal((await action).status,400);
  else await assert.rejects(action,{message:'deep_link_android_rejection_unverified'});
  assert.ok(calls.some(args=>args.includes('--pid=1234')&&args.includes(scope.startedAt)&&args.includes('*:S')));
  assert.equal(calls.some(args=>args.includes('-c')),false);
});
