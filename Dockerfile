# 빌드 스테이지
FROM gradle:8.8-jdk21 AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew clean bootJar -x test

# 런타임 스테이지
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /workspace/build/libs/*SNAPSHOT.jar app.jar
ENV TZ=Asia/Seoul
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
