# Multi-stage build: compile with Maven, run on a slim JRE.
FROM maven:3.9.9-eclipse-temurin-24 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:24-jre
WORKDIR /app
COPY --from=build /build/target/robinhoop-trader-*.jar /app/robinhoop-trader.jar

# /data is where the Render persistent Disk should be mounted — session tokens,
# the daily-loss baseline, and the signal dedup tracker all live here as plain
# files (see SessionStore, DailyEquityTracker, SignalTracker). Without a disk
# mounted here, every restart/redeploy wipes that state: the bot would need a
# fresh SMS-MFA login every time and lose its daily-loss baseline.
RUN mkdir -p /data
WORKDIR /data

ENTRYPOINT ["java", "-jar", "/app/robinhoop-trader.jar", "live"]
