-- Cluster bootstrap for the PostgreSQL primary of the microservices lab.
--
-- Executed once by the official postgres:16 entrypoint with:
--   psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"
--
-- This file contains NO secrets. Passwords are read from the container environment with psql's
-- \getenv, so the same script works locally and in Compose without a committed credential.
-- Required environment variables:
--   PEDIDOS_APP_PASSWORD, INVENTARIO_APP_PASSWORD, REPLICATOR_PASSWORD

\set ON_ERROR_STOP on

\getenv pedidos_password PEDIDOS_APP_PASSWORD
\getenv inventario_password INVENTARIO_APP_PASSWORD
\getenv replicator_password REPLICATOR_PASSWORD

-- Fail fast: a missing password makes the next SELECT divide by zero, which aborts the entrypoint
-- instead of creating roles with an empty password.
SELECT 1 / (CASE WHEN :'pedidos_password' = '' THEN 0 ELSE 1 END) AS pedidos_password_is_required;
SELECT 1 / (CASE WHEN :'inventario_password' = '' THEN 0 ELSE 1 END) AS inventario_password_is_required;
SELECT 1 / (CASE WHEN :'replicator_password' = '' THEN 0 ELSE 1 END) AS replicator_password_is_required;

-- One application role per service. Each role owns exactly one database and nothing else.
CREATE ROLE pedidos_app LOGIN PASSWORD :'pedidos_password';
CREATE ROLE inventario_app LOGIN PASSWORD :'inventario_password';

-- Streaming replication role. It has the REPLICATION attribute and no CONNECT privilege on business
-- databases, so it can replicate the cluster but cannot query either service data.
CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD :'replicator_password';

CREATE DATABASE pedidos_db OWNER pedidos_app;
CREATE DATABASE inventario_db OWNER inventario_app;

-- Closing the default CONNECT privilege for PUBLIC is what keeps each service out of the other's
-- database: without an explicit GRANT, only the owner can connect.
REVOKE ALL ON DATABASE pedidos_db FROM PUBLIC;
REVOKE ALL ON DATABASE inventario_db FROM PUBLIC;

GRANT CONNECT, TEMPORARY ON DATABASE pedidos_db TO pedidos_app;
GRANT CONNECT, TEMPORARY ON DATABASE inventario_db TO inventario_app;
