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

package io.vertx.blueprint.kue.rxjava;

import java.util.Map;

import rx.Observable;
import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.service.rxjava.JobService;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * Vert.x Blueprint - Job Queue
 * Callback-based Kue Interface
 * For Vert.x Codegen
 * <p>
 * <p/>
 * NOTE: This class has been automatically generated from the {@link io.vertx.blueprint.kue.CallbackKue original} non RX-ified interface using Vert.x codegen.
 */

public class CallbackKue extends JobService {

  final io.vertx.blueprint.kue.CallbackKue delegate;

  public CallbackKue(io.vertx.blueprint.kue.CallbackKue delegate) {
    super(delegate);
    this.delegate = delegate;
  }

  public Object getDelegate() {
    return delegate;
  }

  public static CallbackKue createKue(Vertx vertx, JsonObject config) {
    CallbackKue ret = CallbackKue.newInstance(io.vertx.blueprint.kue.CallbackKue.createKue((io.vertx.core.Vertx) vertx.getDelegate(), config));
    return ret;
  }

  public Job createJob(String type, JsonObject data) {
    Job ret = delegate.createJob(type, data);
    return ret;
  }

  public <R> CallbackKue on(String eventType, Handler<Message<R>> handler) {
    delegate.on(eventType, new Handler<io.vertx.core.eventbus.Message<R>>() {
      public void handle(io.vertx.core.eventbus.Message<R> event) {
        handler.handle(Message.newInstance(event));
      }
    });
    return this;
  }

  public CallbackKue saveJob(Job job, Handler<AsyncResult<Job>> handler) {
    delegate.saveJob(job, handler);
    return this;
  }

  public Observable<Job> saveJobObservable(Job job) {
    io.vertx.rx.java.ObservableFuture<Job> handler = io.vertx.rx.java.RxHelper.observableFuture();
    saveJob(job, handler.toHandler());
    return handler;
  }

  public CallbackKue jobDone(Job job) {
    delegate.jobDone(job);
    return this;
  }

  public CallbackKue process(String type, int n, Handler<AsyncResult<Job>> handler) {
    delegate.process(type, n, handler);
    return this;
  }

  public Observable<Job> processObservable(String type, int n) {
    io.vertx.rx.java.ObservableFuture<Job> handler = io.vertx.rx.java.RxHelper.observableFuture();
    process(type, n, handler.toHandler());
    return handler;
  }

  public CallbackKue processBlocking(String type, int n, Handler<AsyncResult<Job>> handler) {
    delegate.processBlocking(type, n, handler);
    return this;
  }

  public Observable<Job> processBlockingObservable(String type, int n) {
    io.vertx.rx.java.ObservableFuture<Job> handler = io.vertx.rx.java.RxHelper.observableFuture();
    processBlocking(type, n, handler.toHandler());
    return handler;
  }


  public static CallbackKue newInstance(io.vertx.blueprint.kue.CallbackKue arg) {
    return arg != null ? new CallbackKue(arg) : null;
  }
}
