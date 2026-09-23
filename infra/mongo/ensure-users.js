const READ_WRITE = 'readWrite';
const ADMIN_DATABASE = 'admin';

function required(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`${name} is required`);
  }
  return value;
}

function account(prefix) {
  return {
    database: required(`${prefix}_DATABASE`),
    user: required(`${prefix}_USERNAME`),
    password: required(`${prefix}_PASSWORD`),
  };
}

function ensure({ database, user, password }) {
  const target = db.getSiblingDB(database);
  const roles = [{ role: READ_WRITE, db: database }];
  if (target.getUser(user) === null) {
    target.createUser({ user, pwd: password, roles });
    print(`created ${user} with ${READ_WRITE} on ${database}`);
    return;
  }
  target.updateUser(user, { pwd: password, roles });
  print(`synchronized ${user} with ${READ_WRITE} on ${database}`);
}

const rootUser = required('MONGO_ROOT_USERNAME');
const rootPassword = required('MONGO_ROOT_PASSWORD');
db.getSiblingDB(ADMIN_DATABASE).auth(rootUser, rootPassword);
['MONGO_APP', 'MONGO_CLIENTS', 'MONGO_PRODUCTS'].map(account).forEach(ensure);
