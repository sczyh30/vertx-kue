# Tutorial: Vert.x Blueprint - Vert.x Kue (Core)

## Table of contents

- [Preface](#preface)
- [Message system in Vert.x](#message-system-in-vert-x)
- [Basic design of Kue](#basic-design-of-kue)
- [Let's start!](#let-s-start)
- [Finish!](#finish)
- [From Akka?](#)

## Preface

Hi, welcome back to the Vert.x Blueprint tutorial series~ In this tutorial, we are going to use Vert.x
to develop a message based application - Vert.x Kue.
Vert.x Kue is a priority job queue backed by *Redis*. It's a Vert.x implementation version of [Automattic/kue](https://github.com/Automattic/kue). We can use Vert.x Kue to process various kinds of jobs, e.g. **converting files** or **processing orders**.

What you are going to learn:

- How to make use of **Vert.x Event Bus** (clustered)
- How to develop message based application with Vert.x
- Event pattern of event bus(Pub/sub, point to point)
- How to design clustered Vert.x applications
- How to design a job queue
- How to use **Vert.x Service Proxy**
- More complicated practice about Vert.x Redis

This is the second part of **Vert.x Blueprint Project**. The entire code developed in this tutorial is available on [GitHub](https://github.com/sczyh30/vertx-blueprint-job-queue/tree/master).

## Message system in Vert.x

We are going to develop a message based application, so let's have a glimpse of the message system in Vert.x. In Vert.x, we could send and receive messages on the **event bus** between the different verticles with different `Vertx` instance. So cool yeah? Messages are sent on the event bus to an **address**, which can be arbitrary string like `foo.bar.nihao`. The event bus supports **publish/subscribe**, **point to point**, and **request-response** messaging. Let's take a look.

### Publish/subscribe messaging

First is **pub/sub** pattern. Messages are published to an address, and all handlers that registered to the address (aka. subscribe) will be notified with the message. Let's see how to do this:

```java
EventBus eventBus = vertx.eventBus();

eventBus.consumer("foo.bar.baz", r -> { // subscribe to `foo.bar.baz` address
  System.out.println("1: " + r.body());
});
eventBus.consumer("foo.bar.baz", r -> { // subscribe to `foo.bar.baz` address
  System.out.println("2: " + r.body());
});

eventBus.publish("foo.bar.baz", "Ni Hao!"); // now publish the message to the address
```

We could get a reference to the event bus with `vertx.eventBus()` method. And then we could subscribe(consume) messages on certain address with a handler. Then we could publish a message to an address with `publish` method. In the example above, the program will print:

```
2: Ni Hao!
1: Ni Hao!
```

### Point to point messaging

If we replace `publish` method with `send` method, that is **point to point(send/recv)** pattern. Messages are sent to an address, Vert.x will then route it to just one of the handlers registered at the address using a non-strict round-robin algorithm. In the point to point example, the program will print only `1: Ni Hao!` or `2: Ni Hao!`.

### Request-response messaging

When a message is received by a handler, can it reply to the sender? Of course!  When we `send` a message, we can specify a reply handler.

Then when a message is received by a consumer, it can reply message to the sender, and then the reply handler on the sender will be called. That is request-response messaging.


Now we are simply aware of event bus in Vert.x, so let' design our Vert.x Kue! For more details about event bus, we can refer to [Vert.x Core Manual - Event Bus](http://vertx.io/docs/vertx-core/java/#event_bus).

## Basic design of Kue

Recall the demand of Vert.x Kue - a priority job queue backed by *Redis*.

### Events in Vert.x Kue

### Workflow

## Let's start!

## Finish!

## From Akka?
