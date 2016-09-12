# Vert.x Kue

[![Travis Build Status](https://travis-ci.org/sczyh30/vertx-blueprint-job-queue.svg?branch=master)](https://travis-ci.org/sczyh30/vertx-blueprint-job-queue)

Vert.x Blueprint Application - **Vert.x Kue** is a priority job queue developed with Vert.x and backed by **Redis**.
It's a Vert.x implementation version of [Automattic/kue](https://github.com/Automattic/kue).

This repo is an introduction to **message-based application development using Vert.x**.

## Detailed Document

Detailed documents and tutorials:

- [English Version](http://sczyh30.github.io/vertx-blueprint-job-queue/kue-core/index.html)
- [中文文档](http://sczyh30.github.io/vertx-blueprint-job-queue/cn/kue-core/index.html)

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

First build the code:

```
gradle build -x test
```

### Run in local

Vert.x Kue requires Redis running:

```
redis-server
```

Then we can run the example:

```
java -jar kue-core/build/libs/vertx-blueprint-kue-core.jar -cluster
java -jar kue-http/build/libs/vertx-blueprint-kue-http.jar -cluster
java -jar kue-example/build/libs/vertx-blueprint-kue-example.jar -cluster
```

Then you can visit `http://localhost:8080` to inspect the queue via Kue UI in the browser.

![](docs/images/vertx_kue_ui_1.png)

### Run with Docker Compose

To run Vert.x Kue with Docker Compose:

```
docker-compose up --build
```

Then you can run your applications in the terminal. For example:

```
java -jar kue-example/build/libs/vertx-blueprint-kue-example.jar -cluster
```

# Architecture

![Diagram - How Vert.x Kue works](https://raw.githubusercontent.com/sczyh30/vertx-blueprint-job-queue/master/docs/images/kue_diagram.png)

## Want to improve this blueprint ?

Forks and PRs are definitely welcome !
