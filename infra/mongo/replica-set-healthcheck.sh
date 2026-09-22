#!/usr/bin/env bash
set -euo pipefail

readonly REPLICA_SET=rs0
readonly MEMBER_HOST=mongo:27017

readonly HEALTH_SCRIPT="
db.getSiblingDB('admin').auth(
  process.env.MONGO_INITDB_ROOT_USERNAME,
  process.env.MONGO_INITDB_ROOT_PASSWORD
);
try {
  quit(rs.status().myState === 1 ? 0 : 1);
} catch (error) {
  rs.initiate({ _id: '${REPLICA_SET}', members: [{ _id: 0, host: '${MEMBER_HOST}' }] });
  quit(1);
}"

mongosh --quiet --eval "${HEALTH_SCRIPT}"
