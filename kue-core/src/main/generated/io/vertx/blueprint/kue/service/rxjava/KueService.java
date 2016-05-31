/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.blueprint.kue.service.rxjava;

import java.util.Map;
import io.vertx.lang.rxjava.InternalHelper;
import rx.Observable;
import io.vertx.blueprint.kue.queue.Job;
import io.vertx.rxjava.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * Vert.x Blueprint - Job Queue
 * Kue Service Interface
 *
 * <p/>
 * NOTE: This class has been automatically generated from the {@link io.vertx.blueprint.kue.service.KueService original} non RX-ified interface using Vert.x codegen.
 */

public class KueService {

  final io.vertx.blueprint.kue.service.KueService delegate;

  public KueService(io.vertx.blueprint.kue.service.KueService delegate) {
    this.delegate = delegate;
  }

  public Object getDelegate() {
    return delegate;
  }

  public static KueService create(Vertx vertx, JsonObject config) {
    KueService ret = KueService.newInstance(io.vertx.blueprint.kue.service.KueService.create((io.vertx.core.Vertx) vertx.getDelegate(), config));
    return ret;
  }

  public static KueService createProxy(Vertx vertx, String address) {
    KueService ret = KueService.newInstance(io.vertx.blueprint.kue.service.KueService.createProxy((io.vertx.core.Vertx) vertx.getDelegate(), address));
    return ret;
  }

  public void process(String type, int n, Handler<AsyncResult<JsonObject>> handler) {
    this.delegate.process(type, n, handler);
  }

  public Observable<JsonObject> processObservable(String type, int n) {
    io.vertx.rx.java.ObservableFuture<JsonObject> handler = io.vertx.rx.java.RxHelper.observableFuture();
    process(type, n, handler.toHandler());
    return handler;
  }

  public void saveJob(Job job) {
    this.delegate.saveJob(job);
  }

  public void updateJob(Job job) {
    this.delegate.updateJob(job);
  }


  public static KueService newInstance(io.vertx.blueprint.kue.service.KueService arg) {
    return arg != null ? new KueService(arg) : null;
  }
}
