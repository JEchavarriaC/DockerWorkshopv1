FROM open-liberty:kernel-slim-java17-openj9

USER root
RUN apt-get update
RUN apt-get install nano

RUN featureUtility installFeature \
    jakartaee-10.0 \
    microprofile-7.0 \
    passwordUtilities-1.1 \
    ssl-1.0

COPY server.xml /opt/ol/wlp/usr/servers/defaultServer/
COPY openliberty-mongo-app.war /opt/ol/wlp/usr/servers/defaultServer/apps/
COPY certs/truststore.p12 /opt/ol/wlp/output/defaultServer/resources/security/truststore.p12

CMD ["server", "run", "defaultServer"]