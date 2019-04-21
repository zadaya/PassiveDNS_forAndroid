package com.topzero.passiveDns.service;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.topzero.passiveDns.net.TCPInput;
import com.topzero.passiveDns.net.TCPOutput;
import com.topzero.passiveDns.net.UDPInput;
import com.topzero.passiveDns.net.UDPOutput;
import com.topzero.passiveDns.model.ByteBufferPool;
import com.topzero.passiveDns.model.Packet;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Selector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyVPNService extends VpnService {

    // TAG
    private static final String TAG = "MyVPNService";

    // 运行状态
    private static boolean running = false;

    // 广播 Vpn 状态
    public static final String BROADCAST_VPN_STATE = "com.topzero.passiveDns.VPN_STATE";

    // VPN 参数
    private static final String VPN_ADDRESS = "10.0.0.2";
    private static final int VPN_ADDRESS_MASK = 32;
    private static final String VPN_ROUTE = "0.0.0.0";
    private static final int VPN_ROUTE_MASK = 0;
    private static final int VPN_MTU = 1500;

    // 文件描述符
    private ParcelFileDescriptor parcelFileDescriptor = null;

    // 队列
    private ConcurrentLinkedQueue<Packet> deviceToNetworkUDPQueue;
    private ConcurrentLinkedQueue<Packet> deviceToNetworkTCPQueue;
    private ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue;
    // 线程池
    private ExecutorService executorService;

    // TCP、UDP选择器
    private Selector udpSelector;
    private Selector tcpSelector;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // 设置运行状态
        MyVPNService.running = true;
        // 建立 VPN 连接
        if (this.parcelFileDescriptor == null) {
            // 设置参数
            Builder builder = new Builder();
            builder.setSession("MyVpnTest-zadaya");
            builder.setMtu(VPN_MTU);
            builder.addAddress(VPN_ADDRESS, VPN_ADDRESS_MASK);
            builder.addRoute(VPN_ROUTE, VPN_ROUTE_MASK);

            // 建立连接
            this.parcelFileDescriptor = builder.establish();
        }

        try {
            // 配置选择器
            udpSelector = Selector.open();
            tcpSelector = Selector.open();

            // 创建队列
            deviceToNetworkUDPQueue = new ConcurrentLinkedQueue<>();
            deviceToNetworkTCPQueue = new ConcurrentLinkedQueue<>();
            networkToDeviceQueue = new ConcurrentLinkedQueue<>();

            // 创建线程池——开启5个线程
            executorService = Executors.newFixedThreadPool(5);

            // 每个线程负责一个任务
            executorService.submit(new UDPInput(networkToDeviceQueue, udpSelector));
            executorService.submit(new UDPOutput(deviceToNetworkUDPQueue, udpSelector, this));
            executorService.submit(new TCPInput(networkToDeviceQueue, tcpSelector));
            executorService.submit(new TCPOutput(deviceToNetworkTCPQueue, networkToDeviceQueue, tcpSelector, this));
            executorService.submit(new VPNRunnable(
                    parcelFileDescriptor.getFileDescriptor(),
                    deviceToNetworkUDPQueue,
                    deviceToNetworkTCPQueue,
                    networkToDeviceQueue,
                    true,
                    true
            ));

            // 发送广播告知 passiveDnsService 已经运行
            LocalBroadcastManager
                    .getInstance(this)
                    .sendBroadcast(
                            new Intent(BROADCAST_VPN_STATE).putExtra("running", true)
                    );

            Log.i(MyVPNService.TAG, "passiveDnsService Started");

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(MyVPNService.TAG, "Can't start passiveDnsService");

            // 清理
            clean();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        executorService.shutdownNow();
        clean();
        Log.i(MyVPNService.TAG, "MyVpnService Stopped");
    }

    // 清理函数
    private void clean() {
        this.deviceToNetworkUDPQueue = null;
        this.deviceToNetworkTCPQueue = null;
        this.networkToDeviceQueue = null;
        ByteBufferPool.clear();
        MyVPNService.closeResource(udpSelector, tcpSelector, parcelFileDescriptor);
    }

    // 清理资源
    private static void closeResource(Closeable... resources) {
        for (Closeable resource : resources) {
            try {
                resource.close();
            } catch (Exception e) {
                e.printStackTrace();
                Log.i(MyVPNService.TAG, "Clean resources failed");
            }
        }
    }

    // Vpn 服务线程
    private static class VPNRunnable implements Runnable {

        private static final String TAG = "MyVpnThread";

        // 文件描述符
        private FileDescriptor fileDescriptor;

        // 队列
        private ConcurrentLinkedQueue<Packet> deviceToNetworkUDPQueue;
        private ConcurrentLinkedQueue<Packet> deviceToNetworkTCPQueue;
        private ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue;

        // 流控模式
        private boolean isUdpFlowModeSpy = true;
        private boolean isTcpFlowModeSpy = true;

        // 构造
        public VPNRunnable(
                FileDescriptor fileDescriptor,
                ConcurrentLinkedQueue<Packet> deviceToNetworkUDPQueue,
                ConcurrentLinkedQueue<Packet> deviceToNetworkTCPQueue,
                ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue,
                boolean isUdpFlowModeSpy,
                boolean isTcpFlowModeSpy
        ) {
            this.fileDescriptor = fileDescriptor;
            this.deviceToNetworkUDPQueue = deviceToNetworkUDPQueue;
            this.deviceToNetworkTCPQueue = deviceToNetworkTCPQueue;
            this.networkToDeviceQueue = networkToDeviceQueue;
            this.isUdpFlowModeSpy = isUdpFlowModeSpy;
            this.isTcpFlowModeSpy = isTcpFlowModeSpy;
        }

        @Override
        public void run() {
            Log.i(VPNRunnable.TAG, "VPN thread started");

            // 获取 channel
            FileChannel vpnInput = new FileInputStream(fileDescriptor).getChannel();
            FileChannel vpnOutput = new FileOutputStream(fileDescriptor).getChannel();

            try {
                // 创建发送缓冲区
                ByteBuffer bufferToNetwork = null;
                boolean dataSent = true;
                boolean dataReceived;

                // 开始循环
                while (!Thread.interrupted()) {
                    if (dataSent) {
                        // 如果已经发送了数据，则从缓冲池中获取一个缓冲区
                        bufferToNetwork = ByteBufferPool.acquire();
                    } else {
                        // 如果还没发送，则先清空
                        bufferToNetwork.clear();
                    }

                    // 读取一个来自物理网卡的数据包
                    int readLength = vpnInput.read(bufferToNetwork);
                    if (readLength > 0) {
                        // 如果读取到了数据
                        Log.i(VPNRunnable.TAG, "get a ip packet");
                        dataSent = true;

                        // 在读取数据前先将 limit 设置为 position，position 设置为 0
                        bufferToNetwork.flip();

                        // 拆包
                        Packet packet = new Packet(bufferToNetwork);

                        // 判断包的种类
                        if (packet.isUDP()) {
                            // 如果是 UDP 包
                            Log.i(VPNRunnable.TAG, "it's a UDP packet");
                            // 在队列中加入包
                            if (this.isUdpFlowModeSpy) deviceToNetworkUDPQueue.offer(packet);
                        } else if (packet.isTCP()) {
                            // 如果是 TCP 包
                            Log.i(VPNRunnable.TAG, "it's a TCP packet");
                            // 在队列中加入包
                            if (this.isTcpFlowModeSpy) deviceToNetworkTCPQueue.offer(packet);
                        } else {
                            // 如果是其他包
                            Log.i(VPNRunnable.TAG, "it's a unknown type packet");
                            Log.i(VPNRunnable.TAG, packet.ip4Header.toString());
                            dataSent = false;
                        }
                    } else {
                        // 如果没有读取到数据
                        dataSent = false;
                    }

                    // 每次都尝试从收外部网络的队列取走一个数据包
                    ByteBuffer bufferFromNetwork = networkToDeviceQueue.poll();
                    // 如果拿到了数据
                    if (bufferFromNetwork != null) {
                        // 准备读取
                        bufferFromNetwork.flip();
                        // 将数据一股脑写回物理网卡
                        vpnOutput.write(bufferFromNetwork);
                        // 设置收到数据标识
                        dataReceived = true;
                        // 从缓冲池中释放缓冲区
                        ByteBufferPool.release(bufferFromNetwork);
                    } else {
                        dataReceived = false;
                    }

                    // 无收也无发，则让线程睡一会
                    if (!dataSent && !dataReceived) {
                        Thread.sleep(10);
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.e(VPNRunnable.TAG, "get a interrupted exception");
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(VPNRunnable.TAG, "get a IO exception");
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(VPNRunnable.TAG, "get a exception");
            } finally {
                MyVPNService.closeResource(vpnInput, vpnOutput);
            }
        }
    }

    // Getters
    public static boolean isRunning() {
        return running;
    }
}
