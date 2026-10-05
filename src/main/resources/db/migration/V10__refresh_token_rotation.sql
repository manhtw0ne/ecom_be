-- JWT jti makes each access token unique, even when issued in the same second.
ALTER TABLE tokens MODIFY COLUMN token VARCHAR(2048);
-- V3 used an empty-string default for sessions without a refresh credential.
-- Those rows cannot refresh; NULL permits multiple legacy rows under a unique key.
UPDATE tokens SET refresh_token = NULL WHERE refresh_token = '';
ALTER TABLE tokens MODIFY COLUMN refresh_token VARCHAR(255) DEFAULT NULL;
CREATE UNIQUE INDEX uk_tokens_refresh_token ON tokens(refresh_token);