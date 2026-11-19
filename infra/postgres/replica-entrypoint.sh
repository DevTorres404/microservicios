#!/usr/bin/env bash
set -Eeuo pipefail

PRIMARY_HOST="${PRIMARY_HOST:-pg-primary}"
PRIMARY_PORT="${PRIMARY_PORT:-5432}"
REPLICATOR_USER="${REPLICATOR_USER:-replicator}"
REPLICATOR_PASSWORD="${REPLICATOR_PASSWORD:-change-me-replicator}"

# Ensure passfile is available in postgres user home directory
mkdir -p /var/lib/postgresql
cat << EOF > /var/lib/postgresql/.pgpass
${PRIMARY_HOST}:${PRIMARY_PORT}:replication:${REPLICATOR_USER}:${REPLICATOR_PASSWORD}
*:*:*:*:${REPLICATOR_PASSWORD}
EOF
chmod 0600 /var/lib/postgresql/.pgpass
chown -R postgres:postgres /var/lib/postgresql/.pgpass
export PGPASSFILE=/var/lib/postgresql/.pgpass

# If PGDATA is uninitialized (empty or missing PG_VERSION), clone from primary
if [ ! -s "$PGDATA/PG_VERSION" ]; then
    echo "PGDATA is uninitialized. Waiting for primary at ${PRIMARY_HOST}:${PRIMARY_PORT}..."
    until pg_isready -h "${PRIMARY_HOST}" -p "${PRIMARY_PORT}" -U "${REPLICATOR_USER}" -d postgres; do
        sleep 1
    done
    echo "Primary is reachable. Cloning via pg_basebackup..."

    # Empty PGDATA directory so pg_basebackup can populate it
    rm -rf "${PGDATA:?}"/* "${PGDATA:?}"/.* 2>/dev/null || true

    gosu postgres pg_basebackup \
        -h "${PRIMARY_HOST}" \
        -p "${PRIMARY_PORT}" \
        -U "${REPLICATOR_USER}" \
        -D "$PGDATA" \
        -Fp -Xs -R -P \
        --no-slot

    # Ensure no credentials are saved in plain text in postgresql.auto.conf (ADR-05)
    if [ -f "$PGDATA/postgresql.auto.conf" ]; then
        sed -i -E "s/password='?[^' ]+'? ?//g" "$PGDATA/postgresql.auto.conf"
        if ! grep -q "passfile=" "$PGDATA/postgresql.auto.conf"; then
            sed -i "s/primary_conninfo = '/primary_conninfo = 'passfile='\/var\/lib\/postgresql\/.pgpass' /g" "$PGDATA/postgresql.auto.conf"
        fi
    fi

    # Explicitly ensure standby.signal exists
    touch "$PGDATA/standby.signal"
    chown postgres:postgres "$PGDATA/standby.signal"
    chown -R postgres:postgres "$PGDATA"
    chmod 0700 "$PGDATA"
    echo "Base backup completed successfully. Standby mode configured."
else
    echo "PGDATA already populated. Starting replica in standby mode..."
    touch "$PGDATA/standby.signal"
    chown postgres:postgres "$PGDATA/standby.signal"
fi

exec /usr/local/bin/docker-entrypoint.sh "$@"
