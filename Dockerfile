FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY build/libs/*.jar app.jar

ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-Duser.timezone=Asia/Seoul","-jar","/app/app.jar"]