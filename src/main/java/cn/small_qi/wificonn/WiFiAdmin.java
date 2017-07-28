package cn.small_qi.wificonn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by small_qi on 2017/7/21.
 */
public class WiFiAdmin {
    private WifiManager mWifiManager;
    private List<ScanResult> mScanResults;
    private Context mContext;
    private WifiStateReceiver mReceiver;
    private WifiStateChangeListener mWifiStateChangeListener;
    private WifiManager.WifiLock mWifiLock;
    private static WiFiAdmin mWifiAdmin;
    private boolean isRegisterRecv;
    private boolean isWifiLock;

    public static final int SECURITY_WEP = 3;//WEP
    public static final int SECURITY_WPA = 2;//WPA/WPA2
    public static final int SECURITY_WPA_PSK = 1;//WPA-PSK/WPA2-PSK
    public static final int SECURITY_NONE = 0;//没有密码
    public static final int ORDER_SMART_SORT = 0;//智能排序，
    public static final int ORDER_SIGNAL_LEVEL_SORT = 1;//信号强度排序

    /***
     * 构造方法  - - 由于使用单例，所以设为私有
     * @param context
     */
    private WiFiAdmin(Context context) {
        mContext = context;
        //如果使用activity的context则不能访问存储空间，在版本大于Android N时,因此使用全局的Context
        mWifiManager = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mWifiLock = mWifiManager.createWifiLock("mlock");
    }

    /**
     * 对外面公开的获取实例的方法
     *
     * @param context
     * @return 新建的
     */
    public static WiFiAdmin getInstance(Context context) {
        if (mWifiAdmin == null) {
            mWifiAdmin = new WiFiAdmin(context);
        }
        return mWifiAdmin;
    }

    /**
     * 设置网络状态监听
     * 如果设置了监听，一定要在调用的Activity/Fragment
     * 的生命周期结束时调用{@link #removeWifiStateChangeListener()}
     * 避免内存泄漏
     */
    public void addWifiStateChangeListener(WifiStateChangeListener wscListener) {
        if (isRegisterRecv) return;
        registerWifiRecv();
        this.mWifiStateChangeListener = wscListener;
    }

    private void registerWifiRecv() {
        //注册广播
        mReceiver = new WifiStateReceiver();
        IntentFilter mFilter = new IntentFilter();
        mFilter.addAction(WifiManager.RSSI_CHANGED_ACTION); //信号强度变化
        mFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION); //网络状态变化
        mFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION); //wifi状态，是否连上，密码
        mFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION); //是不是正在获得IP地址
        mFilter.addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION);
        mFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(mReceiver, mFilter);
        isRegisterRecv = true;
    }

    public void removeWifiStateChangeListener() {
        if (!isRegisterRecv) return;
        isRegisterRecv = false;
        mContext.unregisterReceiver(mReceiver);
    }


    public void openWifi() {
        if (!mWifiManager.isWifiEnabled()) {
            mWifiManager.setWifiEnabled(true);
        }
    }

    public void closeWifi() {
        if (mWifiManager.isWifiEnabled()) {
            mWifiManager.setWifiEnabled(false);
        }
    }

    public boolean lockWifi() {
        if (mWifiLock == null) {
            return false;
        }
        mWifiLock.acquire();
        isWifiLock = true;
        return true;
    }

    public boolean unLockWifi() {
        if (mWifiLock == null) {
            return false;
        }
        mWifiLock.release();
        isWifiLock = false;
        return true;
    }

    public boolean isWifiLock() {
        return isWifiLock;
    }

    /**
     * 获取网卡状态
     *
     * @return
     */
    public int getWifiState() {
        /**
         * WIFI网卡的状态是由一系列的整形常量来表示的。
         　　1.WIFI_STATE_DISABLED : WIFI网卡不可用（1）
         　　2.WIFI_STATE_DISABLING : WIFI网卡正在关闭（0）
         　　3.WIFI_STATE_ENABLED : WIFI网卡可用（3）
         　　4.WIFI_STATE_ENABLING : WIFI网正在打开（2） （WIFI启动需要一段时间）
         　　5.WIFI_STATE_UNKNOWN  : 未知网卡状态
         */
        if (mWifiManager.isWifiEnabled()) {
            return mWifiManager.getWifiState();
        }
        return -1;
    }

    /**
     * 启动扫描,扫描前应使用 {@link #getWifiState()}判断WIFI是否可用
     * 或者在回调函数 mWifiStateChangeListener.onWifiEnable() 调用
     */
    public void startWifiScan() {
        mWifiManager.startScan();
        mScanResults = mWifiManager.getScanResults();
    }


    /**
     * 获取排序后的扫描结果
     *
     * @param order 排序方式
     *              1.只按信号强度排序
     *              2.已经保存的在前面，其他按强度排序
     */
    public List<ScanResult> getOrderScanResults(int order) {
        List<ScanResult> sortResult = mScanResults;
        if (order == ORDER_SIGNAL_LEVEL_SORT) {
            levelSort(sortResult);
        } else if (order == ORDER_SMART_SORT) {
            smartSort(sortResult);
        }
        return null;
    }

    private void levelSort(List<ScanResult> sortResult) {
        Collections.sort(sortResult, new Comparator<ScanResult>() {
            @Override
            public int compare(ScanResult o1, ScanResult o2) {
                return o1.level - o2.level;
            }
        });
    }

    private void smartSort(List<ScanResult> sortResult) {
        Collections.sort(sortResult, new Comparator<ScanResult>() {
            @Override
            public int compare(ScanResult o1, ScanResult o2) {
                if (isWifiConfig(o1.SSID) > 0 && isWifiConfig(o2.SSID) > 0) {
                    return o1.level - o2.level;
                } else if (isWifiConfig(o1.SSID) > 0 || isWifiConfig(o2.SSID) > 0) {
                    return isWifiConfig(o1.SSID) - isWifiConfig(o2.SSID);
                } else {
                    return o1.level - o2.level;
                }
            }
        });
    }

    /**
     * 获取扫描结果
     */
    public List<ScanResult> getScanResults() {
        return mScanResults;
    }

    /**
     * 获取已经保存的wifi列表
     */
    public List<WifiConfiguration> getConfigWifiList() {
        List<WifiConfiguration> configurations = mWifiManager.getConfiguredNetworks();
        return configurations;
    }

    /**
     * 判断该wifi是否已经保存
     *
     * @return 返回-1表示没保存，已经保存返回网络id
     */
    public int isWifiConfig(String ssid) {
        List<WifiConfiguration> lists = getConfigWifiList();
        for (WifiConfiguration c : lists) {
            if (c.SSID.equals("\"" + ssid + "\"")) {
                return c.networkId;
            }
        }
        return -1;
    }

    /**
     * 配置没有保存的wifi
     * 一般只要配置一下几个属性就可以了，其他使用其，默认值
     * @param scanResult
     * @param pwd
     * @return 保存成功则返回该Wifi的网络id，否则-1
     */
    public int configWifi(ScanResult scanResult, String pwd,int security) {
        return configWifi(scanResult.SSID,pwd,security);

    }

    /**
     * 配置方法重载
     */
    public int configWifi(String ssid, String pwd,int security) {
        int result = -1;
        for (ScanResult s : mScanResults) {
            if (s.SSID.equals(ssid)) {
                WifiConfiguration config = new WifiConfiguration();
                config.SSID = "\"" + ssid + "\"";
                config.hiddenSSID = false;
                config.status = WifiConfiguration.Status.ENABLED;
                switch (security){
                    case SECURITY_NONE:
                        config.wepKeys[0] ="\""+pwd+"\"";
                        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                        config.wepTxKeyIndex = 0;
                        break;
                    case SECURITY_WEP:
                        config.wepKeys[0]= "\""+pwd+"\"";
                        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                        config.wepTxKeyIndex = 0;
                        break;
                    case SECURITY_WPA:
                    case SECURITY_WPA_PSK:
                        config.preSharedKey = "\"" + pwd + "\"";
                        break;
                    default:
                        break;
                }
                result = mWifiManager.addNetwork(config);
                break;
            }
        }
        return result;
    }

    /**
     * 配置方法重载，用于更复杂的配置
     */
    public int configWifi(WifiConfiguration config) {
        return mWifiManager.addNetwork(config);
    }

    /**
     * 获取加秘方式
     */
    public int getSecurity(ScanResult scanResult) {
        return getSecurity(scanResult.capabilities);
    }

    /**
     * 获取加秘方式
     *
     * @param capabilities
     * @return 加密类型, 具体类型请查看： {@link #SECURITY_NONE}{@link #SECURITY_WEP}{@link #SECURITY_WPA}{@link #SECURITY_WPA_PSK}
     */
    public int getSecurity(String capabilities) {
        if (capabilities.contains("WEP")) {
            return SECURITY_WEP;
        } else if (capabilities.contains("WPA")) {
            if (capabilities.contains("PSK"))
                return SECURITY_WPA_PSK;
            return SECURITY_WPA;
        } else {
            return SECURITY_NONE;
        }
    }

    /**
     * 删除/忘记一个wifi（也就是通常的不保存）
     *
     * @param ssid 要忘记网络名成
     * @return 执行结果
     */
    public boolean forgetWifi(String ssid) {
        for (WifiConfiguration c : getConfigWifiList()) {
            if (c.SSID.equals("\"" + ssid + "\"")) {
                return mWifiManager.removeNetwork(c.networkId);
            }
        }
        return false;
    }

    /**
     * 断开连接
     * @return
     */
    public boolean disconnectWifi(){
        return mWifiManager.disableNetwork(getConnectInfo().getNetworkId());
       // mWifiManager.disconnect();//断流
    }

    /**
     * 连接wifi
     *
     * @param netId wifi网络id
     * @return 连接结果
     */
    public boolean connectWifi(int netId) {
        return mWifiManager.enableNetwork(netId, true);
    }

    /**
     * 获取已经连接的WIFI信息
     */
    public WifiInfo getConnectInfo() {
        return mWifiManager.getConnectionInfo();
    }

    /**
     * IP 地址转换
     *
     * @param ip 转换前的IP
     * @return 转换后的IP
     */
    public static String parseIPAddressToString(int ip) {
        return ((ip & 0xff) + "." + (ip >> 8 & 0xff) + "." + (ip >> 16 & 0xff) + "." + (ip >> 24 & 0xff));
    }

    /**
     * 广播监听内部类
     */
    class WifiStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isRegisterRecv) return;//没注册监听就没必要执行之后的逻辑
            String action = intent.getAction();
            switch (action) {
                case WifiManager.RSSI_CHANGED_ACTION:
                    //信号强度变化
                    mWifiStateChangeListener.onSignalStrengthChanged(getStrength(context));
                    break;
                case WifiManager.NETWORK_STATE_CHANGED_ACTION:
                    NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) {
                        //wifi已断开
                        mWifiStateChangeListener.onWifiDisconnect();
                    } else if (info.getState().equals(NetworkInfo.State.CONNECTING)) {
                        //正在连接...
                        mWifiStateChangeListener.onWifiConnecting();
                    } else if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
                        //连接到网络
                        mWifiStateChangeListener.onWifiConnected();
                    }else if(info.getDetailedState().equals(NetworkInfo.DetailedState.OBTAINING_IPADDR)){
                        //正在获取IP地址
                        mWifiStateChangeListener.onWifiGettingIP();
                    }

                    break;
                case WifiManager.WIFI_STATE_CHANGED_ACTION:
                    int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
                    switch (wifiState) {
                        case WifiManager.WIFI_STATE_ENABLING:
                            //wifi正在启用
                            mWifiStateChangeListener.onWifiEnabling();
                            break;
                        case WifiManager.WIFI_STATE_ENABLED:
                            //Wifi已启用
                            mWifiStateChangeListener.onWifiEnable();
                            break;
                    }
                    break;
                case WifiManager.SUPPLICANT_STATE_CHANGED_ACTION:
                    int error = intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, 0);
                    switch (error) {
                        case WifiManager.ERROR_AUTHENTICATING:
                            //wifi密码认证错误！
                            mWifiStateChangeListener.onPasswordError();
                            break;
                        default:
                            break;
                    }
                    break;
                case WifiManager.NETWORK_IDS_CHANGED_ACTION:
                    //已经配置的网络的ID可能发生变化时
                    mWifiStateChangeListener.onWifiIDChange();
                    break;
                case ConnectivityManager.CONNECTIVITY_ACTION:
                    //连接状态发生变化，暂时没用到
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 计算信号强度
     *
     * @param context 含有WIFI信息的资源对象
     * @return 信号强度
     */
    private int getStrength(Context context) {
        WifiInfo info = getConnectInfo();
        if (info.getBSSID() != null) {
            int strength = WifiManager.calculateSignalLevel(info.getRssi(), 5);
            // 链接速度
//			int speed = info.getLinkSpeed();
//			// 链接速度单位
//			String units = WifiInfo.LINK_SPEED_UNITS;
//			// Wifi源名称
//			String ssid = info.getSSID();
            return strength;
        }
        return 0;
    }

    /**
     * WIFI状态变化回调接口
     */
    public interface WifiStateChangeListener {
        //void onRssiChanged();
        //void onNetWorkStateChanged();
        //void onWifiStateChanged();
        //void onSupplicantStateChanged();
        // void NetWorkIDSChange();
        void onSignalStrengthChanged(int level);

        void onWifiConnecting();

        void onWifiGettingIP();

        void onWifiConnected();

        void onWifiDisconnect();

        void onWifiEnabling();

        void onWifiEnable();

        void onPasswordError();

        void onWifiIDChange();
        //void onWifiLock(int isLock);

    }


    //-------------------------------以下部分为开启热点-------------------------------------

    /**
     * 创建热点
     *
     * @param mSSID   热点名称
     * @param mPasswd 热点密码
     * @param isOpen  是否是开放热点
     */
    public void startWifiAp(String mSSID, String mPasswd, boolean isOpen) {
        Method method1 = null;
        try {
            method1 = mWifiManager.getClass().getMethod("setWifiApEnabled",
                    WifiConfiguration.class, boolean.class);
            WifiConfiguration netConfig = new WifiConfiguration();

            netConfig.SSID = mSSID;
            netConfig.preSharedKey = mPasswd;
            netConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            netConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            netConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            if (isOpen) {
                netConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            } else {
                netConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            }
            netConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            netConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            netConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            netConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            method1.invoke(mWifiManager, netConfig, true);

        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取热点名
     **/
    public String getApSSID() {
        try {
            Method localMethod = this.mWifiManager.getClass().getDeclaredMethod("getWifiApConfiguration", new Class[0]);
            if (localMethod == null) return null;
            Object localObject1 = localMethod.invoke(this.mWifiManager, new Object[0]);
            if (localObject1 == null) return null;
            WifiConfiguration localWifiConfiguration = (WifiConfiguration) localObject1;
            if (localWifiConfiguration.SSID != null) return localWifiConfiguration.SSID;
            Field localField1 = WifiConfiguration.class.getDeclaredField("mWifiApProfile");
            if (localField1 == null) return null;
            localField1.setAccessible(true);
            Object localObject2 = localField1.get(localWifiConfiguration);
            localField1.setAccessible(false);
            if (localObject2 == null) return null;
            Field localField2 = localObject2.getClass().getDeclaredField("SSID");
            localField2.setAccessible(true);
            Object localObject3 = localField2.get(localObject2);
            if (localObject3 == null) return null;
            localField2.setAccessible(false);
            String str = (String) localObject3;
            return str;
        } catch (Exception localException) {
        }
        return null;
    }


    /**
     * 检查是否开启Wifi热点
     *
     * @return
     */
    public boolean isWifiApEnabled() {
        try {
            Method method = mWifiManager.getClass().getMethod("isWifiApEnabled");
            method.setAccessible(true);
            return (boolean) method.invoke(mWifiManager);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 关闭热点
     */
    public void closeWifiAp() {
        WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        if (isWifiApEnabled()) {
            try {
                Method method = wifiManager.getClass().getMethod("getWifiApConfiguration");
                method.setAccessible(true);
                WifiConfiguration config = (WifiConfiguration) method.invoke(wifiManager);
                Method method2 = wifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
                method2.invoke(wifiManager, config, false);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 开热点手机获得其他连接手机IP的方法
     *
     * @return 其他手机IP 数组列表
     */
    public ArrayList<String> getConnectedIP() {
        ArrayList<String> connectedIp = new ArrayList<String>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(
                    "/proc/net/arp"));
            String line;
            while ((line = br.readLine()) != null) {
                String[] splitted = line.split(" +");
                if (splitted != null && splitted.length >= 4) {
                    String ip = splitted[0];
                    if (!ip.equalsIgnoreCase("ip")) {
                        connectedIp.add(ip);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return connectedIp;
    }


}
