// Logger mínimo usado por los servicios (el original importaba ../utils/logger, que no existía).
module.exports = {
  info: (message) => console.log(message),
  error: (message) => console.error(message),
};
