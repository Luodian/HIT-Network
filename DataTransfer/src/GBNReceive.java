import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class GBNReceive extends Thread
{
	private static int serverPort;
	private static DatagramPacket receivePacket;
	private static DatagramPacket sendPacket;
	private static DatagramSocket serverSocket;
	
	private static byte[] receive = new byte[1024];
	private static byte[] send = new byte[20];
	
	private static int last;
	
	private static int dataNum;
	private static int toReceiveNum;
	
	static int in_port;
	static int out_port;
	
	
	GBNReceive (int listen_port, int send_port)
	{
		last = -1;
		
		in_port = listen_port;
		out_port = send_port;
		dataNum = conf.GBN_DATA_NUM;
		toReceiveNum = dataNum;
		System.out.println ("启动监听端口：" + listen_port);
		
		try
		{
			serverSocket = new DatagramSocket (listen_port);
		} catch (SocketException e)
		{
		
		}
	}
	
	@Override
	public void run ()
	{
		while (true)
		{
			
			receivePacket = new DatagramPacket (receive, receive.length);
			try
			{
				serverSocket.receive (receivePacket);
				
			} catch (IOException ignored)
			{
			
			}
			
			int receiveSeq = -1;
			if (receive[0] == 'T' && receive[1] == 'i' && receive[2] == 'm' && receive[3] == 'e')
			{
				InetAddress inetAddress = receivePacket.getAddress ();
				int clientPort = receivePacket.getPort ();
				SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
//				System.out.println(df.format(new Date ()));// new Date()为获取当前系统时间
				
				send = ("200-" + df.format (new Date ())).getBytes ();
				
				sendPacket = new DatagramPacket (send, send.length, inetAddress, clientPort);
			}
			else
			{
				if (receive[1] == 'x')
				{
					receiveSeq = receive[0] - '0';
				} else
				{
					receiveSeq = (receive[0] - '0') * 10 + (receive[1] - '0');
				}
				//指定丢包概率,取值范围是[0.0,1.0)的左闭右开区间.
				if (Math.random () < 0.6)
				{
					
					if (receiveSeq == last + 1)
					{
						System.out.println ("成功接收序列为" + receiveSeq + "的数据包.");
						//接收正确的序列，构造ACK报文，并发回给客户端
						if (receiveSeq < 10)
						{
							send = ("ACK" + receiveSeq + "x").getBytes ();
						} else if (receiveSeq < 100)
						{
							send = ("ACK" + receiveSeq).getBytes ();
						}
						InetAddress inetAddress = receivePacket.getAddress ();
						int clientPort = receivePacket.getPort ();
						sendPacket = new DatagramPacket (send, send.length, inetAddress, clientPort);
						try
						{
							serverSocket.send (sendPacket);
							toReceiveNum--;
							last++;
						} catch (IOException ignored)
						{
						
						}
						System.out.println ("发送序列为" + receiveSeq + "的ACK.");
					}
					//如果接收到的seq小于接收方维护的last下届，重传seq
					else if (receiveSeq < (last + 1))
					{
						if (receiveSeq < 10)
						{
							send = ("ACK" + receiveSeq + "x").getBytes ();
						} else if (receiveSeq < 100)
						{
							send = ("ACK" + receiveSeq).getBytes ();
						}
						InetAddress inetAddress = receivePacket.getAddress ();
						int clientPort = receivePacket.getPort ();
						sendPacket = new DatagramPacket (send, send.length, inetAddress, clientPort);
						try
						{
							serverSocket.send (sendPacket);
							
						} catch (IOException ignored)
						{
						
						}
					}
					else if (last != -1 && toReceiveNum > 0)
					{
						System.out.println ("产生丢包!当前序号为：" + receiveSeq);
						System.out.println ("应当接收序号为：" + (last + 1));
						//返回序号为上次成功接受到的ACK
						if (last < 10)
						{
							send = ("ACK" + last + "x").getBytes ();
						} else if (last < 100)
						{
							send = ("ACK" + last).getBytes ();
						}
						InetAddress inetAddress = receivePacket.getAddress ();
						int clientPort = receivePacket.getPort ();
						sendPacket = new DatagramPacket (send, send.length, inetAddress, clientPort);
						try
						{
							serverSocket.send (sendPacket);
							toReceiveNum++;
						} catch (IOException ignored)
						{
						
						}
						System.out.println ("发送序列为" + last + "的ACK");
					}
				}
			}
		}
		
	}
}
