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
    if (json.getValue("attempts") instanceof Number) {
      obj.setAttempts(((Number) json.getValue("attempts")).intValue());
    }
    if (json.getValue("backoff") instanceof JsonObject) {
      obj.setBackoff(((JsonObject) json.getValue("backoff")).copy());
    }
    if (json.getValue("created_at") instanceof Number) {
      obj.setCreated_at(((Number) json.getValue("created_at")).longValue());
    }
    if (json.getValue("data") instanceof JsonObject) {
      obj.setData(((JsonObject) json.getValue("data")).copy());
    }
    if (json.getValue("delay") instanceof Number) {
      obj.setDelay(((Number) json.getValue("delay")).longValue());
    }
    if (json.getValue("duration") instanceof Number) {
      obj.setDuration(((Number) json.getValue("duration")).longValue());
    }
    if (json.getValue("failed_at") instanceof Number) {
      obj.setFailed_at(((Number) json.getValue("failed_at")).longValue());
    }
    if (json.getValue("id") instanceof Number) {
      obj.setId(((Number) json.getValue("id")).longValue());
    }
    if (json.getValue("max_attempts") instanceof Number) {
      obj.setMax_attempts(((Number) json.getValue("max_attempts")).intValue());
    }
    if (json.getValue("priority") instanceof String) {
      obj.setPriority(io.vertx.blueprint.kue.queue.Priority.valueOf((String) json.getValue("priority")));
    }
    if (json.getValue("progress") instanceof Number) {
      obj.setProgress(((Number) json.getValue("progress")).intValue());
    }
    if (json.getValue("promote_at") instanceof Number) {
      obj.setPromote_at(((Number) json.getValue("promote_at")).longValue());
    }
    if (json.getValue("removeOnComplete") instanceof Boolean) {
      obj.setRemoveOnComplete((Boolean) json.getValue("removeOnComplete"));
    }
    if (json.getValue("result") instanceof JsonObject) {
      obj.setResult(((JsonObject) json.getValue("result")).copy());
    }
    if (json.getValue("started_at") instanceof Number) {
      obj.setStarted_at(((Number) json.getValue("started_at")).longValue());
    }
    if (json.getValue("state") instanceof String) {
      obj.setState(io.vertx.blueprint.kue.queue.JobState.valueOf((String) json.getValue("state")));
    }
    if (json.getValue("ttl") instanceof Number) {
      obj.setTtl(((Number) json.getValue("ttl")).intValue());
    }
    if (json.getValue("type") instanceof String) {
      obj.setType((String) json.getValue("type"));
    }
    if (json.getValue("updated_at") instanceof Number) {
      obj.setUpdated_at(((Number) json.getValue("updated_at")).longValue());
    }
    if (json.getValue("zid") instanceof String) {
      obj.setZid((String) json.getValue("zid"));
    }
  }

  public static void toJson(Job obj, JsonObject json) {
    if (obj.getAddress_id() != null) {
      json.put("address_id", obj.getAddress_id());
    }
    json.put("attempts", obj.getAttempts());
    if (obj.getBackoff() != null) {
      json.put("backoff", obj.getBackoff());
    }
    json.put("created_at", obj.getCreated_at());
    if (obj.getData() != null) {
      json.put("data", obj.getData());
    }
    json.put("delay", obj.getDelay());
    json.put("duration", obj.getDuration());
    json.put("failed_at", obj.getFailed_at());
    json.put("id", obj.getId());
    json.put("max_attempts", obj.getMax_attempts());
    if (obj.getPriority() != null) {
      json.put("priority", obj.getPriority().name());
    }
    json.put("progress", obj.getProgress());
    json.put("promote_at", obj.getPromote_at());
    json.put("removeOnComplete", obj.isRemoveOnComplete());
    if (obj.getResult() != null) {
      json.put("result", obj.getResult());
    }
    json.put("started_at", obj.getStarted_at());
    if (obj.getState() != null) {
      json.put("state", obj.getState().name());
    }
    json.put("ttl", obj.getTtl());
    if (obj.getType() != null) {
      json.put("type", obj.getType());
    }
    json.put("updated_at", obj.getUpdated_at());
    if (obj.getZid() != null) {
      json.put("zid", obj.getZid());
    }
  }
}