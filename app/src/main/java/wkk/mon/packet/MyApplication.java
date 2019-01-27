package wkk.mon.packet;

import android.app.Application;

import com.tencent.bugly.crashreport.CrashReport;

/**
 * Created by wangkeke on 2019/1/27.
 */

public class MyApplication extends Application {

    private static MyApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        CrashReport.initCrashReport(getApplicationContext(), "37c50d7966", false);
    }

    public static MyApplication getInstance() {
        return instance;
    }
}
