# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Maven wrapper와 pom.xml 복사
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# 의존성 다운로드 (캐싱 최적화)
RUN ./mvnw dependency:go-offline

# 소스 코드 복사
COPY src ./src

# 애플리케이션 빌드
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 빌드 결과물 복사
COPY --from=builder /app/target/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
