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

import rx.Observable;
import io.vertx.core.json.JsonArray;

import java.util.List;

import io.vertx.blueprint.kue.queue.Job;
import io.vertx.rxjava.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * Vert.x Blueprint - Job Queue
 * Job Service Interface
 * <p>
 * <p/>
 * NOTE: This class has been automatically generated from the {@link io.vertx.blueprint.kue.service.JobService original} non RX-ified interface using Vert.x codegen.
 */

public class JobService {

  final io.vertx.blueprint.kue.service.JobService delegate;

  public JobService(io.vertx.blueprint.kue.service.JobService delegate) {
    this.delegate = delegate;
  }

  public Object getDelegate() {
    return delegate;
  }

  public static JobService create(Vertx vertx, JsonObject config) {
    JobService ret = JobService.newInstance(io.vertx.blueprint.kue.service.JobService.create((io.vertx.core.Vertx) vertx.getDelegate(), config));
    return ret;
  }

  public static JobService createProxy(Vertx vertx, String address) {
    JobService ret = JobService.newInstance(io.vertx.blueprint.kue.service.JobService.createProxy((io.vertx.core.Vertx) vertx.getDelegate(), address));
    return ret;
  }

  /**
   * Get job from backend by id
   *
   * @param id      job id
   * @param handler async result handler
   * @return
   */
  public JobService getJob(long id, Handler<AsyncResult<Job>> handler) {
    delegate.getJob(id, handler);
    return this;
  }

  /**
   * Get job from backend by id
   *
   * @param id job id
   * @return
   */
  public Observable<Job> getJobObservable(long id) {
    io.vertx.rx.java.ObservableFuture<Job> handler = io.vertx.rx.java.RxHelper.observableFuture();
    getJob(id, handler.toHandler());
    return handler;
  }

  /**
   * Remove a job by id
   *
   * @param id      job id
   * @param handler async result handler
   * @return
   */
  public JobService removeJob(long id, Handler<AsyncResult<Void>> handler) {
    delegate.removeJob(id, new Handler<AsyncResult<java.lang.Void>>() {
      public void handle(AsyncResult<java.lang.Void> ar) {
        if (ar.succeeded()) {
          handler.handle(io.vertx.core.Future.succeededFuture(ar.result()));
        } else {
          handler.handle(io.vertx.core.Future.failedFuture(ar.cause()));
        }
      }
    });
    return this;
  }

  /**
   * Remove a job by id
   *
   * @param id job id
   * @return
   */
  public Observable<Void> removeJobObservable(long id) {
    io.vertx.rx.java.ObservableFuture<Void> handler = io.vertx.rx.java.RxHelper.observableFuture();
    removeJob(id, handler.toHandler());
    return handler;
  }

  /**
   * Judge whether a job with certain id exists
   *
   * @param id      job id
   * @param handler async result handler
   * @return
   */
  public JobService existsJob(long id, Handler<AsyncResult<Boolean>> handler) {
    delegate.existsJob(id, handler);
    return this;
  }

  /**
   * Judge whether a job with certain id exists
   *
   * @param id job id
   * @return
   */
  public Observable<Boolean> existsJobObservable(long id) {
    io.vertx.rx.java.ObservableFuture<Boolean> handler = io.vertx.rx.java.RxHelper.observableFuture();
    existsJob(id, handler.toHandler());
    return handler;
  }

  /**
   * Get job log by id
   *
   * @param id      job id
   * @param handler async result handler
   * @return
   */
  public JobService getJobLog(long id, Handler<AsyncResult<JsonArray>> handler) {
    delegate.getJobLog(id, handler);
    return this;
  }

  /**
   * Get job log by id
   *
   * @param id job id
   * @return
   */
  public Observable<JsonArray> getJobLogObservable(long id) {
    io.vertx.rx.java.ObservableFuture<JsonArray> handler = io.vertx.rx.java.RxHelper.observableFuture();
    getJobLog(id, handler.toHandler());
    return handler;
  }

  /**
   * Get a list of job in certain state in range (from, to) with order
   *
   * @param state   expected job state
   * @param from    from
   * @param to      to
   * @param order   range order
   * @param handler async result handler
   * @return
   */
  public JobService jobRangeByState(String state, long from, long to, String order, Handler<AsyncResult<List<Job>>> handler) {
    delegate.jobRangeByState(state, from, to, order, handler);
    return this;
  }

  /**
   * Get a list of job in certain state in range (from, to) with order
   *
   * @param state expected job state
   * @param from  from
   * @param to    to
   * @param order range order
   * @return
   */
  public Observable<List<Job>> jobRangeByStateObservable(String state, long from, long to, String order) {
    io.vertx.rx.java.ObservableFuture<List<Job>> handler = io.vertx.rx.java.RxHelper.observableFuture();
    jobRangeByState(state, from, to, order, handler.toHandler());
    return handler;
  }

  /**
   * Get a list of job in range (from, to) with order
   *
   * @param from    from
   * @param to      to
   * @param order   range order
   * @param handler async result handler
   * @return
   */
  public JobService jobRange(long from, long to, String order, Handler<AsyncResult<List<Job>>> handler) {
    delegate.jobRange(from, to, order, handler);
    return this;
  }

  /**
   * Get a list of job in range (from, to) with order
   *
   * @param from  from
   * @param to    to
   * @param order range order
   * @return
   */
  public Observable<List<Job>> jobRangeObservable(long from, long to, String order) {
    io.vertx.rx.java.ObservableFuture<List<Job>> handler = io.vertx.rx.java.RxHelper.observableFuture();
    jobRange(from, to, order, handler.toHandler());
    return handler;
  }


  public static JobService newInstance(io.vertx.blueprint.kue.service.JobService arg) {
    return arg != null ? new JobService(arg) : null;
  }
}
