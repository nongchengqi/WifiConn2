package cn.small_qi.wificonn;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by small_qi on 2017/7/21.
 */

public class WifiAdapter extends BaseAdapter {
    private List<ScanResult> scanResults;
    private Context context;
    private WiFiAdmin admin;
    private int wifiState=-1;
    private int opIndex=-1;
    WifiAdapter(Context context){
        this.context = context;
        scanResults = new ArrayList<>();
        admin = WiFiAdmin.getInstance(this.context);
    }
    public void addAllWifis(List<ScanResult> list){
        scanResults.clear();
        scanResults.addAll(list);
        notifyDataSetChanged();
    }
    public void updateState(int state,int position){
        wifiState = state;
        opIndex=position;
        notifyDataSetChanged();
    }
    @Override
    public int getCount() {
        return scanResults.size();
    }

    @Override
    public ScanResult getItem(int position) {
        return scanResults.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        WifiHolder holder = null;
        if (convertView==null){
            convertView = View.inflate(context,R.layout.item,null);
            holder=new WifiHolder();
            holder.name = (TextView) convertView.findViewById(R.id.name);
            holder.other = (TextView) convertView.findViewById(R.id.other);
            holder.state = (TextView) convertView.findViewById(R.id.state);
            holder.level = (TextView) convertView.findViewById(R.id.level);
            holder.save = (TextView) convertView.findViewById(R.id.save);

            convertView.setTag(holder);
        }else{
            holder = (WifiHolder) convertView.getTag();
        }
        ScanResult result = getItem(position);
        // -55~-100
        holder.save.setText(admin.isWifiConfig(result.SSID)==-1?"":"已保存");
        holder.level.setText(countLevel(result.level));
        holder.name.setText(result.SSID);
        holder.other.setText(result.capabilities.substring(1,result.capabilities.indexOf("]")));
        if (wifiState==0&&position==opIndex){
            holder.state.setText("正在连接...");
        }else if (wifiState==1&&position==opIndex){
            holder.state.setText("正在获取IP地址...");
        }else if(wifiState==2&&position==opIndex||admin.getConnectInfo().getSSID().equals("\"" + result.SSID+"\"" )){
            holder.state.setText("已连接");
        }else if(wifiState==3&&position==opIndex){
            holder.state.setText("密码错误");
        }else if(wifiState==4&&position==opIndex){
            holder.state.setText("已断开");
        }else{
            holder.state.setText("");
        }

        return convertView;
    }

    private String countLevel(int level) {
        if (level>=-55){
            return "100%";
        }
        if (level<=-100){
            return "0%";
        }
        int diff = (int) (100f/55f*(level+100f));
        return diff+"%";

    }

    class WifiHolder{
        private TextView name,other,state,level,save;
    }
}
