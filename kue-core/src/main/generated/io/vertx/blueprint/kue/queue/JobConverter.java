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

package io.vertx.blueprint.kue.queue;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter for {@link io.vertx.blueprint.kue.queue.Job}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.blueprint.kue.queue.Job} original class using Vert.x codegen.
 */
public class JobConverter {

  public static void fromJson(JsonObject json, Job obj) {
    if (json.getValue("data") instanceof JsonObject) {
      obj.setData(((JsonObject) json.getValue("data")).copy());
    }
    if (json.getValue("delay") instanceof Number) {
      obj.setDelay(((Number) json.getValue("delay")).intValue());
    }
    if (json.getValue("id") instanceof Number) {
      obj.setId(((Number) json.getValue("id")).longValue());
    }
    if (json.getValue("jobMetrics") instanceof JsonObject) {
      obj.setJobMetrics(new io.vertx.blueprint.kue.queue.JobMetrics((JsonObject) json.getValue("jobMetrics")));
    }
    if (json.getValue("priority") instanceof String) {
      obj.setPriority(io.vertx.blueprint.kue.queue.Priority.valueOf((String) json.getValue("priority")));
    }
    if (json.getValue("progress") instanceof Number) {
      obj.setProgress(((Number) json.getValue("progress")).intValue());
    }
    if (json.getValue("result") instanceof JsonObject) {
      obj.setResult(((JsonObject) json.getValue("result")).copy());
    }
    if (json.getValue("state") instanceof String) {
      obj.setState(io.vertx.blueprint.kue.queue.JobState.valueOf((String) json.getValue("state")));
    }
    if (json.getValue("type") instanceof String) {
      obj.setType((String) json.getValue("type"));
    }
    if (json.getValue("zid") instanceof String) {
      obj.setZid((String) json.getValue("zid"));
    }
  }

  public static void toJson(Job obj, JsonObject json) {
    if (obj.getData() != null) {
      json.put("data", obj.getData());
    }
    json.put("delay", obj.getDelay());
    json.put("id", obj.getId());
    if (obj.getJobMetrics() != null) {
      json.put("jobMetrics", obj.getJobMetrics().toJson());
    }
    if (obj.getPriority() != null) {
      json.put("priority", obj.getPriority().name());
    }
    json.put("progress", obj.getProgress());
    if (obj.getResult() != null) {
      json.put("result", obj.getResult());
    }
    if (obj.getState() != null) {
      json.put("state", obj.getState().name());
    }
    if (obj.getType() != null) {
      json.put("type", obj.getType());
    }
    if (obj.getZid() != null) {
      json.put("zid", obj.getZid());
    }
  }
}