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

/** @module vertx-kue-root-module-js/callback_kue */
var utils = require('vertx-js/util/utils');
var JobService = require('vertx-kue-service-module-js/job_service');
var Vertx = require('vertx-js/vertx');
var Message = require('vertx-js/message');

var io = Packages.io;
var JsonObject = io.vertx.core.json.JsonObject;
var JCallbackKue = Java.type('io.vertx.blueprint.kue.CallbackKue');
var Job = Java.type('io.vertx.blueprint.kue.queue.Job');

/**

 @class
*/
var CallbackKue = function(j_val) {

  var j_callbackKue = j_val;
  var that = this;
  JobService.call(this, j_val);

  /**
   Get the certain from backend by id.

   @public
   @param id {number} job id 
   @param handler {function} async result handler 
   @return {JobService}
   */
  this.getJob = function(id, handler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] ==='number' && typeof __args[1] === 'function') {
      j_callbackKue["getJob(long,io.vertx.core.Handler)"](id, function(ar) {
      if (ar.succeeded()) {
        handler(utils.convReturnDataObject(ar.result()), null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Remove a job by id.

   @public
   @param id {number} job id 
   @param handler {function} async result handler 
   @return {JobService}
   */
  this.removeJob = function(id, handler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] ==='number' && typeof __args[1] === 'function') {
      j_callbackKue["removeJob(long,io.vertx.core.Handler)"](id, function(ar) {
      if (ar.succeeded()) {
        handler(null, null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Judge whether a job with certain id exists.

   @public
   @param id {number} job id 
   @param handler {function} async result handler 
   @return {JobService}
   */
  this.existsJob = function(id, handler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] ==='number' && typeof __args[1] === 'function') {
      j_callbackKue["existsJob(long,io.vertx.core.Handler)"](id, function(ar) {
      if (ar.succeeded()) {
        handler(ar.result(), null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Get job log by id.

   @public
   @param id {number} job id 
   @param handler {function} async result handler 
   @return {JobService}
   */
  this.getJobLog = function(id, handler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] ==='number' && typeof __args[1] === 'function') {
      j_callbackKue["getJobLog(long,io.vertx.core.Handler)"](id, function(ar) {
      if (ar.succeeded()) {
        handler(utils.convReturnJson(ar.result()), null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Get a list of job in certain state in range (from, to) with order.

   @public
   @param state {string} expected job state 
   @param from {number} from 
   @param to {number} to 
   @param order {string} range order 
   @param handler {function} async result handler 
   @return {JobService}
   */
  this.jobRangeByState = function(state, from, to, order, handler) {
    var __args = arguments;
    if (__args.length === 5 && typeof __args[0] === 'string' && typeof __args[1] ==='number' && typeof __args[2] ==='number' && typeof __args[3] === 'string' && typeof __args[4] === 'function') {
      j_callbackKue["jobRangeByState(java.lang.String,long,long,java.lang.String,io.vertx.core.Handler)"](state, from, to, order, function(ar) {
      if (ar.succeeded()) {
        handler(utils.convReturnListSetDataObject(ar.result()), null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Get a list of job in certain state and type in range (from, to) with order.

   @public
   @param type {string} expected job type 
   @param state {string} expected job state 
   @param from {number} from 
   @param to {number} to 
   @param order {string} range order 
   @param handler {function} async result handler 
   @return {JobService}
   */
  this.jobRangeByType = function(type, state, from, to, order, handler) {
    var __args = arguments;
    if (__args.length === 6 && typeof __args[0] === 'string' && typeof __args[1] === 'string' && typeof __args[2] ==='number' && typeof __args[3] ==='number' && typeof __args[4] === 'string' && typeof __args[5] === 'function') {
      j_callbackKue["jobRangeByType(java.lang.String,java.lang.String,long,long,java.lang.String,io.vertx.core.Handler)"](type, state, from, to, order, function(ar) {
      if (ar.succeeded()) {
        handler(utils.convReturnListSetDataObject(ar.result()), null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Get a list of job in range (from, to) with order.

   @public
   @param from {number} from 
   @param to {number} to 
   @param order {string} range order 
   @param handler {function} async result handler 
   @return {JobService}
   */
  this.jobRange = function(from, to, order, handler) {
    var __args = arguments;
    if (__args.length === 4 && typeof __args[0] ==='number' && typeof __args[1] ==='number' && typeof __args[2] === 'string' && typeof __args[3] === 'function') {
      j_callbackKue["jobRange(long,long,java.lang.String,io.vertx.core.Handler)"](from, to, order, function(ar) {
      if (ar.succeeded()) {
        handler(utils.convReturnListSetDataObject(ar.result()), null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Get cardinality by job type and state.

   @public
   @param type {string} job type 
   @param state {Object} job state 
   @param handler {function} async result handler 
   @return {JobService}
   */
  this.cardByType = function(type, state, handler) {
    var __args = arguments;
    if (__args.length === 3 && typeof __args[0] === 'string' && typeof __args[1] === 'string' && typeof __args[2] === 'function') {
      j_callbackKue["cardByType(java.lang.String,io.vertx.blueprint.kue.queue.JobState,io.vertx.core.Handler)"](type, io.vertx.blueprint.kue.queue.JobState.valueOf(state), function(ar) {
      if (ar.succeeded()) {
        handler(utils.convReturnLong(ar.result()), null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Get cardinality by job state.

   @public
   @param state {Object} job state 
   @param handler {function} async result handler 
   @return {JobService}
   */
  this.card = function(state, handler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'function') {
      j_callbackKue["card(io.vertx.blueprint.kue.queue.JobState,io.vertx.core.Handler)"](io.vertx.blueprint.kue.queue.JobState.valueOf(state), function(ar) {
      if (ar.succeeded()) {
        handler(utils.convReturnLong(ar.result()), null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Get cardinality of completed jobs.

   @public
   @param type {string} job type; if null, then return global metrics 
   @param handler {function} async result handler 
   @return {JobService}
   */
  this.completeCount = function(type, handler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'function') {
      j_callbackKue["completeCount(java.lang.String,io.vertx.core.Handler)"](type, function(ar) {
      if (ar.succeeded()) {
        handler(utils.convReturnLong(ar.result()), null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Get cardinality of failed jobs.

   @public
   @param type {string} job type; if null, then return global metrics 
   @param handler {function} 
   @return {JobService}
   */
  this.failedCount = function(type, handler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'function') {
      j_callbackKue["failedCount(java.lang.String,io.vertx.core.Handler)"](type, function(ar) {
      if (ar.succeeded()) {
        handler(utils.convReturnLong(ar.result()), null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Get cardinality of inactive jobs.

   @public
   @param type {string} job type; if null, then return global metrics 
   @param handler {function} 
   @return {JobService}
   */
  this.inactiveCount = function(type, handler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'function') {
      j_callbackKue["inactiveCount(java.lang.String,io.vertx.core.Handler)"](type, function(ar) {
      if (ar.succeeded()) {
        handler(utils.convReturnLong(ar.result()), null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Get cardinality of active jobs.

   @public
   @param type {string} job type; if null, then return global metrics 
   @param handler {function} 
   @return {JobService}
   */
  this.activeCount = function(type, handler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'function') {
      j_callbackKue["activeCount(java.lang.String,io.vertx.core.Handler)"](type, function(ar) {
      if (ar.succeeded()) {
        handler(utils.convReturnLong(ar.result()), null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Get cardinality of delayed jobs.

   @public
   @param type {string} job type; if null, then return global metrics 
   @param handler {function} 
   @return {JobService}
   */
  this.delayedCount = function(type, handler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'function') {
      j_callbackKue["delayedCount(java.lang.String,io.vertx.core.Handler)"](type, function(ar) {
      if (ar.succeeded()) {
        handler(utils.convReturnLong(ar.result()), null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Get the job types present.

   @public
   @param handler {function} async result handler 
   @return {JobService}
   */
  this.getAllTypes = function(handler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_callbackKue["getAllTypes(io.vertx.core.Handler)"](function(ar) {
      if (ar.succeeded()) {
        handler(ar.result(), null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Return job ids with the given .

   @public
   @param state {Object} job state 
   @param handler {function} async result handler 
   @return {JobService}
   */
  this.getIdsByState = function(state, handler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'function') {
      j_callbackKue["getIdsByState(io.vertx.blueprint.kue.queue.JobState,io.vertx.core.Handler)"](io.vertx.blueprint.kue.queue.JobState.valueOf(state), function(ar) {
      if (ar.succeeded()) {
        handler(utils.convReturnListSetLong(ar.result()), null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Get queue work time in milliseconds.

   @public
   @param handler {function} async result handler 
   @return {JobService}
   */
  this.getWorkTime = function(handler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_callbackKue["getWorkTime(io.vertx.core.Handler)"](function(ar) {
      if (ar.succeeded()) {
        handler(utils.convReturnLong(ar.result()), null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public
   @param type {string} 
   @param data {Object} 
   @return {Object}
   */
  this.createJob = function(type, data) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && (typeof __args[1] === 'object' && __args[1] != null)) {
      return utils.convReturnDataObject(j_callbackKue["createJob(java.lang.String,io.vertx.core.json.JsonObject)"](type, utils.convParamJsonObject(data)));
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public
   @param eventType {string} 
   @param handler {function} 
   @return {CallbackKue}
   */
  this.on = function(eventType, handler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'function') {
      j_callbackKue["on(java.lang.String,io.vertx.core.Handler)"](eventType, function(jVal) {
        handler(utils.convReturnVertxGen(Message, jVal, undefined));
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public
   @param job {Object} 
   @param handler {function} 
   @return {CallbackKue}
   */
  this.saveJob = function(job, handler) {
    var __args = arguments;
    if (__args.length === 2 && (typeof __args[0] === 'object' && __args[0] != null) && typeof __args[1] === 'function') {
      j_callbackKue["saveJob(io.vertx.blueprint.kue.queue.Job,io.vertx.core.Handler)"](job != null ? new Job(new JsonObject(Java.asJSONCompatible(job))) : null, function (ar) {
      if (ar.succeeded()) {
        handler(utils.convReturnDataObject(ar.result()), null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public
   @param job {Object} 
   @param complete {number} 
   @param total {number} 
   @param handler {function} 
   @return {CallbackKue}
   */
  this.jobProgress = function(job, complete, total, handler) {
    var __args = arguments;
    if (__args.length === 4 && (typeof __args[0] === 'object' && __args[0] != null) && typeof __args[1] ==='number' && typeof __args[2] ==='number' && typeof __args[3] === 'function') {
      j_callbackKue["jobProgress(io.vertx.blueprint.kue.queue.Job,int,int,io.vertx.core.Handler)"](job != null ? new Job(new JsonObject(Java.asJSONCompatible(job))) : null, complete, total, function (ar) {
      if (ar.succeeded()) {
        handler(utils.convReturnDataObject(ar.result()), null);
      } else {
        handler(null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public
   @param job {Object} 
   @return {CallbackKue}
   */
  this.jobDone = function(job) {
    var __args = arguments;
    if (__args.length === 1 && (typeof __args[0] === 'object' && __args[0] != null)) {
      j_callbackKue["jobDone(io.vertx.blueprint.kue.queue.Job)"](job != null ? new Job(new JsonObject(Java.asJSONCompatible(job))) : null);
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public
   @param job {Object} 
   @param ex {todo} 
   @return {CallbackKue}
   */
  this.jobDoneFail = function(job, ex) {
    var __args = arguments;
    if (__args.length === 2 && (typeof __args[0] === 'object' && __args[0] != null) && typeof __args[1] === 'object') {
      j_callbackKue["jobDoneFail(io.vertx.blueprint.kue.queue.Job,java.lang.Throwable)"](job != null ? new Job(new JsonObject(Java.asJSONCompatible(job))) : null, utils.convParamThrowable(ex));
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public
   @param type {string} 
   @param n {number} 
   @param handler {function} 
   @return {CallbackKue}
   */
  this.process = function(type, n, handler) {
    var __args = arguments;
    if (__args.length === 3 && typeof __args[0] === 'string' && typeof __args[1] ==='number' && typeof __args[2] === 'function') {
      j_callbackKue["process(java.lang.String,int,io.vertx.core.Handler)"](type, n, function(jVal) {
      handler(utils.convReturnDataObject(jVal));
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public
   @param type {string} 
   @param n {number} 
   @param handler {function} 
   @return {CallbackKue}
   */
  this.processBlocking = function(type, n, handler) {
    var __args = arguments;
    if (__args.length === 3 && typeof __args[0] === 'string' && typeof __args[1] ==='number' && typeof __args[2] === 'function') {
      j_callbackKue["processBlocking(java.lang.String,int,io.vertx.core.Handler)"](type, n, function(jVal) {
      handler(utils.convReturnDataObject(jVal));
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  // A reference to the underlying Java delegate
  // NOTE! This is an internal API and must not be used in user code.
  // If you rely on this property your code is likely to break if we change it / remove it without warning.
  this._jdel = j_callbackKue;
};

CallbackKue._jclass = utils.getJavaClass("io.vertx.blueprint.kue.CallbackKue");
CallbackKue._jtype = {
  accept: function (obj) {
    return CallbackKue._jclass.isInstance(obj._jdel);
  },
  wrap: function (jdel) {
    var obj = Object.create(CallbackKue.prototype, {});
    CallbackKue.apply(obj, arguments);
    return obj;
  },
  unwrap: function (obj) {
    return obj._jdel;
  }
};
CallbackKue._create = function (jdel) {
  var obj = Object.create(CallbackKue.prototype, {});
  CallbackKue.apply(obj, arguments);
  return obj;
}
/**

 @memberof module:vertx-kue-root-module-js/callback_kue
 @param vertx {Vertx} 
 @param config {Object} 
 @return {CallbackKue}
 */
CallbackKue.createKue = function(vertx, config) {
  var __args = arguments;
  if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && (typeof __args[1] === 'object' && __args[1] != null)) {
    return utils.convReturnVertxGen(CallbackKue, JCallbackKue["createKue(io.vertx.core.Vertx,io.vertx.core.json.JsonObject)"](vertx._jdel, utils.convParamJsonObject(config)));
  } else throw new TypeError('function invoked with invalid arguments');
};

module.exports = CallbackKue;