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
 * Converter for {@link io.vertx.blueprint.kue.queue.JobMetrics}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.blueprint.kue.queue.JobMetrics} original class using Vert.x codegen.
 */
public class JobMetricsConverter {

  public static void fromJson(JsonObject json, JobMetrics obj) {
    if (json.getValue("createdAt") instanceof Number) {
      obj.setCreatedAt(((Number) json.getValue("createdAt")).longValue());
    }
    if (json.getValue("duration") instanceof Number) {
      obj.setDuration(((Number) json.getValue("duration")).doubleValue());
    }
    if (json.getValue("failedAt") instanceof Number) {
      obj.setFailedAt(((Number) json.getValue("failedAt")).longValue());
    }
    if (json.getValue("promoteAt") instanceof Number) {
      obj.setPromoteAt(((Number) json.getValue("promoteAt")).longValue());
    }
    if (json.getValue("startedAt") instanceof Number) {
      obj.setStartedAt(((Number) json.getValue("startedAt")).longValue());
    }
    if (json.getValue("updatedAt") instanceof Number) {
      obj.setUpdatedAt(((Number) json.getValue("updatedAt")).longValue());
    }
  }

  public static void toJson(JobMetrics obj, JsonObject json) {
    json.put("createdAt", obj.getCreatedAt());
    json.put("duration", obj.getDuration());
    json.put("failedAt", obj.getFailedAt());
    json.put("promoteAt", obj.getPromoteAt());
    json.put("startedAt", obj.getStartedAt());
    json.put("updatedAt", obj.getUpdatedAt());
  }
}