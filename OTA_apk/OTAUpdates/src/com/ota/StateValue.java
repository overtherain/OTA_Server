package com.ota;

public class StateValue 
{
    public static final String CHECKBROADACTION = "android.intent.action.CHECKBROADACTION";

    public static final String SHARED_PREFERENCES_NAME = "ota_updates_info";
	
    public static final String RESULT = "CHECK_UPDATE_RESULT:";
    public static final String SRC_VER = "srcVersion";
    public static final String DST_VER = "dstVersion";
    public static final String DSTCRIPTION = "description";
    public static final String DOWNLOADURL = "downloadURL";
    public static final String SIZE = "size";
    public static final String PRIORITY = "priority";
    public static final String SESSIONID = "sessionId";
    public static final String FILEPATH = "filePath";
    public static final String FILENAME = "fileName";
    public static final String WAITTIMES = "waitTimes";
    public static final String STARTSTATE = "startState";
    public static final String ISAUTOCHECK = "isAutoCheck";
	public static final String IS_CMWAP = "iscmwap";
	public static final String DB_URL = "db_url";
    public static final String DATA="data";
    public static final String MD5="md5";
    
    public final static int CHECK_NO_NET = 0; 		//������
    public final static int CHECK_NEED_UP = 1; 		//��Ҫ����
    public final static int CHECK_NO_NEED_UP = 2; 	//�Ѿ�����
    public final static int CHECK_ERR_DATA = 3; 	//���ݴ���
    public final static int CHECK_SP_ERR = 4; 		//������������
    public final static int CHECK_Mandatory=5;

    public final static int DOWNLOAD_START = 1; 	//������ʾ
    public final static int DOWNLOAD_OK = 2; 		//�������
    public final static int DOWNLOAD_FAIL = 3; 		//����ʧ��
    public final static int DOWNLOAD_DEL = 4; 		//��ɾ��
    public final static int DOWNLOAD_OTHER=5;
}


class UpdatesInfo 
{
	public int result = -1; // 0 ��Ҫ������1����Ҫ������ 2��ְ�������
	public String src_version = "";
	public String dst_version = "";
	public String description = "";
	public String downloadUrl = "";
	public int fileSize = 0;
	public String priority = "";
	public String sessionId = "";
	public String filePath = "";
	public String fileName = "";
	public int waitTimes = 0;
	public String db_url = "";
	public String md5 = "";
}