package com.topzero.passiveDns.socket;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;



public class UDPTest {
    boolean flag = false;
    // 客户端发送数据实现
    public void connectServerWithUDPSocket(String messageStr) {
        String MsgStr = messageStr;
        Log.i("zadaya", "发送数据！！！！！！！！！！！！");
        DatagramSocket socket;
        try {
            //创建DatagramSocket对象并指定一个端口号，注意，如果客户端需要接收服务器的返回数据,
            //还需要使用这个端口号来receive，所以一定要记住
            socket = new DatagramSocket(1985);
            //使用InetAddress(Inet4Address).getByName把IP地址转换为网络地址
            InetAddress serverAddress = InetAddress.getByName("127.0.0.1");
            //Inet4Address serverAddress = (Inet4Address) Inet4Address.getByName("192.168.1.32");
            String str = MsgStr;//设置要发送的报文
            byte data[] = str.getBytes();//把字符串str字符串转换为字节数组
            //创建一个DatagramPacket对象，用于发送数据。
            //参数一：要发送的数据  参数二：数据的长度  参数三：服务端的网络地址  参数四：服务器端端口号
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, 10025);
            socket.send(packet);//把数据发送到服务端。
            //Log.e("zdy", "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS");
            socket.close();

        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 客户端接收服务器返回的数据
    public void ReceiveServerSocketData() {
        Log.i("zadaya", "返回数据！！！！！！！！！！！！");
        int flag = 0;
        flag += 1;
        DatagramSocket socket;
        try {
            //实例化的端口号要和发送时的socket一致，否则收不到data
            socket = new DatagramSocket(1985);
            byte data[] = new byte[4 * 1024];
            //参数一:要接受的data 参数二：data的长度
            DatagramPacket packet = new DatagramPacket(data, data.length);
            socket.receive(packet);
            //把接收到的data转换为String字符串
            String result = new String(packet.getData(), packet.getOffset(),
                    packet.getLength());
            socket.close();//不使用了记得要关闭
            System.out.println("the number of reveived Socket is  :" + Integer.valueOf(flag).toString() + "udpData:" + result);
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 服务器接收客户端实现
    public void ServerReceviedByUdp() {
        //创建一个DatagramSocket对象，并指定监听端口。（UDP使用DatagramSocket）
        Log.i("zadaya", "接收数据！！！！！！！！！！！！");
        DatagramSocket socket;
        try {
            socket = new DatagramSocket(10025);
            //创建一个byte类型的数组，用于存放接收到得数据
            byte data[] = new byte[4 * 1024];
            //创建一个DatagramPacket对象，并指定DatagramPacket对象的大小
            DatagramPacket packet = new DatagramPacket(data, data.length);
            //读取接收到得数据
            socket.receive(packet);

            // 将接收到的数据原路返回发送方
            // packet.setSocketAddress(packet.getSocketAddress());
            Thread.sleep(1000);
            // socket.send(packet);

            //Log.e("zdy", "JJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJ");
            //把客户端发送的数据转换为字符串。
            //使用三个参数的String方法。参数一：数据包 参数二：起始位置 参数三：数据包长
            String result = new String(packet.getData(), packet.getOffset(), packet.getLength());
            Log.i("zadaya", result);
            socket.close();

        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
//       Log.i("zadaya", "&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&******************************");
