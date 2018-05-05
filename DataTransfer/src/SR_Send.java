import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.*;


public class SR_Send extends Thread {
	
	private static int winSize;
	private static int seqNum ;
	private static int begin = 0, end;
	
	private static int[] getACK;
	
	private static int dataNum;
	private static int toSendNum;
	static int serverPort;
	static Timer[] timers;
	
	private static InetAddress inetAddress;
	
	private static DatagramSocket clientSocket;
	private byte[] receive = new byte[1024];
	private byte[] send = new byte[1024];
	
	
	public SR_Send(int port){
		
		try {
			inetAddress = InetAddress.getByName("localhost");
		}catch (UnknownHostException e){
		
		}
		
		serverPort = port;
		winSize = conf.SR_S_WIN_ZIZE;
		end = begin + winSize -1;
		dataNum = conf.SR_DATA_NUM;
		toSendNum = dataNum;
		seqNum = conf.SEQ_NUM;
		
		
		timers = new Timer[seqNum];
		getACK = new int[seqNum];
		
		for(int i = 0; i < getACK.length; i++){
			getACK[i] = 0;
		}
		
		try {
			clientSocket = new DatagramSocket();
		}catch (SocketException e){
		
		}
		
		System.out.println("Client即将发送" + dataNum +"个数据包");
		
		//首先发送窗格大小个数的数据包
		for (int i = begin; i <= end; i++){
			if(i < 10 ){
				send = (i + "x " + "seq").getBytes();
			}
			else if(i < 100){
				send = (i + " " + "seq").getBytes();
			}
			
			//UDP数据报文
			DatagramPacket sendPacket = new DatagramPacket(send,send.length,inetAddress, serverPort);
			
			try {
				clientSocket.send(sendPacket);
				toSendNum--;
				
				//设置定时器，设置时间为3秒
				timers[i] = new Timer(3000, new SRDelayActionListener(clientSocket,i,timers));
				timers[i].start();
				
				System.out.println("Client发送数据包："+i+"");
			}catch (IOException e){
			
			}
		}
	}
	
	public void run(){
		
		while (true){
			DatagramPacket receivePacket = new DatagramPacket(receive,receive.length);
			try{
				clientSocket.receive(receivePacket);
				int ackNum = -1;
				
				if(receive[4] == 'x'){
					ackNum = receive[3]-'0';
				}
				else {
					ackNum = (receive[3]-'0')*10 + (receive[4]-'0');
				}
				
				System.out.println("Client接收到ACK序号："+ackNum+"");
				
				//关闭定时器
				timers[ackNum].stop();
				
				if(ackNum > begin){
					
					getACK[ackNum] = 1;
				}
				else if(ackNum == begin && toSendNum > 0){
					
					int moveNum = 0;
					getACK[begin] = 1;
					
					//移动窗口
					for (int i = begin; i <= end; i++){
						
						if(getACK[i] == 1) {
							moveNum++;
							getACK[i] = 0;
							
							if(toSendNum >= 0){
								int next = i - begin + 1 + end;
								
								if (next < 10) {
									send = (next + "x " + "seq").getBytes();
								} else if (next < 100) {
									send = (next + " " + "seq").getBytes();
								}
								DatagramPacket sendPacket = new DatagramPacket(send, send.length, inetAddress, serverPort);
								try {
									clientSocket.send(sendPacket);
									toSendNum--;
									
									//设置定时器
									timers[next] = new Timer(3000, new SRDelayActionListener(clientSocket, next, timers));
									timers[next].start();
									
									System.out.println("Client发送数据包：" + next);
								} catch (IOException e) {
								}
							}
							else {
								System.out.println("发送完毕！");
								return;
							}
							
							
						}else {
							break;
						}
					}
					
					begin+= moveNum;
					end+= moveNum;
					
				}
				
			}catch (IOException ignored){
			
			}
		}//end of while
	}
	
}

//为空中的数据包计时，作为Timer的Timertask
class SRDelayActionListener implements ActionListener
{
	
	private DatagramSocket socket;
	private int seqNo;
	
	public SRDelayActionListener (DatagramSocket clientSocket, int seqNo, Timer[] timers)
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
		System.out.println ("Server准备重传数据 " + seqNo + "--" + end);
		
		for (int i = seqNo; i <= end; i++)
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
				System.out.println ("Server重新发送数据包 " + i);
			} catch (Exception e1)
			{
				e1.printStackTrace ();
			}
		}
	}
}
