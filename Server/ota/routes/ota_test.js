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

/* GET OTA UPDATE */
router.get('/checkupdate', function(req, res, next) {
    var hardWare = req.query.hw;
    var hardWareVersion = req.query.hwv;
    var softWareVersion = req.query.swv;
    var imei = req.query.imei;
    var serial = req.query.serialno;
    var sessionId = req.sessionID;
    var model = req.query.model;
    var sql = 'SELECT * FROM updateInfo WHERE (hardWare = "' + hardWare + '" AND model = "' + model + '")';
    
    var info = {
        'OTA_NAME':'blephone_ota',
        'CHECK_UPDATE_RESULT':'-1',
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
    
    var connection = mysql.createConnection({
        host     : OTA_HOST,
        user     : OTA_USERNAME,
        password : OTA_PWD,
        database : OTA_DATABASE
    });
    
    connection.connect();
    
    connection.query(sql,function (err, result) {
        if(err){
            logger.error('get update info failed! err.message:' + err.message);
            info.CHECK_UPDATE_RESULT = '-1';
            info.description = 'get ota info failed!';
            var bd = new xml2js.Builder();
            var xml = bd.buildObject(info);
            
            res.writeHead(200, {'Content-Type': 'application/json;charset=utf-8'});
            res.end(JSON.stringify(info));
            return;
        }else{
            info.srcVersion = result[0].srcVersion;
            info.dstVersion = result[0].dstVersion;
            info.model = result[0].model;
            
            if(softWareVersion == info.srcVersion){
                info.CHECK_UPDATE_RESULT = '0';
                info.fileName = result[0].fileName;
                info.filePath = result[0].filePath;
                info.description = result[0].description;
                info.priority = result[0].priority;
                
                //var dir = process.cwd() + '/ota_file';
                var filePath = process.cwd() + result[0].filePath;
                var md5 = getFileMD5(filePath);
                logger.info('filePath : ' + filePath);
                var otafile = fs.statSync(filePath);
                info.size = otafile.size + "byte";
                logger.info('file size : ' + info.size);
                info.md5 = md5;
                logger.info('md5 : ' + md5);
                info.downloadUrl = 'http://121.43.183.196:8081/updsvr/ota/test/ota_test_file?' + 'file=' + info.fileName;
                logger.info('downloadUrl : ' + info.downloadUrl);
            }else if(softWareVersion == info.dstVersion){
                logger.error('software is already updated! ver:' + softWareVersion);
                info.CHECK_UPDATE_RESULT = '1';
                info.description = 'software is already updated!';
            }else{
                logger.error('wrong target software version! ver:' + softWareVersion);
                info.CHECK_UPDATE_RESULT = '-1';
                info.description = 'wrong target software version!';
            }
            
            var bd = new xml2js.Builder();
            var xml = bd.buildObject(info);
            
            res.writeHead(200, {'Content-Type': 'application/json;charset=utf-8'});
            res.end(JSON.stringify(info));
        }
    });
    
    connection.end();
});

/* GET OTA FILE */
router.get('/ota_test_file', function(req, res, next) {
    var ssid = req.query.ssid;
    var filename = req.query.file;
    logger.info('download ssid : ' + ssid);
    logger.info('login ssid : ' + req.sessionID);
    logger.info('filename : ' + filename);
    var dir = process.cwd() + '/ota_test_file'
    var filePath = path.join(dir, filename);
    
    fs.exists(filePath, function(exist) {
        if (exist) {
            logger.info('downloading:' + filename);
            res.download(filePath); 
        }
        else {
            logger.error('File not exist!');
            res.set('Content-type', 'text/html');
            res.end('File not exist.');
        }
    });
    /*if(ssid == req.sessionID){
        
    }else{
        res.send('ssid is wrong! ' + ssid + ':' + req.sessionID);
        res.end();
    }*/
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
