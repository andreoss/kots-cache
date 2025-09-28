#!/usr/bin/env bash
# One-time init of the composed Couchbase node: cluster, credentials, bucket.
set -euo pipefail

compose_exec() { docker compose exec -T couchbase "$@"; }

for _ in $(seq 1 60); do
  compose_exec curl -sf http://127.0.0.1:8091/pools >/dev/null 2>&1 && break
  sleep 2
done

compose_exec couchbase-cli cluster-init -c 127.0.0.1 \
  --cluster-username Administrator --cluster-password password \
  --services data,index,query \
  --cluster-ramsize 512 --cluster-index-ramsize 256 || true

compose_exec couchbase-cli bucket-create -c 127.0.0.1 \
  -u Administrator -p password \
  --bucket cache --bucket-type couchbase --bucket-ramsize 256 \
  --enable-flush 1 --wait || true

echo "couchbase ready"
