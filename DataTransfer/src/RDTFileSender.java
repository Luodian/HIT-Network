import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;

public class RDTFileSender extends Thread
{
	private static int seqNum;
	private static int begin = 0, end;
	private static long dataNum;
	private static long toSendNum;
	private static String file_path;
	static int in_port;
	static int out_port;
	
	static Timer timer;
	
	private static InetAddress inetAddress;
	
	private static DatagramSocket clientSocket;
	private byte[] receive = new byte[1024];
	private byte[] send = new byte[1024];
	private byte[] content = new byte[1024];
	
	
	RDTFileSender (int listen_port, int send_port,String path,String inet_address) throws InterruptedException, IOException
	{
		try
		{
			inetAddress = InetAddress.getByName (conf.LOCAL_ADDRESS);
		} catch (UnknownHostException ignored)
		{
		
		}
		
		in_port = listen_port;
		out_port = send_port;
		file_path = path;
		
		BufferedInputStream in = new BufferedInputStream(new FileInputStream(file_path));
		ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
		
		System.out.println("Available bytes:" + in.available());
		
		byte[] temp = new byte[1024];
		int size = 0;
		while ((size = in.read(temp)) != -1) {
			out.write(temp, 0, size);
		}
		in.close();
		
		content = out.toByteArray();
		dataNum = content.length;
		toSendNum = dataNum;
		seqNum = conf.SEQ_NUM;
		System.out.println("Readed bytes count:" + content.length);
		
		try
		{
			clientSocket = new DatagramSocket ();
		} catch (SocketException ignored)
		{
		
		}
		
		System.out.println ("即将向端口：" + out_port + "发送" + dataNum + "个数据包");
		System.out.println ("启动监听端口：" + in_port);

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
		
		timer = new Timer (3000, new RDTFileDelayActionListener (clientSocket, 0));
		timer.start ();
		
		send = (0 + "x " + content[0] + "seq").getBytes ();
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
				System.out.println ("端口" + in_port + "接收到ACK序号：" + ackNum);
				
				//0-9全部接受完毕
				if (ackNum == dataNum - 1)
				{
					System.out.println ("数据全部发送完毕!");
					timer.stop ();
					byte[] sendData = null;
					sendData = ("EOF").getBytes ();
					DatagramPacket sendPacket = new DatagramPacket (sendData, sendData.length, inetAddress, out_port);
					try
					{
						clientSocket.send (sendPacket);
						
						System.out.println ("向端口" + out_port +"发送EOF数据包.");
					} catch (IOException ignored)
					{
					
					}
					return;
				}
				else if (ackNum == begin && toSendNum > 0)
				{
					timer.stop ();
					begin++;
					end++;
					//巡回
					if (end > seqNum - 1)
					{
						end = 0;
					}
					if (begin > seqNum - 1)
					{
						begin = 0;
					}
					
					if (end < 10)
					{
						
						String content_end = String.valueOf (content[end]);
						send = (end + "x " + content_end + "seq").getBytes ();
					} else if (end < 100)
					{
						String content_end = String.valueOf (content[end]);
						send = (end + " " + content_end + "seq").getBytes ();
					}
					
					DatagramPacket sendPacket = new DatagramPacket (send, send.length, inetAddress, out_port);
					try
					{
						clientSocket.send (sendPacket);
						toSendNum--;
						
						//设置定时器
						timer = new Timer (3000, new RDTFileDelayActionListener (clientSocket, begin));
						timer.start ();
						
						System.out.println ("向端口" + out_port +"发送数据包：" + end);
					} catch (IOException ignored)
					{
					
					}
					
				}
				
			} catch (IOException ignored)
			{
			
			}
		}//end of while
		
	}//end of run
	//为空中的数据包计时，作为Timer的Timertask
	class RDTFileDelayActionListener implements ActionListener
	{
		
		private DatagramSocket socket;
		private int seqNo;
		
		public RDTFileDelayActionListener (DatagramSocket clientSocket, int seqNo)
		{
			this.socket = clientSocket;
			this.seqNo = seqNo;
		}
		
		@Override
		public void actionPerformed (ActionEvent e)
		{
			
			RDTFileSender.timer.stop ();
			//重传时也要启动timer来计时
			RDTFileSender.timer = new Timer (3000, new RDTFileDelayActionListener (socket, seqNo));
			RDTFileSender.timer.start ();
			
			System.out.println ("准备向端口" + RDTSend.out_port +"重传数据 " + seqNo);
			
			byte[] sendData = null;
			InetAddress serverAddress = null;
			try
			{
				serverAddress = InetAddress.getByName (conf.LOCAL_ADDRESS);
				if (seqNo < 10)
				{
					sendData = (seqNo + "x " + content[seqNo]+ "seq").getBytes ();
				} else if (seqNo < 100)
				{
					sendData = (seqNo + " " + content[seqNo]+ "seq").getBytes ();
				}
				assert sendData != null;
				DatagramPacket sendPacket = new DatagramPacket (sendData, sendData.length, serverAddress, out_port);
				socket.send (sendPacket);
				System.out.println ("向端口" + out_port +"重传数据 " + seqNo);
			} catch (Exception e1)
			{
				e1.printStackTrace ();
			}
		}
		
	}
}



