import test from "node:test";
import assert from "node:assert/strict";
import { opaqueRegistrationDelay } from "./response-policy.mjs";

test("applies floor and bounded jitter to fast existing/new paths alike", async () => {
  const sleeps=[];
  await opaqueRegistrationDelay(100,{now:()=>200,random:()=>0.5,sleep:async(ms)=>sleeps.push(ms),floorMs:900,jitterMs:200});
  assert.deepEqual(sleeps,[900]);
});
test("never sleeps negative after a slow saga", async () => {
  const sleeps=[];
  await opaqueRegistrationDelay(0,{now:()=>2000,random:()=>0,sleep:async(ms)=>sleeps.push(ms)});
  assert.deepEqual(sleeps,[0]);
});
