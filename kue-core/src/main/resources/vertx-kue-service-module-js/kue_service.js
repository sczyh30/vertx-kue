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

/** @module vertx-kue-service-module-js/kue_service */
var utils = require('vertx-js/util/utils');
var Vertx = require('vertx-js/vertx');

var io = Packages.io;
var JsonObject = io.vertx.core.json.JsonObject;
var JKueService = io.vertx.blueprint.kue.service.KueService;
var Job = io.vertx.blueprint.kue.queue.Job;

/**
 Vert.x Blueprint - Job Queue
 Kue Service Interface

 @class
 */
var KueService = function (j_val) {

  var j_kueService = j_val;
  var that = this;

  /**
   Process a job in asynchronous way

   @public
   @param type {string} job type
   @param n {number} job process times
   @param handler {function} job process handler 
   */
  this.process = function (type, n, handler) {
    var __args = arguments;
    if (__args.length === 3 && typeof __args[0] === 'string' && typeof __args[1] === 'number' && typeof __args[2] === 'function') {
      j_kueService["process(java.lang.String,int,io.vertx.core.Handler)"](type, n, function (ar) {
        if (ar.succeeded()) {
          handler(utils.convReturnDataObject(ar.result()), null);
        } else {
          handler(null, ar.cause());
        }
      });
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Process a job in synchronous and blocking way

   @public
   @param type {string} job type
   @param n {number} job process times
   @param handler {function} job process handler
   */
  this.processBlocking = function (type, n, handler) {
    var __args = arguments;
    if (__args.length === 3 && typeof __args[0] === 'string' && typeof __args[1] === 'number' && typeof __args[2] === 'function') {
      j_kueService["processBlocking(java.lang.String,int,io.vertx.core.Handler)"](type, n, function (ar) {
        if (ar.succeeded()) {
          handler(utils.convReturnDataObject(ar.result()), null);
        } else {
          handler(null, ar.cause());
        }
      });
    } else throw new TypeError('function invoked with invalid arguments');
  };

  // A reference to the underlying Java delegate
  // NOTE! This is an internal API and must not be used in user code.
  // If you rely on this property your code is likely to break if we change it / remove it without warning.
  this._jdel = j_kueService;
};

/**

 @memberof module:vertx-kue-service-module-js/kue_service
 @param vertx {Vertx} 
 @param config {Object} 
 @return {KueService}
 */
KueService.create = function (vertx, config) {
  var __args = arguments;
  if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && (typeof __args[1] === 'object' && __args[1] != null)) {
    return utils.convReturnVertxGen(JKueService["create(io.vertx.core.Vertx,io.vertx.core.json.JsonObject)"](vertx._jdel, utils.convParamJsonObject(config)), KueService);
  } else throw new TypeError('function invoked with invalid arguments');
};

/**

 @memberof module:vertx-kue-service-module-js/kue_service
 @param vertx {Vertx} 
 @param address {string} 
 @return {KueService}
 */
KueService.createProxy = function (vertx, address) {
  var __args = arguments;
  if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && typeof __args[1] === 'string') {
    return utils.convReturnVertxGen(JKueService["createProxy(io.vertx.core.Vertx,java.lang.String)"](vertx._jdel, address), KueService);
  } else throw new TypeError('function invoked with invalid arguments');
};

// We export the Constructor function
module.exports = KueService;