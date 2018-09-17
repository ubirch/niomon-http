FROM ubirch/java

COPY target/http-receiver-1.0.jar service.jar

EXPOSE 8080

CMD ["java","-Xmx256m","-Xms128m","-jar", "service.jar"]