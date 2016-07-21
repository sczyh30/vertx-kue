# Vert.x Kue

Vert.x Blueprint Application - **Vert.x Kue** is a priority job queue developed with Vert.x and backed by *Redis*.
It's a Vert.x implementation version of [Automattic/kue](https://github.com/Automattic/kue).

This repo is an introduction to message-based application development using Vert.x.

## Detailed Document

Detailed documents and tutorials:

- [English Version](docs/en)
- [中文文档](docs/zh-cn)

## Features

- Job priority
- Delayed jobs
- Process many jobs simultaneously
- Job and queue event
- Optional retries with backoff
- RESTful JSON API
- Rich integrated UI (with the help of Automattic/kue's UI)
- UI progress indication
- Job specific logging
- Future-based asynchronous model
- Polyglot language support
- Powered by Vert.x!

For the detail of the features, please see [Vert.x Kue Features](docs/en/vertx-kue-features-en.md).

## Build/Run

To build the code:

    gradle build

Vert.x Kue requires Redis running:

    redis-server

Then we can run the example:

    java -jar kue-core/build/libs/vertx-blueprint-kue-core.jar -cluster -ha -conf config/config.json
    java -jar kue-http/build/libs/vertx-blueprint-kue-http.jar -cluster -ha -conf config/config.json
    java -jar kue-example/build/libs/vertx-blueprint-kue-example.jar -cluster -ha -conf config/config.json

Then you can visit `http://localhost:8080` to inspect the queue via Kue UI in the browser.

![](docs/images/vertx_kue_ui_1.png)
