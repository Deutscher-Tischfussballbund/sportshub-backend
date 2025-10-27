# Sportshub Backend Project

---
## Setup

### Prerequisite
To run the application you need to have a Java 17 or higher installed.
Either bundled with your IDE or download it via [Oracle](https://www.oracle.com/de/java/technologies/downloads/)

### Option 1: Run with IDE
Create a run configuration if not yet existing and execute.
You can use the configuration provided in ``.run`` folder.
It should automatically be added to intellij.

### Option 2: Run with gradle
Use Gradle to run the application

```./gradlew bootRun```

### Option 3: Build with gradle, run with java
Use gradle to build the application

```./gradlew clean build```

```shell
java -jar build/libs/sportshub-backend-0.0.1-SNAPSHOT.jar
```

In any case:
if not defined otherwise, the application.properties file is providing the environment variables

### Keycloak
To set up keycloak run
```shell
./docker/keycloak/docker compose up -d
```

### json-server
Use json-server to simulate an external api (e.g. for a player source)
There is a testfile located in ``./testdata``
```shell
npm install -g json-server
```

```shell
json-server --watch testdata\db.json --port 3000
```

## Components
The following is a list of components running when everything is booted up

| Component   | Default Port | Usage                   |
|-------------|--------------|-------------------------|
| Spring Boot | 8082         | Backend + Database      |
| Keycloak    | 8081 + 5432  | Auth + DB for Users     |
| Json Server | 3000         | External Api Simulation |

As a database there is a h2 (in-memory) database used for development.
Since you want eventually to view the data stored in the database, the h2 is being run in
file mode, creating the database in folder ``./data``
Connecting to the database can be done via the properties in ``./src/resources/application.properties``
named ``spring.datasource.*``

## Keycloak setup
TODO
