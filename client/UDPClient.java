import java.net.*;

public class UDPClient {
    public static void main(String[] args) {
        try{
            DatagramSocket ds = new DatagramSocket();
            InetAddress ip = InetAddress.getByName("127.0.0.1");
            byte[] sendData = "Hello from client!".getBytes();

            DatagramPacket dp = new DatagramPacket(sendData, sendData.length, ip, 8080);
            ds.send(dp);

            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            ds.receive(receivePacket);
            System.out.println("Server response " + new String(receivePacket.getData(), 0, receivePacket.getLength()));
            ds.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}