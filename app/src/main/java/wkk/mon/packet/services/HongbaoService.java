package wkk.mon.packet.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.graphics.Path;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import com.tencent.bugly.crashreport.CrashReport;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import wkk.mon.packet.MyApplication;
import wkk.mon.packet.utils.AccessibilityHelper;
import wkk.mon.packet.utils.HongbaoSignature;
import wkk.mon.packet.utils.NotifyHelper;
import wkk.mon.packet.utils.PowerUtil;


import java.util.List;
import java.util.concurrent.TimeUnit;

public class HongbaoService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "HongbaoService";
    private static final String WECHAT_DETAILS_EN = "Details";
    private static final String WECHAT_DETAILS_CH = "红包详情";
    private static final String WECHAT_BETTER_LUCK_EN = "Better luck next time!";
    private static final String WECHAT_BETTER_LUCK_CH = "手慢了";
    private static final String WECHAT_BETTER_CHAOGUO_24 = "已超过24小时";

    private static final String WECHAT_EXPIRES_CH = "已超过24小时";
    private static final String WECHAT_VIEW_SELF_CH = "查看红包";
    private static final String WECHAT_VIEW_OTHERS_CH = "微信红包";
    private static final String WECHAT_VIEW_OTHERS_CH_NEW_TIP = "恭喜发财，大吉大利";
    private static final String WECHAT_NOTIFICATION_TIP = "[微信红包]";
    private static final String WECHAT_LUCKMONEY_RECEIVE_ACTIVITY = ".plugin.luckymoney.ui";//com.tencent.mm/.plugin.luckymoney.ui.En_fba4b94f  com.tencent.mm/com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI
    private static final String WECHAT_LUCKMONEY_DETAIL_ACTIVITY = "LuckyMoneyDetailUI";
    private static final String WECHAT_LUCKMONEY_GENERAL_ACTIVITY = "LauncherUI";
    private static final String WECHAT_LUCKMONEY_CHATTING_ACTIVITY = "ChattingUI";
    private String currentActivityName = WECHAT_LUCKMONEY_GENERAL_ACTIVITY;

    /**
     * 微信的包名
     */
    public static final String PACKAGE_NAME = "com.tencent.mm";

    private AccessibilityNodeInfo rootNodeInfo, mReceiveNode, mUnpackNode;
    private boolean mLuckyMoneyPicked, mLuckyMoneyReceived;
    private int mUnpackCount = 0;
    private boolean mMutex = false, mListMutex = false, mChatMutex = false;
    private HongbaoSignature signature = new HongbaoSignature();

    private PowerUtil powerUtil;
    private SharedPreferences sharedPreferences;

    private static final int APP_STATE_BACKGROUND = -1;
    private static final int APP_STATE_FOREGROUND = 1;

    /**
     * AccessibilityEvent
     *
     * @param event 事件
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
//            Toast.makeText(this, "onAccessibilityEvent sharedPreferences="+sharedPreferences, Toast.LENGTH_SHORT).show();
            if (sharedPreferences == null) return;
            setCurrentActivityName(event);

        /* 检测通知消息 */
            if (doStartWatch(event)) return;

//            checkChatList();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean doStartWatch(AccessibilityEvent event) {
        if (!mMutex) {
            if (sharedPreferences.getBoolean("pref_watch_notification", false) && watchNotifications(event)) {
                return true;
            }
            if (sharedPreferences.getBoolean("pref_watch_list", false) && watchList(event)) {
                return true;
            }
            mListMutex = false;
        }
        if (!mChatMutex) {
            mChatMutex = true;
            if (sharedPreferences.getBoolean("pref_watch_chat", false)) watchChat(event);
            mChatMutex = false;
        }
        return false;
    }

    /**
     * 聊天列表有红包
     */
    /*private void checkChatList() {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if(null == nodeInfo){
            return;
        }
        List<AccessibilityNodeInfo> listViewNodes = nodeInfo.findAccessibilityNodeInfosByText("WECHAT_NOTIFICATION_TIP");
        if (null != listViewNodes && listViewNodes.size() > 0) {
            Toast.makeText(this, "发现聊天列表有红包", Toast.LENGTH_SHORT).show();
            for (int i = 0; i < listViewNodes.size(); i++) {

                for (int j = 0; j < listViewNodes.get(i).getChildCount(); j++) {
                    listViewNodes.get(i).getChild(j).
                }
            }
        }
    }*/
    private void watchChat(AccessibilityEvent event) {
        this.rootNodeInfo = getRootInActiveWindow();
        if (rootNodeInfo == null) return;

        mReceiveNode = null;
        mUnpackNode = null;

        /*if(NotifyHelper.isLockScreen(this)){

        }

        if (!isRunningForeground(getApplicationContext())) {
            //在后台

        } else {
            //在前台
            checkNodeInfo();
        }*/
        checkNodeInfo();
    }

    /**
     * 唤醒手机屏幕并解锁
     */
    public static void wakeUpAndUnlock() {
        // 获取电源管理器对象
        PowerManager pm = (PowerManager) MyApplication.getInstance()
                .getSystemService(Context.POWER_SERVICE);
        boolean screenOn = pm.isScreenOn();
        if (!screenOn) {
            // 获取PowerManager.WakeLock对象,后面的参数|表示同时传入两个值,最后的是LogCat里用的Tag
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.ACQUIRE_CAUSES_WAKEUP |
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "bright");
            wl.acquire(10000); // 点亮屏幕
            wl.release(); // 释放
        }
        // 屏幕解锁
        KeyguardManager keyguardManager = (KeyguardManager) MyApplication.getInstance()
                .getSystemService(KEYGUARD_SERVICE);
        KeyguardManager.KeyguardLock keyguardLock = keyguardManager.newKeyguardLock("unLock");
        // 屏幕锁定
        keyguardLock.reenableKeyguard();
        keyguardLock.disableKeyguard(); // 解锁
    }

    private int mAppState = APP_STATE_FOREGROUND;

    private void setCurrentActivityName(AccessibilityEvent event) {

        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            Log.e("wangkeke","锁屏状态 ： "+NotifyHelper.isLockScreen(MyApplication.getInstance()));
            if (NotifyHelper.isLockScreen(MyApplication.getInstance())) {

                NotifyHelper.openScreen(MyApplication.getInstance());
                NotifyHelper.openLockScreen(MyApplication.getInstance());
                doStartWatch(event);
            } else {
                doStartWatch(event);
            }
            return;
        }

        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        try {
            ComponentName componentName = new ComponentName(
                    event.getPackageName().toString(),
                    event.getClassName().toString()
            );

            getPackageManager().getActivityInfo(componentName, 0);
            currentActivityName = componentName.flattenToShortString();
        } catch (PackageManager.NameNotFoundException e) {
            currentActivityName = WECHAT_LUCKMONEY_GENERAL_ACTIVITY;
        }
    }

    private boolean watchList(AccessibilityEvent event) {

        if (mListMutex) return false;
        mListMutex = true;
        AccessibilityNodeInfo eventSource = getRootInActiveWindow();
        // Not a message
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || eventSource == null)
            return false;


        // 直接去获取聊天记录的第一条Item, 不为null, 则是聊天记录列表
        AccessibilityNodeInfo item = AccessibilityHelper.findNodeInfosById(eventSource, "com.tencent.mm:id/b4m"); //第一条消息
        if (item != null) {
            AccessibilityNodeInfo red = AccessibilityHelper.findNodeInfosById(item, "com.tencent.mm:id/mm");
            if (red != null) { // 有小圆点, 说明有未读消息
                AccessibilityNodeInfo label = AccessibilityHelper.findNodeInfosById(item, "com.tencent.mm:id/b4q");
                if (label != null) {
                    String text = String.valueOf(label.getText());
                    Log.d("qhb", "列表页" + label.getText());
                    int index = text.lastIndexOf(":");
                    if (index != -1) {
                        text = text.substring(index + 1);
                    }
                    if (text.contains(WECHAT_NOTIFICATION_TIP)) {
                        // 有红包, 点开item
                        AccessibilityHelper.performClick(label);
                        return true;
                    }
                }
            }
        }

        //查找所有的聊天item
        /*List<AccessibilityNodeInfo> nodes = eventSource.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/b4m");
        if(null!=nodes && nodes.size()> 0){
            for (int i = 0; i < nodes.size(); i++) {
                List<AccessibilityNodeInfo> findHongBaos = nodes.get(i).findAccessibilityNodeInfosByText(WECHAT_NOTIFICATION_TIP);
                List<AccessibilityNodeInfo> msgMMs = nodes.get(i).findAccessibilityNodeInfosByViewId("com.tencent.mm:id/mm");
                for (int m = 0; m < msgMMs.size(); m++) {
                    AccessibilityNodeInfo mitem = msgMMs.get(m);
                }
                if(null!=findHongBaos && findHongBaos.size()> 0){
                    nodes.get(i).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    break;
                }
            }
        }*/

        /*List<AccessibilityNodeInfo> nodes = eventSource.findAccessibilityNodeInfosByText(WECHAT_NOTIFICATION_TIP);
        //增加条件判断currentActivityName.contains(WECHAT_LUCKMONEY_GENERAL_ACTIVITY)
        //避免当订阅号中出现标题为“[微信红包]拜年红包”（其实并非红包）的信息时误判
        if (!nodes.isEmpty() && currentActivityName.contains(WECHAT_LUCKMONEY_GENERAL_ACTIVITY)) {
            AccessibilityNodeInfo nodeToClick = nodes.get(0);
            if (nodeToClick == null) return false;
            CharSequence contentDescription = nodeToClick.getContentDescription();
            if (contentDescription != null && !signature.getContentDescription().equals(contentDescription)) {
                nodeToClick.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                signature.setContentDescription(contentDescription.toString());
                return true;
            }
        }*/
        return false;
    }

    /**
     * 微信是否运行在前台
     */
    private boolean isRunningForeground(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (!tasks.isEmpty()) {
            String packageName = tasks.get(0).topActivity.getPackageName();
            if (PACKAGE_NAME.equals(packageName)) {
                return true;
            }
        }
        return false;
    }


    private boolean watchNotifications(AccessibilityEvent event) {
        // Not a notification
        if (event.getEventType() != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            return false;
        }
        Log.e("wangkeke", " -------   来通知了");

        // Not a hongbao
        String tip = event.getText().toString();
        if (!tip.contains(WECHAT_NOTIFICATION_TIP)) return true;

        Parcelable parcelable = event.getParcelableData();
        if (parcelable instanceof Notification) {
            Notification notification = (Notification) parcelable;
            try {
                /* 清除signature,避免进入会话后误判 */
                signature.cleanSignature();

                notification.contentIntent.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    @Override
    public void onInterrupt() {

    }

    private void checkNodeInfo() {
        if (this.rootNodeInfo == null) return;

        /* 聊天会话窗口，遍历节点匹配“领取红包”和"查看红包" */
        AccessibilityNodeInfo node1 = (sharedPreferences.getBoolean("pref_watch_self", false)) ?
                this.getTheLastNode(WECHAT_VIEW_OTHERS_CH, WECHAT_VIEW_SELF_CH, WECHAT_VIEW_OTHERS_CH_NEW_TIP) : this.getTheLastNode(WECHAT_VIEW_OTHERS_CH);
        Log.e("wangkeke", "----开始点击红包布局之前");
        if (node1 != null &&
                (currentActivityName.contains(WECHAT_LUCKMONEY_CHATTING_ACTIVITY)
                        || currentActivityName.contains(WECHAT_LUCKMONEY_GENERAL_ACTIVITY))) {
            Log.e("wangkeke", "----开始点击红包布局之后");
            clickOpenPakcet();
        }
    }

    /**
     * 点击抢：com.tencent.mm:id/ao4
     * 开：com.tencent.mm:id/cv0               开
     * 返回上一页：com.tencent.mm:id/k4           < 红包详情
     * 已领取：com.tencent.mm:id/ape
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void clickOpenPakcet() {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {
            //获取当前红包布局list -> AccessibilityNodeInfo
            List<AccessibilityNodeInfo> infos = nodeInfo.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/ao4");
            if (null == infos) {
                return;
            }
            nodeInfo.recycle();
            for (AccessibilityNodeInfo item : infos) {
                List<AccessibilityNodeInfo> hasChais = item.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/ape");
                //如果未领取或者已过期，不再点击
                if (null == hasChais || hasChais.size() == 0) {

                    List<AccessibilityNodeInfo> otherPersonSends = item.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/apb");
                    //如果是自己发的，则不抢
                    if (otherPersonSends.size() == 0) {
                        continue;
                    }

                    //点击未领取过的红包
                    item.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    //点击后继续执行拆红包逻辑
                    checkIsOpenPacket();
                }
            }
        }
    }

    /**
     * 打开红包页面的底部关闭按钮：com.tencent.mm:id/cs9
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void checkIsOpenPacket() {

        Disposable s = Observable.timer(1000, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) throws Exception {
                        //拆红包
                        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
                        if (null == nodeInfo) {
                            return;
                        }

                        List<AccessibilityNodeInfo> listViewNodes = nodeInfo.findAccessibilityNodeInfosByViewId(WECHAT_BETTER_CHAOGUO_24);
                        if (null == listViewNodes || listViewNodes.size() > 0) {
                            //点开了已过期的红包，直接退出
                            retrunChatView("com.tencent.mm:id/cs9");
                            return;
                        }
//                        getLuckyMoney();
                        openRedPackage(nodeInfo);
                        closePage();
                    }
                });
    }

    /**
     * 抢红包
     */
    private void getLuckyMoney() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            List<AccessibilityWindowInfo> nodeInfos = getWindows();
            for (AccessibilityWindowInfo window : nodeInfos) {
                AccessibilityNodeInfo nodeInfo = window.getRoot();
                if (nodeInfo == null) {
                    break;
                }
                List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/cv0");
                if (list != null && list.size() > 0) {
                    list.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//                    AccessibilityUtils.performOpenRedPacketWithDelay(list.get(0));
                    return;
                }
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();  //获得整个窗口对象
            if (nodeInfo == null) {
                return;
            }

            List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/cv0");
            if (list != null && list.size() > 0) {
//                AccessibilityUtils.performOpenRedPacketWithDelay(list.get(0));
                list.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return;
            }

            //如果没找到拆红包的button，则将界面上所有子节点都点击一次
            for (int i = nodeInfo.getChildCount() - 1; i >= 0; i--) {
                if (("android.widget.Button").equals(nodeInfo.getChild(i).getClassName())) {
                    nodeInfo.getChild(i).performAction(AccessibilityNodeInfo.ACTION_CLICK);
//                    AccessibilityUtils.performOpenRedPacketWithDelay(nodeInfo.getChild(i));
                    return;
                }
            }
//            Toast.makeText(this, "未找到开红包按钮", Toast.LENGTH_SHORT).show();
        }
    }

    private void closePage() {
        Observable.timer(1000, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) throws Exception {
                        //拆红包
                        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
                        if (null == nodeInfo) {
                            return;
                        }
                        List<AccessibilityNodeInfo> listGeNodes = nodeInfo.findAccessibilityNodeInfosByText("已存入零钱");
                        List<AccessibilityNodeInfo> listQunNodes = nodeInfo.findAccessibilityNodeInfosByText("个红包共");
                        //领取红包页面关闭
                        //私发红包
                        if (null != listGeNodes && listGeNodes.size() >= 0) {
                            //关闭领取结果页面
                            retrunChatView("com.tencent.mm:id/k4");
                        }
                        //群发红包
                        if (null != listQunNodes && listQunNodes.size() >= 0) {
                            //关闭领取结果页面
                            retrunChatView("com.tencent.mm:id/k4");
                        }

                    }
                });
    }

    /**
     * 模拟点击某个指定坐标作用在View上
     *
     * @param view
     * @param x
     * @param y
     */
    public void clickView(View view, float x, float y) {
        long downTime = SystemClock.uptimeMillis();
        final MotionEvent downEvent = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        downTime += 10;
        final MotionEvent upEvent = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_UP, x, y, 0);
        view.onTouchEvent(downEvent);
        view.onTouchEvent(upEvent);
        downEvent.recycle();
        upEvent.recycle();
    }

    /**
     * 通过ID获取控件，并进行模拟点击
     *
     * @param clickId
     */
    private void retrunChatView(String clickId) {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {
            List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByViewId(clickId);
            Log.e("wangkeke", "retrunChatView -------> " + list.size());
            for (AccessibilityNodeInfo item : list) {
                item.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return;
            }
        }
    }

    public void openRedPackage(AccessibilityNodeInfo info) {

        try {
            for (int i = 0; i < info.getChildCount(); i++) {
                if (info.getChild(i) != null) {
                    if (info.getChild(i).isClickable()) {
                        info.getChild(i).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        return;
                    } else {
                        openRedPackage(info.getChild(i));
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private AccessibilityNodeInfo getTheLastNode(String... texts) {
        int bottom = 0;
        AccessibilityNodeInfo lastNode = null, tempNode;
        List<AccessibilityNodeInfo> nodes;

        for (String text : texts) {
            if (text == null) continue;

            nodes = this.rootNodeInfo.findAccessibilityNodeInfosByText(text);
            Log.e("wangkeke", "nodes = " + nodes);
            if (nodes != null && !nodes.isEmpty()) {
                tempNode = nodes.get(nodes.size() - 1);
                if (tempNode == null) return null;
                Rect bounds = new Rect();
                tempNode.getBoundsInScreen(bounds);
                if (bounds.bottom > bottom) {
                    bottom = bounds.bottom;
                    lastNode = tempNode;
                    signature.others = text.equals(WECHAT_VIEW_OTHERS_CH);
                }
            }
        }
        return lastNode;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Toast.makeText(this, "抢红包功能开启成功！", Toast.LENGTH_SHORT).show();
        this.watchFlagsFromPreference();
    }

    private void watchFlagsFromPreference() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        this.powerUtil = new PowerUtil(this);
        Boolean watchOnLockFlag = sharedPreferences.getBoolean("pref_watch_on_lock", false);
        this.powerUtil.handleWakeLock(watchOnLockFlag);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("pref_watch_on_lock")) {
            Boolean changedValue = sharedPreferences.getBoolean(key, false);
            this.powerUtil.handleWakeLock(changedValue);
        }
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "您已关闭抢红包服务！", Toast.LENGTH_SHORT).show();
        this.powerUtil.handleWakeLock(false);
        super.onDestroy();
    }

    private String generateCommentString() {
        if (!signature.others) return null;

        Boolean needComment = sharedPreferences.getBoolean("pref_comment_switch", false);
        if (!needComment) return null;

        String[] wordsArray = sharedPreferences.getString("pref_comment_words", "").split(" +");
        if (wordsArray.length == 0) return null;

        Boolean atSender = sharedPreferences.getBoolean("pref_comment_at", false);
        if (atSender) {
            return "@" + signature.sender + " " + wordsArray[(int) (Math.random() * wordsArray.length)];
        } else {
            return wordsArray[(int) (Math.random() * wordsArray.length)];
        }
    }
}