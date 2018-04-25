var createError = require('http-errors');
var express = require('express');
var path = require('path');
var cookieParser = require('cookie-parser');
//var morgan = require('morgan');
var session = require('express-session');
var log4js = require('log4js');
log4js.configure('config/log4js.json');

var otaRouter = require('./routes/ota');
var otaTestRouter = require('./routes/ota_test');
var usersRouter = require('./routes/users');

var app = express();

// view engine setup
app.set('views', path.join(__dirname, 'views'));
app.set('view engine', 'pug');

//app.use(morgan('dev'));
app.use(log4js.connectLogger(log4js.getLogger("http"), { level: 'trace' }));
app.use(express.json());
app.use(express.urlencoded({ extended: false }));
app.use(express.static(path.join(__dirname, 'public')));
app.use(cookieParser('blf_ota'));
app.use(session({
    secret: 'blf_ota',
    resave: false,
    saveUninitialized: true
}));

app.use('/updsvr/ota', otaRouter);
app.use('/updsvr/ota/test', otaTestRouter);
app.use('/users', usersRouter);

// catch 404 and forward to error handler
app.use(function(req, res, next) {
  next(createError(404));
});

// error handler
app.use(function(err, req, res, next) {
  // set locals, only providing error in development
  res.locals.message = err.message;
  res.locals.error = req.app.get('env') === 'development' ? err : {};

  // render the error page
  res.status(err.status || 500);
  res.render('error');
});

module.exports = app;
