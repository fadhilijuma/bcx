FROM openjdk:8-jre-alpine
COPY target/bcx-1.0-jar-with-dependencies.jar /wakala.jar
COPY deploy/   /deploy/
COPY genericpackager.dtd /genericpackager.dtd
COPY genericpackager.xml /genericpackager.xml
COPY uchumi.lmk /lmk/uchumi.lmk
EXPOSE 9900

CMD ["java", "-jar", "/wakala.jar"]