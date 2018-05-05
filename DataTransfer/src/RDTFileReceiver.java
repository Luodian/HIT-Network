import java.io.*;
import java.net.*;

public class RDTFileReceiver extends Thread
{
	private static DatagramPacket receivePacket;
	private static DatagramPacket sendPacket;
	private static DatagramSocket serverSocket;
	
	private static byte[] receive = new byte[1024];
	private static byte[] send = new byte[1024];
	
	private static int last;
	
	private static int dataNum;
	private static int toReceiveNum;
	
	static int in_port;
	static int out_port;
	
	static byte[] received_data = new byte[1024];
	static String save_path;
	File file = null;
	
	public RDTFileReceiver (int listen_port,int send_port,String path)
	{
		last = -1;
		
		in_port = listen_port;
		out_port = send_port;
		dataNum = conf.RDT_DATA_NUM;
		toReceiveNum = dataNum;
		save_path = path;
		
		System.out.println ("启动监听端口：" + listen_port);
		
		file = new File(save_path);
		
		if (!file.exists ())
		{
			try
			{
				file.createNewFile ();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		
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
				
			} catch (IOException e)
			{
			
			}
			
			int receiveSeq = -1;
			if (receive[1] == 'x')
			{
				receiveSeq = receive[0] - '0';
				received_data[receiveSeq] = receive[3];
				System.out.println (receive[3]);
			}
			else if (receive[2] == 'x')
			{
				receiveSeq = (receive[0] - '0') * 10 + (receive[1] - '0');
				received_data[receiveSeq] = receive[4];
			}
			else if (receive[0] == 'E' && receive[1] =='O' && receive[2] == 'F')
			{
				FileOutputStream fos = null;
				if (file.exists ())
				{
					file.delete ();
					try
					{
						file.createNewFile ();
					} catch (IOException e)
					{
						e.printStackTrace ();
					}
				}
				try {
					fos = new FileOutputStream(file);
					fos.write(received_data);
					fos.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			//指定丢包概率,取值范围是[0.0,1.0)的左闭右开区间.
			//有0.2的概率跳过发送ACK的环节，从而产生丢包。
			if (Math.random () < 1)
			{
				if (receiveSeq == last + 1)
				{
					System.out.println ("端口" + in_port + "成功接收序列为" + receiveSeq + "的数据包.");
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
					System.out.println ("向端口"+out_port+"发送序列为" + receiveSeq + "的ACK.");
				} else if (receiveSeq < (last + 1))
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
				} else if (last != -1 && toReceiveNum > 0)
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
					System.out.println ("向端口"+out_port+"发送序列为" + last + "的ACK");
				}
			}
		}
		
	}
}
