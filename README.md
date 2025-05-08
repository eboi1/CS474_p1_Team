# CS474_p1_Team

Team Members:
  Eric Johnson, Thomas Pengelly, Maten Karim

Project Source: https://github.com/FinWave-App/FinWave-Backend

### Testing

Our tests are written in JUnit 5 and Mockito. They include the API,
Database, and Business Logic layers as well as Utilities

JUnit tests can be located under src/test/java/app.finwave.backend/

To run tests: (from project root)

```bash
./gradlew test
```

To run a singular test:

```bash
./gradlew test --tests "package.test"
```