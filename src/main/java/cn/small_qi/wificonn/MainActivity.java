package cn.small_qi.wificonn;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private ListView list;
    private WifiAdapter adapter;
    private WiFiAdmin admin;
    private static final int PERMISSION_WIFI_CODE = 1001;
    private static final int PERMISSION_FILE_CODE = 1001;
    private int curPosition=-1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int code = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (code != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_FILE_CODE);
            }
        }
        initList();
        setListener();
        setStateListener();


    }


    private void initList() {
        admin = WiFiAdmin.getInstance(this);
        list = (ListView) findViewById(R.id.list);
        adapter = new WifiAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                curPosition = position;
                final ScanResult result = adapter.getItem(position);
                if (admin.getConnectInfo().getSSID().equals("\""+result.SSID+"\"")){
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("是否断开连接？")
                            .setPositiveButton("断开", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                   admin.disconnectWifi();
                                }
                            })
                            .setNegativeButton("不保存", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    admin.forgetWifi(result.SSID);
                                }
                            })
                            .create().show();
                    return;
                }
                final int security =admin.getSecurity(result) ;
                int netid = admin.isWifiConfig(result.SSID);
                if ( netid == -1) {
                    if (security != WiFiAdmin.SECURITY_NONE) {
                        final EditText pwdEt = new EditText(MainActivity.this);
                        //弹出输入密码对话框
                        new AlertDialog.Builder(MainActivity.this)
                                .setView(pwdEt)
                                .setTitle("请输入密码")
                                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        admin.configWifi(result, pwdEt.getText().toString(),security);
                                       admin.connectWifi(admin.isWifiConfig(result.SSID));
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .create().show();
                    } else {
                        admin.configWifi(result, "",security);
                        admin.connectWifi(admin.isWifiConfig(result.SSID)) ;
                    }
                } else {
                    admin.connectWifi(netid);
                }
            }
        });
    }

    private void setListener() {
        findViewById(R.id.close).setOnClickListener(this);
        findViewById(R.id.open).setOnClickListener(this);
        findViewById(R.id.scan).setOnClickListener(this);
        findViewById(R.id.info).setOnClickListener(this);
        findViewById(R.id.conn).setOnClickListener(this);
    }
    private void setStateListener() {
        admin.addWifiStateChangeListener(new WiFiAdmin.WifiStateChangeListener() {
            @Override
            public void onSignalStrengthChanged(int level) {

            }

            @Override
            public void onWifiConnecting() {
                adapter.updateState(0,curPosition);
            }

            @Override
            public void onWifiGettingIP() {
                adapter.updateState(1,curPosition);
            }

            @Override
            public void onWifiConnected() {
                adapter.updateState(2,curPosition);
            }

            @Override
            public void onWifiDisconnect() {
                adapter.updateState(4,curPosition);

            }

            @Override
            public void onWifiEnabling() {

            }

            @Override
            public void onWifiEnable() {

            }

            @Override
            public void onPasswordError() {
                adapter.updateState(3,curPosition);
            }

            @Override
            public void onWifiIDChange() {

            }
        });
    }


    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int code = ContextCompat.checkSelfPermission(this, Manifest.permission_group.LOCATION);
            if (code != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_WIFI_CODE);
            } else {
                admin.startWifiScan();
                adapter.addAllWifis(admin.getScanResults());
            }
        } else {
            admin.startWifiScan();
            adapter.addAllWifis(admin.getScanResults());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        admin.removeWifiStateChangeListener();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_WIFI_CODE:
                if (grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    admin.startWifiScan();
                    adapter.addAllWifis(admin.getScanResults());
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;

        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.close:
                admin.closeWifi();
                break;
            case R.id.open:
                admin.openWifi();
                break;
            case R.id.scan:
                //6.0权限
                checkPermission();
                break;
            case R.id.info:
                showInfo();
                break;
            case R.id.conn:
                if (admin!=null&&admin.getConnectInfo().getIpAddress()!=0){
                    startActivity(new Intent(MainActivity.this,ConnActivity.class));
                }else{
                    Toast.makeText(this, "请先连接Wifi或者等待Wifi连接成功", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private void showInfo() {
        WifiInfo info = admin.getConnectInfo();
        if (info == null) {
            Toast.makeText(MainActivity.this, "当前未连接到WIFI", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("当前连接WIFI信息：")
                .setMessage(info.getSSID() + "\n" + info.getMacAddress() + "\n" + WiFiAdmin.parseIPAddressToString(info.getIpAddress()) + "\n" +
                        info.getLinkSpeed() + "\n" + info.getBSSID() + "\n" + info.getRssi())
                .create()
                .show();
    }
}
