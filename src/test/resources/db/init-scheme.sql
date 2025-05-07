-- example table; repeat for each table you need in unit tests
CREATE TABLE users (
                       user_id SERIAL PRIMARY KEY,
                       username VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       phonenumber VARCHAR(20)
);

-- add other tables as necessary for your unit-test scenarios
