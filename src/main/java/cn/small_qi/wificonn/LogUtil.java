package cn.small_qi.wificonn;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Created by small_qi on 2017/7/26.
 */

public class LogUtil {
    private static final boolean writeToFile=true;
    public static void log(Object info){
        Log.i("wifi", String.valueOf(info));
        if (writeToFile){
            writeToFile(info);
        }
    }

    private static void writeToFile(Object info) {
        File file =new File(Environment.getExternalStorageDirectory(),"wifi_log.txt");
        try {
            FileOutputStream os = new FileOutputStream(file,true);
            os.write(String.valueOf(info).getBytes());
            os.flush();
            os.close();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }

    }
}
