import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class HttpProxy
{
	public static String cachePath = "";
	public static OutputStream writeCache;
	public static int TIMEOUT = 5000;//response time out upper bound
	public static int RETRIEVE = 5;//retry connection 5 times
	public static int CONNECT_PAUSE = 5000;//waiting for connection
	
	public static void main (String[] args) throws IOException
	{
		ServerSocket serverSocket;
		Socket currsoket = null;
		/** users need to setup work space */
		
		System.out.println ("==============请输入缓存的存储目录，输入 d 则设置为默认目录（程序同一目录下）=================");
		Scanner scanner = new Scanner (System.in);
		cachePath = scanner.nextLine ();
		if (cachePath.equals ("d"))
		{
			cachePath = "defaut_cache.txt";
		}
		/** 初始化缓存写对象 */
		writeCache = new FileOutputStream (cachePath, true);
		System.out.println ("=================================== 工作目录设置完毕====================================");
		
		try
		{
			//设置serversocket，绑定端口8888
			serverSocket = new ServerSocket (8888);
			int i = 0;
			//循环，持续监听从这个端口的所有请求
			while (true)
			{
				currsoket = serverSocket.accept ();
				//启动一个新的线程来处理这个请求
				i++;
//				System.out.println ("启动第" + i + "个线程");
				new MyProxy (currsoket);
			}
		} catch (IOException e)
		{
			if (currsoket != null)
			{
				currsoket.close ();//及时关闭这个socket
			}
			e.printStackTrace ();
		}
		writeCache.close ();//关闭文件输出流
	}
}