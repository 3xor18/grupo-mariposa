#!/usr/bin/env bash
set -euo pipefail

readonly INIT_SCRIPT='
try {
  quit(rs.status().myState === 1 ? 0 : 1);
} catch (error) {
  rs.initiate({ _id: "rs0", members: [{ _id: 0, host: "mongo:27017" }] });
  quit(1);
}'

mongosh --quiet \
  --username "${MONGO_INITDB_ROOT_USERNAME}" \
  --password "${MONGO_INITDB_ROOT_PASSWORD}" \
  --authenticationDatabase admin \
  --eval "${INIT_SCRIPT}"
