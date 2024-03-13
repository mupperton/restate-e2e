// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import * as restate from "@restatedev/restate-sdk";
import { v4 as uuidv4 } from "uuid";
import { counterApi } from "./counter";
import { REGISTRY } from "./services";

export const NonDeterministicServiceFQN = "NonDeterministic";

const Counter: counterApi = { path: "Counter" };

REGISTRY.add({
  fqdn: NonDeterministicServiceFQN,
  binder: (e) => e.object(service),
});

const invocationCounts = new Map<string, number>();

function doLeftAction(ctx: restate.ObjectContext): boolean {
  const newValue = (invocationCounts.get(ctx.key()) ?? 0) + 1;
  invocationCounts.set(ctx.key(), newValue);
  return newValue % 2 == 1;
}

function incrementCounter(ctx: restate.ObjectContext) {
  ctx.objectSend(Counter, ctx.key()).add(1);
}

const service = restate.object(NonDeterministicServiceFQN, {
  async leftSleepRightCall(ctx: restate.ObjectContext) {
    if (doLeftAction(ctx)) {
      await ctx.sleep(100);
    } else {
      await ctx.object(Counter, "abc").get();
    }
    incrementCounter(ctx);
  },

  async callDifferentMethod(ctx: restate.ObjectContext) {
    if (doLeftAction(ctx)) {
      await ctx.object(Counter, "abc").get();
    } else {
      await ctx.object(Counter, "abc").reset();
    }
    incrementCounter(ctx);
  },

  async backgroundInvokeWithDifferentTargets(ctx: restate.ObjectContext) {
    if (doLeftAction(ctx)) {
      ctx.objectSend(Counter, "abc").get();
    } else {
      ctx.objectSend(Counter, "abc").reset();
    }
    await ctx.sleep(100);
    incrementCounter(ctx);
  },

  async setDifferentKey(ctx: restate.ObjectContext) {
    if (doLeftAction(ctx)) {
      ctx.set("a", "my-state");
    } else {
      ctx.set("b", "my-state");
    }
    await ctx.sleep(100);
    incrementCounter(ctx);
  },

  async setDifferentValue(ctx: restate.ObjectContext) {
    ctx.set("a", uuidv4());
    await ctx.sleep(100);
    incrementCounter(ctx);
  },
});
