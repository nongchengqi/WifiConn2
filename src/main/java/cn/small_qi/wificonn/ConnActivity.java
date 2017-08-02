package cn.small_qi.wificonn;

import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ConnActivity extends AppCompatActivity implements View.OnClickListener {

    private LinearLayout logContainer;
    private SearchThread searchThread;
    private ResponseThread responseThread;
    private boolean in_searching, in_response;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conn);
        findViewById(R.id.start).setOnClickListener(this);
        findViewById(R.id.stop).setOnClickListener(this);
        findViewById(R.id.online).setOnClickListener(this);
        findViewById(R.id.offline).setOnClickListener(this);
        logContainer = (LinearLayout) findViewById(R.id.log);

    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            showLog((String) msg.obj);
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.start:
                if (!in_searching) {
                    searchThread = new SearchThread(mHandler, 20);
                    searchThread.startSearch();
                    in_searching = true;
                } else {
                    Toast.makeText(this, "线程已经启动", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.stop:
                if (in_searching) {
                    searchThread.stopSearch();
                    in_searching = false;
                } else {
                    Toast.makeText(this, "线程未启动", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.online:
                if (!in_response) {
                    responseThread = new ResponseThread(mHandler);
                    responseThread.startResponse();
                    in_response = true;
                } else {
                    Toast.makeText(this, "线程已经启动", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.offline:
                if (in_response) {
                    responseThread.stopResponse();
                    in_response = false;
                } else {
                    Toast.makeText(this, "线程未启动", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private void showLog(final String msg) {
        TextView tv = new TextView(ConnActivity.this);
        tv.setText(msg);
        logContainer.addView(tv);
    }

    /**
     * 搜索线程
     */
    static class SearchThread extends Thread {
        private boolean flag = true;
        private byte[] recvDate = null;
        private byte[] sendDate = null;
        private DatagramPacket recvDP = null;
        private DatagramSocket recvDS = null;
        private DatagramSocket sendDS = null;
        private Handler mHandler;
        private StateChangeListener onStateChangeListener;
        private int state;
        private int maxDevices;//防止广播攻击，设置最大搜素数量
        public static final int STATE_INIT_FINISH = 0;
        public static final int STATE_SEND_BROADCAST = 1;
        public static final int STATE_WAITE_RESPONSE = 2;
        public static final int STATE_HANDLE_RESPONSE = 3;

        public SearchThread(Handler handler, int max) {
            recvDate = new byte[256];
            recvDP = new DatagramPacket(recvDate, 0, recvDate.length);
            mHandler = handler;
            maxDevices = max;

        }

        public void setOnStateChangeListener(StateChangeListener onStateChangeListener) {
            this.onStateChangeListener = onStateChangeListener;
        }

        public void run() {
            try {
                recvDS = new DatagramSocket(54000);
                sendDS = new DatagramSocket();

                changeState(STATE_INIT_FINISH);
                //发送一次广播:广播地址255.255.255.255和组播地址224.0.1.140 --  为了防止丢包，理应多次发送
                sendDate = "name:服务器:msg:你好啊:type:search".getBytes();
                DatagramPacket sendDP = new DatagramPacket(sendDate, sendDate.length, InetAddress.getByName("255.255.255.255"), 53000);
                sendDS.send(sendDP);
                changeState(STATE_SEND_BROADCAST);
                sendMsg("等待接收-----");
                int curDevices = 0;//当前搜索到的设备数量
                while (flag) {
                    changeState(STATE_WAITE_RESPONSE);
                    recvDS.receive(recvDP);
                    changeState(STATE_HANDLE_RESPONSE);
                    String recvContent = new String(recvDP.getData());
                    //判断是不是本机发起的结束搜索请求
                    if (recvContent.contains("stop_search")) {
                        sendMsg("停止搜索：" + flag);
                    } else {
                        if (curDevices >= maxDevices) {
                            break;
                        }
                        sendMsg("收到：" + recvDP.getAddress() + ":" + recvDP.getPort() + " 发来：" + recvContent);
                        //回应
                        sendDate = "name:服务器:msg:你好啊:type:response".getBytes();
                        DatagramPacket responseDP = new DatagramPacket(sendDate, sendDate.length, recvDP.getAddress(), 53000);
                        sendDS.send(responseDP);
                        curDevices++;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();

            } finally {
                if (recvDS != null)
                    recvDS.close();
                if (sendDS != null)
                    sendDS.close();
            }
        }

        private void sendMsg(String string) {
            Message msg = Message.obtain(mHandler);
            msg.obj = string;
            mHandler.sendMessage(msg);
        }

        public void stopSearch() {
            flag = false;
            //由于在等待接收数据包时阻塞，无法达到关闭线程效果，因此给本机发送一个消息取消阻塞状态
            //为了避免用户在UI线程调用，所以新建一个线程
            new Thread() {
                @Override
                public void run() {
                    if (sendDS != null) {
                        sendDate = "name:服务器:msg:stop_search:type:stop".getBytes();
                        try {
                            DatagramPacket sendDP = new DatagramPacket(sendDate, sendDate.length, InetAddress.getByName("localhost"), 54000);
                            sendDS.send(sendDP);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }.start();
        }

        public void startSearch() {
            flag = true;
            start();
            sendMsg("开始搜索");
        }

        private void changeState(int state) {
            this.state = state;
            if (onStateChangeListener != null) {
                onStateChangeListener.onStateChanged(this.state);
            }
        }

        public interface StateChangeListener {
            void onStateChanged(int state);
        }
    }

    /**
     * 等待搜索线程
     */
    static class ResponseThread extends Thread {
        private byte[] recvDate = null;
        private byte[] sendDate = null;
        private DatagramPacket recvDP;
        private DatagramSocket recvDS = null;
        private DatagramSocket sendDS = null;
        private boolean flag = true;
        private Handler mHandler;

        public ResponseThread(Handler handler) {
            recvDate = new byte[256];
            recvDP = new DatagramPacket(recvDate, 0, recvDate.length);
            mHandler = handler;
        }

        public void run() {
            try {
                sendMsg("设备已经开启，等待其他设备搜索...");
                recvDS = new DatagramSocket(53000);
                sendDS = new DatagramSocket();
                while (flag) {
                    recvDS.receive(recvDP);
                    String content = new String(recvDP.getData());
                    if (content.contains("response")) {
                        sendMsg("确认收到回应");
                    } else if (content.contains("stop_receive")) {
                        sendMsg("下线：" + flag);
                    } else {
                        sendMsg("收到：" + recvDP.getAddress() + ":" + recvDP.getPort() + " 发来连接请求：" + content);
                        sendDate = "name:客户端:msg:我收到了:type:response".getBytes();
                        sendMsg("回应>>");
                        DatagramPacket sendDP = new DatagramPacket(sendDate, sendDate.length, recvDP.getAddress(), 54000);
                        sendDS.send(sendDP);
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (recvDS != null)
                    recvDS.close();
                if (sendDS != null)
                    sendDS.close();
            }
        }

        private void sendMsg(String string) {
            Message msg = Message.obtain(mHandler);
            msg.obj = string;
            mHandler.sendMessage(msg);
        }

        public void startResponse() {
            flag = true;
            start();
            sendMsg("上线");
        }

        public void stopResponse() {
            flag = false;
            //为了避免用户在UI线程调用，所以新建一个线程
            new Thread() {
                @Override
                public void run() {
                    if (sendDS != null) {
                        sendDate = "name:客户端:msg:stop_receive:type:stop".getBytes();
                        try {
                            DatagramPacket sendDP = new DatagramPacket(sendDate, sendDate.length, InetAddress.getByName("localhost"), 53000);
                            sendDS.send(sendDP);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }.start();

        }
    }

}
