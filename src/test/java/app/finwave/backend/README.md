# FinWave Integration Tests

This directory contains integration tests for the FinWave application. These tests verify that components work correctly together.

## Test Structure

### Integration Test Approach

The integration tests use a consistent pattern:

1. `BaseApiTest` - Base class with common setup and utility methods
2. SQL setup in `resources/db/test-setup.sql` - Prepares the database with test data
3. JSON fixtures in `resources/fixtures/` - Test data for requests
4. Individual test classes for each major feature:
   - `AccountIntegrationTest` - Tests account CRUD operations
   - `TransactionIntegrationTest` - Tests transaction handling
   - `BudgetIntegrationTest` - Tests budget management

### Testing Pattern

Each test follows this pattern:

1. **Setup**: Mock dependencies and configure API instances
2. **Test Data**: Load from fixtures or set up directly
3. **Execute**: Call API methods
4. **Verify**: Check responses and database operations

## Running Tests

Run tests with:

```bash
./gradlew test
```

## Test Fixtures

Test fixtures are JSON files in `src/test/resources/fixtures/`:

- `account-create.json` - Data for account creation
- `sample-account.json` - Sample account data
- `transaction-create.json` - Data for transaction creation

## Database Setup

The test database is initialized in `src/test/resources/db/test-setup.sql` with:

- Test user data
- Sample account folders
- Currencies
- Empty accounts and categories

## Test Support

- `BaseApiTest.java` - Provides common utilities for testing:
  - Request/response mocking
  - Fixture loading
  - Reflection utilities for accessing private fields 