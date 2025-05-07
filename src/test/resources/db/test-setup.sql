-- Clear test data
TRUNCATE TABLE users CASCADE;
TRUNCATE TABLE accounts CASCADE;
TRUNCATE TABLE categories CASCADE;
TRUNCATE TABLE transactions CASCADE;
TRUNCATE TABLE categories_budgets CASCADE;

-- Create test user
INSERT INTO users (user_id, username, password_hash) 
VALUES (1, 'test_user', 'password_hash');

-- Create test folders
INSERT INTO account_folders (id, owner_id, name)
VALUES (1, 1, 'Test Folder');

-- Create test currencies
INSERT INTO currencies (id, code, name)
VALUES 
  (1, 'USD', 'US Dollar'),
  (2, 'EUR', 'Euro');

-- Create test accounts
INSERT INTO accounts (id, owner_id, folder_id, currency_id, name, description, amount, hidden)
VALUES 
  (1, 1, 1, 1, 'Sample Account', 'This is a test account', 0, false);

-- Create test categories
INSERT INTO categories (id, owner_id, name)
VALUES 
  (1, 1, 'Test Category');

-- Return the test user ID
SELECT 1 AS test_user_id; 