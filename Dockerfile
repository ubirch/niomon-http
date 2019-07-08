FROM ubirch/java
ARG THIRD_PARTY_LIB
ARG PROJECT_LIB
ARG JAR_FILE
ARG VERSION
ARG BUILD
ARG SERVICE_NAME

LABEL "com.ubirch.service"="${SERVICE_NAME}"
LABEL "com.ubirch.version"="${VERSION}"

EXPOSE 8080
EXPOSE 9010
EXPOSE 9020
EXPOSE 4321

ENV _JAVA_OPTIONS "-Xms128m -Xmx256m -Djava.awt.headless=true"

ENTRYPOINT [ \
  "/usr/bin/java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Djava.rmi.server.hostname=localhost", \
  "-Dcom.sun.management.jmxremote", \
  "-Dcom.sun.management.jmxremote.port=9010", \
  "-Dcom.sun.management.jmxremote.rmi.port=9010", \
  "-Dcom.sun.management.jmxremote.local.only=false", \
  "-Dcom.sun.management.jmxremote.authenticate=false", \
  "-Dcom.sun.management.jmxremote.ssl=false", \
  "-Dconfig.resource=application-docker.conf", \
  "-Dlogback.configurationFile=logback-docker.xml", \
  "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=9020", \
  "-jar", "/usr/share/service/main.jar" \
]

# Add Maven dependencies (not shaded into the artifact; Docker-cached)
# To improve cache usage this is split into two steps - third-party libs and libs from the project
COPY ${THIRD_PARTY_LIB} /usr/share/service/lib/
# NOTE: uncomment the line below when http-receiver gets some project libs
# COPY ${PROJECT_LIB} /usr/share/service/lib/

# Add the service itself
COPY ${JAR_FILE} /usr/share/service/main.jar
LABEL "com.ubirch.build"="${BUILD}"