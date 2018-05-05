import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class GBNSend extends Thread
{
	
	private static int winSize;
	private static int seqNum;
	private static int begin = 0, end;
	private static int dataNum;
	private static int toSendNum;
	static int serverPort;
	private static Boolean connection = false;
	
	static Timer timer;
	
	private static InetAddress inetAddress;
	
	private static DatagramSocket clientSocket;
	private byte[] receive = new byte[1024];
	private byte[] send = new byte[1024];
	static int in_port;
	static int out_port;
	SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
	
	GBNSend (int listen_port, int send_port,int data_num) throws InterruptedException, IOException
	{
		
		try
		{
			inetAddress = InetAddress.getByName (conf.LOCAL_ADDRESS);
		} catch (UnknownHostException ignored)
		{
		
		}
		
		in_port = listen_port;
		out_port = send_port;
		dataNum = data_num;
		toSendNum = dataNum;
		seqNum = conf.SEQ_NUM;
		
		try
		{
			clientSocket = new DatagramSocket ();
			byte[] hand_shake = new byte[1024];
			hand_shake = ("Time").getBytes ();
			DatagramPacket hand_shake_packet = new DatagramPacket (hand_shake, hand_shake.length,inetAddress, out_port);
			clientSocket.send (hand_shake_packet);
			System.out.println ("向端口" + send_port + "发送握手信息，获取时间.");
			
		} catch (SocketException ignored)
		{
		
		} catch (IOException e)
		{
			e.printStackTrace ();
		}
		
		
//		for (int i = 0; i < 25; ++i)
//		{
//			sleep (100);
//			System.out.print ("*");
//		}
//		System.out.println ();
//
//		for (int i = 0; i < 25; ++i)
//		{
//			sleep (80);
//			System.out.print ("*");
//		}
//		System.out.println ();
		System.out.println ("即将向端口：" + out_port + "发送" + dataNum + "个数据包");
		System.out.println ("启动监听端口：" + in_port);
		DatagramPacket receivePacket = new DatagramPacket (receive, receive.length);
//		clientSocket.receive (receivePacket);
		
		System.out.println ("获取时间：" + df.format(new Date ()));
		timer = new Timer (3000, new GBNDelayActionListener (clientSocket, 0));
		timer.start ();
		
		send = (0 + "x " + "seq").getBytes ();
		DatagramPacket sendPacket = new DatagramPacket (send, send.length, inetAddress, out_port);
		try
		{
			clientSocket.send (sendPacket);
			toSendNum--;
			System.out.println ("向端口" + out_port +"发送数据包：" + 0);
		} catch (IOException ignored)
		{
		
		}
	}
	
	@Override
	public void run ()
	{
		while (true)
		{
			//设定接受方报文为空
			DatagramPacket receivePacket = new DatagramPacket (receive, receive.length);
			try
			{
				clientSocket.receive (receivePacket);
				int ackNum = -1;
				
				if (receive[4] == 'x')
				{
					ackNum = receive[3] - '0';
				} else
				{
					ackNum = (receive[3] - '0') * 10 + (receive[4] - '0');
				}
				System.out.println ("接收到ACK序号：" + ackNum);
				
				if (ackNum == dataNum - 1)
				{
					System.out.println ("数据全部发送完毕!");
					timer.stop ();
					return;
				} else if (ackNum == begin && toSendNum > 0)
				{
					
					timer.stop ();
					begin++;
					end++;
					
//					if (end > seqNum - 1)
//					{
//						end = 0;
//					}
//					if (begin > seqNum - 1)
//					{
//						begin = 0;
//					}
					
					if (end < 10)
					{
						send = (end + "x " + "seq").getBytes ();
					} else if (end < 100)
					{
						send = (end + " " + "seq").getBytes ();
					}
					
					DatagramPacket sendPacket = new DatagramPacket (send, send.length, inetAddress, out_port);
					try
					{
						clientSocket.send (sendPacket);
						toSendNum--;
						//设置定时器
						timer = new Timer (3000, new GBNDelayActionListener (clientSocket, begin));
						timer.start ();
						
						System.out.println ("发送数据包：" + end);
					} catch (IOException ignored)
					{
					
					}
					
				}
				
			} catch (IOException ignored)
			{
			
			}
		}//end of while
		
	}//end of run
}

//为空中的数据包计时，作为Timer的Timertask
class GBNDelayActionListener implements ActionListener
{
	
	private DatagramSocket socket;
	private int seqNo;
	
	public GBNDelayActionListener (DatagramSocket clientSocket, int seqNo)
	{
		this.socket = clientSocket;
		this.seqNo = seqNo;
	}
	
	@Override
	public void actionPerformed (ActionEvent e)
	{
		
		GBNSend.timer.stop ();
		//重传时也要启动timer来计时
		GBNSend.timer = new Timer (3000, new GBNDelayActionListener (socket, seqNo));
		GBNSend.timer.start ();
		
		int end = seqNo + conf.GBN_WIN_SIZE - 1;
		if (end >= conf.GBN_DATA_NUM - 1)
		{
			end = conf.GBN_DATA_NUM - 1;
		}
		System.out.println ("准备重传数据 " + seqNo + "--" + end);
		
		for (int i = seqNo; i <= end && i <= conf.GBN_DATA_NUM - 1; i++)
		{
			byte[] sendData = null;
			InetAddress serverAddress = null;
			try
			{
				serverAddress = InetAddress.getByName (conf.LOCAL_ADDRESS);
				if (i < 10)
				{
					sendData = (i + "x " + "seq").getBytes ();
				} else if (i < 100)
				{
					sendData = (i + " " + "seq").getBytes ();
				}
				assert sendData != null;
				DatagramPacket sendPacket = new DatagramPacket (sendData, sendData.length, serverAddress, GBNSend.out_port);
				socket.send (sendPacket);
				System.out.println ("重新发送数据包 " + i);
			} catch (Exception e1)
			{
				e1.printStackTrace ();
			}
		}
	}
}
