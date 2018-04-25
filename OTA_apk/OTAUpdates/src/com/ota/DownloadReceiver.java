package com.ota;

import java.io.File;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.provider.Downloads;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;

public class DownloadReceiver extends BroadcastReceiver 
{	
	@Override
	public void onReceive(Context context, Intent intent) 
	{
		android.content.SharedPreferences sharedPref;
		android.content.SharedPreferences.Editor editor;
		sharedPref = context.getSharedPreferences(StateValue.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		editor = sharedPref.edit();
		editor.putBoolean("cgisdownloading", false);
		editor.commit();

		ContentResolver cr = context.getContentResolver();
		Uri data = intent.getData();
		Cursor cursor = null;
		try {
			cursor = cr.query(data,
					new String[] { Downloads.Impl._ID, Downloads.Impl._DATA, Downloads.Impl.COLUMN_MIME_TYPE, Downloads.Impl.COLUMN_STATUS}, 
					null, null, null);
			
			if (cursor.moveToFirst()) 
			{
				String mdrm = "content://drm/";
				String id = cursor.getString(0);
				String filename = cursor.getString(1);
				String mimetype = cursor.getString(2);
				int status = cursor.getInt(3);
				String action = intent.getAction();

				Intent startIntent = new Intent(context, OTAUpdatesActivity.class);
				startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				if (Downloads.ACTION_DOWNLOAD_COMPLETED.equals(action))
				{				    
					if(status == 200)
					{
						startIntent.putExtra(StateValue.STARTSTATE, StateValue.DOWNLOAD_OK);
						startIntent.putExtra(StateValue.FILEPATH, filename);
						startIntent.putExtra(StateValue.DATA, data.toString());
					}
					else
					{
						Log.d("dl  recver ", "filename = " + filename);
						if(filename!=null)
						{
							if (deleteFile(cr, filename, mimetype)) 
							{
							}
						}
							cr.delete(data, null, null);
						startIntent.putExtra(StateValue.STARTSTATE, StateValue.DOWNLOAD_FAIL);
					}
					context.startActivity(startIntent);
				}
				else if (Intent.ACTION_DELETE.equals(action))
				{
					if (deleteFile(cr, filename, mimetype)) {
						cr.delete(data, null, null);
					}
					startIntent.putExtra(StateValue.STARTSTATE, StateValue.DOWNLOAD_DEL);
					context.startActivity(startIntent);
				}
				
			}
			else
			{
				Intent startIntent = new Intent(context, OTAUpdatesActivity.class);
				startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startIntent.putExtra(StateValue.STARTSTATE, StateValue.DOWNLOAD_OTHER);
				context.startActivity(startIntent);
			}
		} 
		finally 
		{
			if (cursor != null)
			{
				cursor.close();
			}
		}
	}
	
	private boolean deleteFile(ContentResolver cr, String filename,String mimetype) 
	{
		Uri uri;
		
		if (mimetype.startsWith("image")) 
		{
			uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		} 
		else if (mimetype.startsWith("audio")) 
		{
			uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		} 
		else if (mimetype.startsWith("video")) 
		{
			uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
		} 
		else 
		{
			uri = null;
		}
		
		return (uri != null && cr.delete(uri, MediaStore.MediaColumns.DATA + " = " 
				+ DatabaseUtils.sqlEscapeString(filename), null) > 0) || new File(filename).delete();
	}
}
