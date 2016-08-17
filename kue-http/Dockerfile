FROM java:8-jre

ENV VERTICLE_FILE build/libs/vertx-blueprint-kue-http.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

EXPOSE 8080

COPY $VERTICLE_FILE $VERTICLE_HOME/
COPY src/config/docker.json $VERTICLE_HOME/

WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["java -jar vertx-blueprint-kue-http.jar -cluster -conf docker.json"]