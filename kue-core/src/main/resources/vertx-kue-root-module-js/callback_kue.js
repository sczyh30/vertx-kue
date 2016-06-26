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
var JCallbackKue = io.vertx.blueprint.kue.CallbackKue;
var Job = io.vertx.blueprint.kue.queue.Job;

/**
 Vert.x Blueprint - Job Queue
 Callback-based Kue Interface
 For Vert.x Codegen to support polyglot languages

 @class
 */
var CallbackKue = function (j_val) {

  var j_callbackKue = j_val;
  var that = this;
  JobService.call(this, j_val);

  /**

   @public
   @param type {string} 
   @param data {Object} 
   @return {Object}
   */
  this.createJob = function (type, data) {
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
  this.on = function (eventType, handler) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'function') {
      j_callbackKue["on(java.lang.String,io.vertx.core.Handler)"](eventType, function (jVal) {
        handler(utils.convReturnVertxGen(jVal, Message));
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
  this.saveJob = function (job, handler) {
    var __args = arguments;
    if (__args.length === 2 && (typeof __args[0] === 'object' && __args[0] != null) && typeof __args[1] === 'function') {
      j_callbackKue["saveJob(io.vertx.blueprint.kue.queue.Job,io.vertx.core.Handler)"](job != null ? new Job(new JsonObject(JSON.stringify(job))) : null, function (ar) {
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
  this.jobProgress = function (job, complete, total, handler) {
    var __args = arguments;
    if (__args.length === 4 && (typeof __args[0] === 'object' && __args[0] != null) && typeof __args[1] === 'number' && typeof __args[2] === 'number' && typeof __args[3] === 'function') {
      j_callbackKue["jobProgress(io.vertx.blueprint.kue.queue.Job,int,int,io.vertx.core.Handler)"](job != null ? new Job(new JsonObject(JSON.stringify(job))) : null, complete, total, function (ar) {
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
  this.jobDone = function (job) {
    var __args = arguments;
    if (__args.length === 1 && (typeof __args[0] === 'object' && __args[0] != null)) {
      j_callbackKue["jobDone(io.vertx.blueprint.kue.queue.Job)"](job != null ? new Job(new JsonObject(JSON.stringify(job))) : null);
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public
   @param job {Object}
   @param ex {todo} 
   @return {CallbackKue}
   */
  this.jobDoneFail = function (job, ex) {
    var __args = arguments;
    if (__args.length === 2 && (typeof __args[0] === 'object' && __args[0] != null) && typeof __args[1] === 'object') {
      j_callbackKue["jobDoneFail(io.vertx.blueprint.kue.queue.Job,java.lang.Throwable)"](job != null ? new Job(new JsonObject(JSON.stringify(job))) : null, utils.convParamThrowable(ex));
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
  this.process = function (type, n, handler) {
    var __args = arguments;
    if (__args.length === 3 && typeof __args[0] === 'string' && typeof __args[1] === 'number' && typeof __args[2] === 'function') {
      j_callbackKue["process(java.lang.String,int,io.vertx.core.Handler)"](type, n, function (jVal) {
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
  this.processBlocking = function (type, n, handler) {
    var __args = arguments;
    if (__args.length === 3 && typeof __args[0] === 'string' && typeof __args[1] === 'number' && typeof __args[2] === 'function') {
      j_callbackKue["processBlocking(java.lang.String,int,io.vertx.core.Handler)"](type, n, function (jVal) {
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

/**

 @memberof module:vertx-kue-root-module-js/callback_kue
 @param vertx {Vertx} 
 @param config {Object} 
 @return {CallbackKue}
 */
CallbackKue.createKue = function (vertx, config) {
  var __args = arguments;
  if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && (typeof __args[1] === 'object' && __args[1] != null)) {
    return utils.convReturnVertxGen(JCallbackKue["createKue(io.vertx.core.Vertx,io.vertx.core.json.JsonObject)"](vertx._jdel, utils.convParamJsonObject(config)), CallbackKue);
  } else throw new TypeError('function invoked with invalid arguments');
};

// We export the Constructor function
module.exports = CallbackKue;