var express = require('express');
var router = express.Router();

// add module
var fs = require('fs');
var path = require('path');
var crypto = require('crypto');
var http = require('http');
var xml2js = require('xml2js');
var logger = require('log4js').getLogger("index");

/* GET OTA UPDATE */
router.get('/checkupdate', function(req, res, next) {
    var hardWare = req.query.hw;
    var hardWareVersion = req.query.hwv;
    var softWareVersion = req.query.swv;
    var imei = req.query.imei;
    var serial = req.query.serialno;
    var sessionId = req.sessionID;
    
    logger.info('get msg for clent - hardWare:' + hardWare + ', hardWareVersion:' + hardWareVersion + ', softWareVersion:' + softWareVersion + ', IMEI:' + imei + ', serialno:' + serial + ', sessionId:' + sessionId);
    
    req.session.user = serial;
    logger.info('req.session.user : ' + req.session.user);
    
    var info = {
        'OTA_NAME':'blephone_ota',
        'CHECK_UPDATE_RESULT':'-1',
        'fileName':'',
        'filePath':'',
        'downloadUrl':'',
        'description':'',
        'srcVersion':'',                    //old version
        'dstVersion':'',                    //new version
        'priority':'',                      //Mandatory, Optional
        'md5':'',
        'sessionId':''
    };
    
    info.srcVersion = '112';
    info.dstVersion = '114';
    info.sessionId = sessionId;
    var ota_info_dir = process.cwd() + '/config';
    var ota_info_path = path.join(ota_info_dir, 'ota_info.json');
    var ota_info;
    if(fs.existsSync(ota_info_path)){
        ota_info = JSON.parse(fs.readFileSync(ota_info_path));
    }else{
        logger.error('ota_info not exist, ' + ota_info_path);
    }
    
    if(softWareVersion == info.srcVersion){
        info.CHECK_UPDATE_RESULT = '0';
        info.fileName = ota_info.fileName;
        info.filePath = ota_info.filePath;
        info.description = ota_info.description;
        info.priority = ota_info.priority;
        
        var dir = process.cwd() + '/ota_test_file';
        var filePath = path.join(dir, info.fileName);
        var md5 = getFileMD5(filePath);
        logger.info('filePath : ' + filePath);
        
        info.md5 = md5;
        logger.info('md5 : ' + md5);
        info.downloadUrl = 'http://121.43.183.196:8081/updsvr/ota/test/ota_test_file?' + 'ssid=' + info.sessionId + '&file=' + info.fileName;
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
    //logger.info(xml);
    
    res.writeHead(200, {'Content-Type': 'application/json;charset=utf-8'});
    res.end(JSON.stringify(info));
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
    
    if(ssid == req.sessionID){
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
    }else{
        res.send('ssid is wrong! ' + ssid + ':' + req.sessionID);
        res.end();
    }
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
