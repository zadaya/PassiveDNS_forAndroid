package com.topzero.passiveDns;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.JsonReader;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.gson.Gson;
import com.topzero.passiveDns.model.DnsLogs;
import com.topzero.passiveDns.service.MyVPNService;
import com.topzero.passiveDns.socket.UdpSocket;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

public class MainActivity extends AppCompatActivity {

    // UI组件
    private Button startVpnBtn;
    private Button udpTestBtn;
    private EditText sendMessageEdit;
    private UdpSocket UdpSocketTest;

    // 等待 Vpn 启动状态
    private boolean waittingVpnStart;
    // Vpn运行状态
    private boolean vpnServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startVpnBtn = (Button) findViewById(R.id.startVPN_btn);
        udpTestBtn = (Button) findViewById(R.id.UDP_Socket_Test);
        sendMessageEdit = (EditText) findViewById(R.id.sendMessageEdit);
        UdpSocketTest = new UdpSocket();
        startVpnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = VpnService.prepare(MainActivity.this);
                if (intent != null) {
                    startActivityForResult(intent, 0);
                } else {
                    onActivityResult(0, RESULT_OK, null);
                }
            }
        });
        udpTestBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                String msgStr = sendMessageEdit.getText().toString().trim();
//                byte[] msgByte = msgStr.getBytes();
//                UdpSocketTest.ServerReceviedByUdp();
//                new UdpSocket().connectServerWithUDPSocket(msgByte);
//            UdpSocketTest.parseJSONWithJSONObject("[{ \"stu_no\":12345,\"stu_name\":\"John\",\"stu_sex\":\"male\"\n" +
//                    "},{ \"stu_no\":12346,\"stu_name\":\"Tom\",\"stu_sex\":\"male\"\n" +
//                    "},{\"stu_no\":12347,\"stu_name\":\"Lily\",\"stu_sex\":\"female\"}]");
                String msgStr = sendMessageEdit.getText().toString().trim();
                DnsLogs dnsLogs = new DnsLogs("test.zadaya.com","111.111.111.111","222.222.222.222","333.333.333.333",new Date());
                Gson gson = new Gson();
                msgStr = gson.toJson(dnsLogs);
                UdpSocketTest.setBytes(msgStr.getBytes());
                new Thread(UdpSocketTest).start();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 设置按钮状态
        this.setButtonEnableStatus(!waittingVpnStart && !MyVPNService.isRunning());

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            // 开启服务
            Intent intent = new Intent(this, MyVPNService.class);
            startService(intent);

            this.waittingVpnStart = true;
            // 调整启动 VPN 按钮状态
            this.setButtonEnableStatus(false);
        }
    }

    // 设置启动防火墙按钮状态
    private void setButtonEnableStatus(boolean enable) {
        // 设置激活状态
        this.startVpnBtn.setEnabled(enable);
        // 设置文字
        this.startVpnBtn.setText(
                enable ? getString(R.string.MainActivity_startVpnBtn__enable__text) :
                        getString(R.string.MainActivity_startVpnBtn__disable__text)
        );
    }

    // 用于接受服务广播
    private BroadcastReceiver vpnStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 如果收到了服务已经运行的广播，则刚更新 flag
            if (MyVPNService.BROADCAST_VPN_STATE.equals(intent.getAction()) &&
                    intent.getBooleanExtra("running", false)) {
                waittingVpnStart = false;
                vpnServiceRunning = true;
            }
        }
    };
}
