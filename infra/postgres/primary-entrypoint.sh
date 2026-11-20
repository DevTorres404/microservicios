#!/usr/bin/env bash
set -Eeuo pipefail

# If PGDATA already has pg_hba.conf from an existing volume, ensure replication rule is present
if [ -f "$PGDATA/pg_hba.conf" ]; then
    if ! grep -q "host replication replicator" "$PGDATA/pg_hba.conf"; then
        echo "host replication replicator 0.0.0.0/0 scram-sha-256" >> "$PGDATA/pg_hba.conf"
    fi
fi

exec /usr/local/bin/docker-entrypoint.sh "$@"
