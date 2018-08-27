var express = require('express');
var router = express.Router();
var mysql = require('mysql');

// add module
var fs = require('fs');
var path = require('path');
var crypto = require('crypto');
var http = require('http');
var xml2js = require('xml2js');
var logger = require('log4js').getLogger("index");

var OTA_HOST = 'localhost';
var OTA_DATABASE = 'ota';
var OTA_USERNAME = 'root';
var OTA_PWD = 'youqin';
var OTA_TABLE = 'updateInfo';

var dlUrlBase = 'http://121.43.183.196:8081/updsvr/ota/ota_file?file=';

/* GET OTA UPDATE */
router.get('/checkupdate', function(req, res, next) {
    var hardWare = req.query.hw;
    var hardWareVersion = req.query.hwv;
    var softWareVersion = req.query.swv;
    var imei = req.query.imei;
    var serial = req.query.serialno;
    var sessionId = req.sessionID;
    var model = req.query.model;
    var sql = 'SELECT * FROM ' + OTA_TABLE + ' WHERE (hardWare = "' + hardWare + '" AND model = "' + model + '" AND srcVersion = "' + softWareVersion + '")';
    
    var info = {
        'OTA_NAME':'blephone_ota',
        'CHECK_UPDATE_RESULT':'-1',
        'hardware':'',
        'model':'',
        'fileName':'',
        'filePath':'',
        'downloadUrl':'',
        'size':'',
        'description':'',
        'srcVersion':'',                    //old version
        'dstVersion':'',                    //new version
        'priority':'',                      //Mandatory, Optional
        'md5':'',
        'sessionId':''
    };
    
    logger.info('get msg for clent - hardWare:' + hardWare + ', hardWareVersion:' + hardWareVersion + ', model:' + model + ', softWareVersion:' + softWareVersion + ', IMEI:' + imei + ', serialno:' + serial + ', sessionId:' + sessionId);
    
    req.session.user = serial;
    logger.info('req.session.user : ' + req.session.user);
    info.sessionId = sessionId;
    info.model = model;
    info.hardware = hardWare;
    
    var connection = mysql.createConnection({
        host     : OTA_HOST,
        user     : OTA_USERNAME,
        password : OTA_PWD,
        database : OTA_DATABASE
    });
    
    connection.connect();
    
    connection.query(sql, function (err, result) {
        if(err){
            logger.error('get update info failed! err.message:' + err.message);
            logger.info('connection.query error');
            info.CHECK_UPDATE_RESULT = '-1';
            info.description = 'check ota info from database failed!';
            returnXmlHead(res, info);
        }else{
            var retLength = result.length;
            if(retLength <= 0){
                logger.info('connection.query result is null');
                info.CHECK_UPDATE_RESULT = '-1';
                info.description = 'check ota info from database failed!';
                returnXmlHead(res, info);
            }else{
                info.srcVersion = result[0].srcVersion;
                info.dstVersion = result[0].dstVersion;
                
                if(softWareVersion == info.srcVersion){
                    var mfilePath = process.cwd() + result[0].filePath;
                    logger.info('load mysql filePath:' + mfilePath);
                    fs.exists(mfilePath, function(exist) {
                        if (exist) {
                            logger.info('exist ota pkg:' + mfilePath);
                            info.CHECK_UPDATE_RESULT = '0';
                            info.fileName = result[0].fileName;
                            info.filePath = result[0].filePath;
                            info.description = result[0].description;
                            info.priority = result[0].priority;
                            //var md5 = getFileMD5(mfilePath);
                            var otafile = fs.statSync(mfilePath);
                            info.size = otafile.size + "byte";
                            //info.size = result[0].size;
                            logger.info('file size : ' + info.size);
                            info.md5 = result[0].md5;
                            logger.info('md5 : ' + info.md5);
                            //info.downloadUrl = dlUrlBase + info.filePath;
                            info.downloadUrl = result[0].downloadUrl;
                            logger.info('downloadUrl : ' + info.downloadUrl);
                        } else {
                            logger.info('OTA pkg File not exist!');
                            info.CHECK_UPDATE_RESULT = '-1';
                            info.description = 'ota pkg file is not exist!';
                        }
                        returnXmlHead(res, info);
                    });
                }else if(softWareVersion == info.dstVersion){
                    logger.info('software is already updated! ver:' + softWareVersion);
                    info.CHECK_UPDATE_RESULT = '1';
                    info.description = 'software is already updated!';
                    returnXmlHead(res, info);
                }else{
                    logger.info('wrong target software version! ver:' + softWareVersion);
                    info.CHECK_UPDATE_RESULT = '-1';
                    info.description = 'wrong target software version!';
                    returnXmlHead(res, info);
                }
            }
        }
    });
    
    connection.end();
});

/* switch json to xml */
function returnXmlHead(response, infos) {
    var bd = new xml2js.Builder();
    var xml = bd.buildObject(infos);
    logger.info('----function returnXmlHead----');
    response.writeHead(200, {'Content-Type': 'application/json;charset=utf-8'});
    response.end(JSON.stringify(infos));
}

/* GET OTA FILE */
router.get('/ota_file', function(req, res, next) {
    var ssid = req.query.ssid;
    var filepath = req.query.file;
    logger.info('download ssid : ' + ssid);
    logger.info('login ssid : ' + req.sessionID);
    logger.info('filepath : ' + filepath);
    var dlpath = process.cwd() + filepath;
    
    fs.exists(dlpath, function(exist) {
        if (exist) {
            logger.info('downloading:' + filepath);
            res.download(dlpath); 
        } else {
            logger.error('File not exist!');
            res.set('Content-type', 'text/html');
            res.end('File not exist.');
        }
    });
});

/* GET SESSION */
router.get('/session', function(req, res, next) {
    res.send('session : ' + req.session.user);
    res.end();
});

function getFileMD5(filePath) {
    var buffer = fs.readFileSync(filePath);
    var fsHash = crypto.createHash('md5');
    
    fsHash.update(buffer);
    var md5 = fsHash.digest('hex');
    logger.info(filePath + ' MD5 ：' + md5);
    
    return md5;
}

module.exports = router;
