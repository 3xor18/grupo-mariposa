const appDatabase = process.env.MONGO_APP_DATABASE;

db.getSiblingDB(appDatabase).createUser({
  user: process.env.MONGO_APP_USERNAME,
  pwd: process.env.MONGO_APP_PASSWORD,
  roles: [{ role: 'readWrite', db: appDatabase }],
});
