FROM navikt/java:11-appdynamics

ENV APPD_ENABLED=true

COPY build/libs/*.jar app.jar