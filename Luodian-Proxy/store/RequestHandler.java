import sun.jvm.hotspot.opto.Block;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;


/**
 * RequestHandler processes each client's request through proxy server,
 * then sends to remote server and write back to client.
 *
 * @author Luodian
 */
public class RequestHandler implements Runnable
{
	public static String cacheFilePath = "./Cache.txt";
	public static ArrayList<String> cache_list;
	public static HashMap<String,String> pair_cache_list;
	protected boolean has_cache_no_timestamp = false;
	protected int cache_url_index = -1;
	protected String[] Blocked_lsit = new String[] {"www.sina.com.cn"};
	protected String[] Phishing_list = new String[] {"http://jwts.hit.edu.cn/"};
	protected String[] Blocked_addresses = new String[] {"172.20.18.116"};
	protected Boolean is_fishing = false;
	protected Boolean has_cache_already = false;
	protected String local_address;
	protected String first_line;
	protected Boolean is_refused;
	
	OutputStream outputStream_client; //输出流用来将数据发送到浏览器
	PrintWriter outPrintWriter_client; //writer用来向浏览器写入数据
	InputStream inputStream_Web; //输入流用来读取从网站发回的响应
	PrintWriter outPrintWriter_Web; //writer用来向网站发送请求
	BufferedReader bufferedReader_web; //缓冲用来缓存想网站发送的请求
	FileInputStream fileInputStream;
	/**
	 * Client input stream for reading request
	 */
	protected DataInputStream clientInputStream;
	/**
	 * Client output stream for rendering response
	 */
	protected OutputStream clientOutputStream;
	
	/**
	 * Remote output stream to send in client's request
	 */
	protected OutputStream remoteOutputStream;
	
	/**
	 * Remote input stream to read back response to client
	 */
	protected InputStream remoteInputStream;
	
	/**
	 * Client socket object
	 */
	protected Socket clientSocket;
	
	/**
	 * Remote socket object
	 */
	protected Socket remoteSocket;
	
	/**
	 * Client request type (Only "GET" or "POST" are handled)
	 */
	protected String requestType;
	
	/**
	 * Client request url (e.g. http://www.google.com)
	 */
	protected String url;
	
	/**
	 * Client request uri parsed from url (e.g. /index.html)
	 */
	protected String uri;
	
	/**
	 * Client request version (e.g. HTTP/1.1)
	 */
	protected String httpVersion;
	
	/**
	 * Data structure to hold all client request handers (e.g. proxy-connection: keep-alive)
	 */
	protected HashMap<String, String> header;
	
	/**
	 * End of line character
	 */
	static String endOfLine = "\r\n";
	
	/**
	 * Create a RequestHandler instance with clientSocket object
	 *
	 * @param clientSocket Client socket object
	 */
	public RequestHandler (Socket clientSocket) throws FileNotFoundException
	{
		header = new HashMap<String, String> ();
		this.clientSocket = clientSocket;
		
		fileInputStream = new FileInputStream (cacheFilePath);
		cache_list = readCache (fileInputStream);
		
		System.out.println ("****Read " + cache_list.size () + " lines of cache*******");
	}
	
	/**
	 * When instance is created, open client/remote streams then
	 * proceed with the following 3 tasks:<br>
	 * <p>
	 * 1) get request from client<br>
	 * 2) forward request to remote host<br>
	 * 3) read response from remote back to client<br>
	 * <p>
	 * Close client/remote streams when finished.<br>
	 *
	 * @see Runnable#run()
	 */
	public void run ()
	{
		try
		{
			is_refused = false;
			clientInputStream = new DataInputStream (clientSocket.getInputStream ());
			clientOutputStream = clientSocket.getOutputStream ();
			
			InetAddress addr = clientSocket.getInetAddress ();
			local_address = addr.getHostAddress ();
			
			//block users
			for (String item : Blocked_addresses)
			{
				if (item.equals (local_address))
				{
					System.out.println ("IP: " + local_address + " is blocked.");
					return;
					//exit run()
				}
			}
			
			if (is_fishing)
			{
				System.out.println ("Is Processing Fishing.");
				return;
			}
			
			if (has_cache_already)
			{
				System.out.println ("Is Processing cache strategy.");
				return;
			}
			
			
			//get request from client
			clientToProxy ();
			
			if (!is_refused)
			{
				for (String iter : cache_list)
				{
					if (iter.equals (first_line))
					{
						has_cache_already = true;
						break;
					}
				}
				
				if (!has_cache_already)
				{
					String temp = first_line + "\r\n";
					write_cache (temp.getBytes (), 0, temp.length ());
					first_line = "";
				}
				
				//如果没有cache
				if (cache_list.size () == 0)
				{
					//forward request to remote host
					proxyToRemote ();
					
					//read response from remote back to client
					remoteToClient ();
				}
				else
				{
					//缓存文件不为空，寻找之前有没有缓存过该url对应的请求
					String modifyTime;
					String info = "";
					modifyTime = findModifyTime (cache_list, first_line);//提取modifytime
					System.out.println ("Modified Time is " + modifyTime);
					if (modifyTime != null || has_cache_no_timestamp)
					{
						//cache中没有If-Modified字段
						if (!has_cache_no_timestamp)
						{
							first_line += "\r\n";
							outPrintWriter_Web.write (first_line);
							System.out.print ("向服务器发送确认修改时间请求:\n" + first_line);
							String str1 = "Host: " + header.get ("host") + "\r\n";
							outPrintWriter_Web.write (str1);
							String str = "If-modified-since: " + modifyTime
									+ "\r\n";
							outPrintWriter_Web.write (str);
							outPrintWriter_Web.write ("\r\n");
							outPrintWriter_Web.flush ();
							System.out.print (str1);
							System.out.print (str);
							
							info = bufferedReader_web.readLine ();
							System.out.println ("服务器发回的信息是：" + info);
						}
						//如果服务器给回的响应是304 Not Modified，就将缓存的数据直接发送给浏览器
						if (info.contains ("Not Modified") || has_cache_no_timestamp)
						{//如果服务器给回的响应是304 Not Modified，就将缓存的数据直接发送给浏览器
							int contentindex = 0;
							String temp_response = "";
							System.out.println ("使用缓存数据");
							if (cache_url_index != -1)
								for (int i = cache_url_index + 1; i < cache_list.size (); i++)
								{
									if (cache_list.get (i).contains ("http://"))
										break;
									temp_response += cache_list.get (i);
									temp_response += "\r\n";
									
								}
							System.out.println ("使用缓存：\n" + temp_response);
							outputStream_client.write (temp_response.getBytes (), 0, temp_response.getBytes ().length);
							outputStream_client.write ("\r\n".getBytes (), 0, "\r\n".getBytes ().length);
							outputStream_client.flush ();
						}
						else
						{
							/** 服务器返回的不是304 Not Modified的话，就将服务器的响应直接转发到浏览器并记录缓存就好了 */
							System.out.println ("有更新，使用新的数据");
							transmitResponseToClient ();
						}
					}
					else
					{
						//forward request to remote host
						proxyToRemote ();
						
						//read response from remote back to client
						remoteToClient ();
					}
				}
			}
			
			if (is_fishing)
			{
				is_fishing = false;
			}
			if (has_cache_already)
			{
				has_cache_already = false;
			}
			if (is_refused)
			{
				is_refused = false;
			}
			
			if (remoteOutputStream != null) remoteOutputStream.close ();
			if (remoteInputStream != null) remoteInputStream.close ();
			if (remoteSocket != null) remoteSocket.close ();
			
			if (clientOutputStream != null) clientOutputStream.close ();
			if (clientInputStream != null) clientInputStream.close ();
			if (clientSocket != null) clientSocket.close ();
		} catch (IOException e)
		{
		
		}
	}
	
	/**
	 * Receive and pre-process client's request headers before redirecting to remote server
	 */
	private void clientToProxy ()
	{
		
		String line, key, value;
		StringTokenizer tokens;
		try
		{
			// HTTP Command
			if ((line = clientInputStream.readLine ()) != null)
			{
				first_line = line;
				System.out.println (line);
				
				tokens = new StringTokenizer (line);
				requestType = tokens.nextToken ();
				url = tokens.nextToken ();
				
				// https filtered
				if (line.contains ("CONNECT") || line.contains ("google"))
				{
					System.out.println ("Request " + line + " has been filtered.");
					is_refused = true;
					return;
					//exit run()
				}
				
				// block site
				for (String item : Blocked_lsit)
				{
					if (url.contains (item))
					{
						System.out.println (item + " blocked!");
						return;
					}
				}
				
				// phishing some sites
				for (String item : Phishing_list)
				{
					if (url.equals (item))
					{
						System.out.println ("Redirecting to " + "www.qq.com");
						url = "http://www.qq.com.cn/";
						is_fishing = true;
						// http://www.qq.com/
						
					}
				}
				httpVersion = tokens.nextToken ();
			}
			
			// Header Info
			while ((line = clientInputStream.readLine ()) != null && is_fishing == false)
			{
				// check for empty line
				if (line.trim ().length () == 0) break;
				
				// tokenize every header as key and value pair
				tokens = new StringTokenizer (line);
				key = tokens.nextToken (":");
				value = line.replaceAll (key, "").replace (": ", "");
				header.put (key.toLowerCase (), value);
			}
			
			if (is_fishing)
			{
				header.put ("accept-language", "zh-CN,zh;q=0.9,zh-TW;q=0.8");
				header.put ("host", "www.qq.com.cn");
				header.put ("upgrade-insecure-requests", "1");
				header.put ("cache-control", "max-age=0");
				header.put ("accept-encoding", "gzip, deflate");
				header.put ("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
//				header.put("cookie","bdshare_firstime=1500944937169; _ga=GA1.3.426459933.1504267704; lastVisit=2018%C4%EA%B4%BA%BC%BE%D1%A7%C6%DA%C3%C0%B9%FA%BC%D3%D6%DD%B4%F3%D1%A7%B2%AE%BF%CB%C0%FB%B7%D6%D0%A3%B1%BE%BF%C6%C9%FA%B7%C3%D1%A7%CF%EE%C4%BF%CD%A8%D6%AA%7C%2Fnews%2F2017%2F07%2D03%2F954653170RL0%2Ehtm%7C2017%2D9%2D20+12%3A12%3A22%2C2018%C4%EA%B4%BA%BC%BE%D1%A7%C6%DA%C3%C0%B9%FA%B1%F6%CF%A6%B7%A8%C4%E1%D1%C7%B4%F3%D1%A7%B1%BE%BF%C6%C9%FA%B7%C3%D1%A7%CF%EE%C4%BF%CD%A8%D6%AA%7C%2Fnews%2F2017%2F07%2D03%2F7984233170RL0%2Ehtm%7C2017%2D9%2D15+17%3A25%3A15%2C2018%C4%EA%B4%BA%BC%BE%D1%A7%C6%DA%C3%C0%B9%FA%C6%D5%B6%C9%B4%F3%D1%A7%B1%BE%BF%C6%C9%FA%BD%BB%BB%BB%B7%C3%D1%A7%CF%EE%C4%BF%CD%A8%D6%AA%7C%2Fnews%2F2017%2F07%2D03%2F5173853170RL0%2Ehtm%7C2017%2D9%2D1+15%3A08%3A10%2C2017%C4%EA%CA%EE%C6%DA%C3%C0%B9%FA%BC%D3%D6%DD%B4%F3%D1%A7%C2%E5%C9%BC%ED%B6%B7%D6%D0%A3%CF%EE%C4%BF%D0%FB%BD%B2%7C%2Fnews%2F2017%2F03%2D16%2F8322805130RL0%2Ehtm%7C2017%2D9%2D1+15%3A07%3A03%2C2017%C4%EA%CA%EE%C6%DA%BC%D3%D6%DD%B4%F3%D1%A7%CA%A5%B5%D8%D1%C7%B8%E7%B7%D6%D0%A3%B4%B4%D0%C2%B4%B4%D2%B5%CF%EE%C4%BF%CD%A8%D6%AA%7C%2Fnews%2F2017%2F03%2D14%2F1462021130RL0%2Ehtm%7C2017%2D9%2D1+15%3A06%3A49%2C2017%C4%EA%CA%EE%BC%D9%BD%A3%C7%C5%D1%A7%CA%F5%B7%A2%D5%B9%BF%CE%B3%CC%B1%A8%C3%FB%B5%C4%CD%A8%D6%AA%7C%2Fnews%2F2017%2F05%2D03%2F2625501150RL0%2Ehtm%7C2017%2D9%2D1+15%3A06%3A17%2C2017%C4%EA%CA%EE%C6%DA%C5%A6%D4%BC%B4%F3%D1%A7%B1%BE%BF%C6%C9%FA%BD%BB%C1%F7%CF%EE%C4%BF%CD%A8%D6%AA%7C%2Fnews%2F2017%2F04%2D18%2F2471450140RL0%2Ehtm%7C2017%2D9%2D1+15%3A06%3A01%2C%B9%D8%D3%DA2017%C4%EA%CF%C4%BC%BE%D1%A7%C6%DA%D0%C2%BC%D3%C6%C2%C4%CF%D1%F3%C0%ED%B9%A4%B4%F3%D1%A7%BD%BB%C1%F7%CF%EE%C4%BF%C9%EA%B1%A8%B5%C4%CD%A8%D6%AA%7C%2Fnews%2F2017%2F04%2D05%2F6332734140RL0%2Ehtm%7C2017%2D9%2D1+15%3A05%3A00%2C%B9%D8%D3%DA2017%C4%EA%B5%DA%B6%FE%C5%FA%B4%CECSC%D3%C5%D0%E3%B1%BE%BF%C6%C9%FA%B9%FA%BC%CA%BD%BB%C1%F7%CF%EE%C4%BF%C9%EA%B1%A8%B5%C4%CD%A8%D6%AA%7C%2Fnews%2F2017%2F07%2D11%2F0681014170RL0%2Ehtm%7C2017%2D9%2D1+15%3A04%3A42%2C2017%C4%EA%B5%DA%B6%FE%C5%FA%B4%CECSC%D3%C5%D0%E3%B1%BE%BF%C6%C9%FA%B9%FA%BC%CA%BD%BB%C1%F7%CF%EE%C4%BF%D1%A1%B0%CE%BD%E1%B9%FB%B9%AB%CA%BE%7C%2Fnews%2F2017%2F07%2D28%2F3965056170RL0%2Ehtm%7C2017%2D9%2D1+15%3A04%3A36%2C%B9%D8%D3%DA2017%C4%EA%B5%DA%D2%BB%C5%FA%B4%CECSC%D3%C5%D0%E3%B1%BE%BF%C6%C9%FA%B9%FA%BC%CA%BD%BB%C1%F7%CF%EE%C4%BF%B3%F5%D1%A1%BD%E1%B9%FB%BC%B0%B2%B9%B1%A8%B5%C4%CD%A8%D6%AA%7C%2Fnews%2F2017%2F03%2D17%2F0605402130RL0%2Ehtm%7C2017%2D9%2D1+15%3A04%3A26%2C2017%B0%CD%C0%E8%B8%DF%BF%C64%2B2%CF%EE%C4%BF%D5%D0%C9%FA%CD%A8%D6%AA%7C%2Fnews%2F2017%2F07%2D17%2F2241105170RL0%2Ehtm%7C2017%2D8%2D31+0%3A28%3A35%2C2018%B4%BA%BC%BE%D1%A7%C6%DA%C3%C0%B9%FA%CD%FE%CB%B9%BF%B5%D0%C7%B4%F3%D1%A7%C2%F3%B5%CF%D1%B7%B7%D6%D0%A3%B1%BE%BF%C6%C9%FA%B7%C3%D1%A7%CF%EE%C4%BF%CD%A8%D6%AA%7C%2Fnews%2F2017%2F07%2D03%2F5472153170RL0%2Ehtm%7C2017%2D8%2D31+0%3A28%3A16%2C%CF%E3%B8%DB%B4%F3%D1%A7%C9%FA%BA%DA%C1%FA%BD%AD%D3%CE%D1%A7%CD%C5%D2%BB%D0%D0%B7%C3%CE%CA%CE%D2%CE%D2%D0%A3%7C%2Fnews%2F2017%2F08%2D28%2F9663507180RL0%2Ehtm%7C2017%2D8%2D30+22%3A25%3A25%2C%BE%FC%D1%B5%BD%F8%D0%D0%CA%B1%A3%BA%CC%E5%D1%E9%CA%B6%CD%BC%D3%C3%F7%C8%C1%A6%A3%AC%CA%EC%CF%A4%D0%A3%D4%B0%C3%BF%D2%BB%BD%C7%C2%E4%7C%2Fnews%2F2017%2F08%2D30%2F1843031280RL0%2Ehtm%7C2017%2D8%2D30+22%3A25%3A00%2C2018%C4%EA%B4%BA%BC%BE%D1%A7%C6%DA%C3%C0%B9%FA%B8%E7%C2%D7%B1%C8%D1%C7%B4%F3%D1%A7%B1%BE%BF%C6%C9%FA%B7%C3%D1%A7%CF%EE%C4%BF%CD%A8%D6%AA%7C%2Fnews%2F2017%2F07%2D03%2F0613043170RL0%2Ehtm%7C2017%2D8%2D30+22%3A20%3A15%2C2017%C4%EA%CF%C4%BC%BE%D1%A7%C6%DA%B8%B0%CE%F7%B0%B2%BD%BB%CD%A8%B4%F3%D1%A7%BD%BB%C1%F7%D1%A7%CF%B0%CF%EE%C4%BF%D1%A1%B0%CE%BD%E1%B9%FB%B9%AB%CA%BE%7C%2Fnews%2F2017%2F06%2D07%2F0991449060RL0%2Ehtm%7C2017%2D8%2D30+22%3A19%3A40%2C2017%C4%EA%CF%C4%BC%BE%D1%A7%C6%DA%B8%B0%C4%CF%BE%A9%B4%F3%D1%A7%BD%BB%C1%F7%D1%A7%CF%B0%CF%EE%C4%BF%D1%A1%B0%CE%BD%E1%B9%FB%B9%AB%CA%BE%7C%2Fnews%2F2017%2F06%2D19%2F9680339160RL0%2Ehtm%7C2017%2D8%2D30+22%3A18%3A23%2C%BC%C6%CB%E3%BB%FA%D1%A7%D4%BA%BE%D9%D0%D02017%C4%EA%D1%D0%BE%BF%C9%FA%CA%EE%C6%DA%C9%E7%BB%E1%CA%B5%BC%F9%BB%EE%B6%AF%7C%2Fnews%2F2017%2F07%2D24%2F315417170RL0%2Ehtm%7C2017%2D7%2D25+9%3A08%3A31%2C; UM_distinctid=1624d9ec41aa8-015a6d34bd93c-33607c05-fa000-1624d9ec41b2cf; __utmz=161430584.1522117075.22.3.utmccn=(referral)|utmcsr=google.com.hk|utmcct=/|utmcmd=referral; ASPSESSIONIDCASSTDDQ=IIFCKPLDGHDCIIOGPAAOGDFK; __utmc=161430584; __utma=161430584.1293650820.1500944937.1524121037.1524145860.25; ASPSESSIONIDQQTTATBC=IJBMKFABHFFAKMDDICPGIJPM");
//				header.put("If-None-Match","baa5cd53cded31:27138");
//				header.put("If-Modified-Since","Fri, 27 Apr 2018 15:31:37 GMT");
			}
			stripUnwantedHeaders ();getUri ();
		} catch (UnknownHostException e)
		{
			return;
		} catch (SocketException e)
		{
			return;
		} catch (IOException e)
		{
			return;
		}
	}
	
	/**
	 * Sending pre-processed client request to remote server
	 */
	private void proxyToRemote ()
	{
		
		try
		{
			if (header.get ("host") == null) return;
			if (!requestType.startsWith ("GET") && !requestType.startsWith ("POST"))
				return;
			
			remoteSocket = new Socket (header.get ("host"), 80);
			remoteOutputStream = remoteSocket.getOutputStream ();
			
			// make sure streams are still open
			checkRemoteStreams ();
			checkClientStreams ();
			
			// make request from client to remote server
			String request = requestType + " " + uri + " HTTP/1.0";
			remoteOutputStream.write (request.getBytes ());
			remoteOutputStream.write (endOfLine.getBytes ());
//			System.out.println(request);
			
			// send hostname
			String command = "host: " + header.get ("host");
			remoteOutputStream.write (command.getBytes ());
			remoteOutputStream.write (endOfLine.getBytes ());
//			System.out.println(command);
			
			// send rest of the headers
			for (String key : header.keySet ())
			{
				if (!key.equals ("host"))
				{
					command = key + ": " + header.get (key);
					remoteOutputStream.write (command.getBytes ());
					remoteOutputStream.write (endOfLine.getBytes ());
//					System.out.println(command);
				}
			}
			
			remoteOutputStream.write (endOfLine.getBytes ());
			remoteOutputStream.flush ();
			
			// send client request data if its a POST request
			if (requestType.startsWith ("POST"))
			{
				int contentLength = Integer.parseInt (header.get ("content-length"));
				for (int i = 0; i < contentLength; i++)
				{
					remoteOutputStream.write (clientInputStream.read ());
				}
			}
			
			// complete remote server request
			remoteOutputStream.write (endOfLine.getBytes ());
			remoteOutputStream.flush ();
		} catch (UnknownHostException e)
		{
			return;
		} catch (SocketException e)
		{
			return;
		} catch (IOException e)
		{
			return;
		}
	}
	
	/**
	 * Sending buffered remote server response back to client with minor header processing
	 */
	@SuppressWarnings("deprecation")
	private void remoteToClient ()
	{
		try
		{
			// If socket is closed, return
			if (remoteSocket == null) return;
			
			String line;
			DataInputStream remoteOutHeader = new DataInputStream (remoteSocket.getInputStream ());
			ArrayList<String> header = new ArrayList<> ();
			// get remote response header
			while ((line = remoteOutHeader.readLine ()) != null)
			{
				// check for end of header blank line
				if (line.trim ().length () == 0) break;

//				System.out.println ("################");
//				System.out.println (line);
				// check for proxy-connection: keep-alive
			if (line.toLowerCase ().startsWith ("proxy")) continue;
				if (line.contains ("keep-alive")) continue;
				
				// write remote response to client
//				System.out.println(line);
				clientOutputStream.write (line.getBytes ());
				clientOutputStream.write (endOfLine.getBytes ());
				header.add (line);
			}
			
			// complete remote header response
			clientOutputStream.write (endOfLine.getBytes ());
			clientOutputStream.flush ();
			
			
			// get remote response body
			remoteInputStream = remoteSocket.getInputStream ();
			byte[] buffer = new byte[2048];
			// buffer remote response then write it back to client
			
			// write cache
			for (int i; (i = remoteInputStream.read (buffer)) != -1; )
			{
				clientOutputStream.write (buffer, 0, i);
				
				String show_response = new String (buffer, 0, buffer.length);
				
//				System.out.println ("Server's response is: " + show_response);
				
				write_cache (buffer, 0, i);
				write_cache ("\r\n".getBytes (), 0, 2);
				
				clientOutputStream.flush ();
			}
			
			
			String date = null;
			String location = null;
			for (String item:header)
			{
				if (item.contains ("Last-Modified"))
				{
					date = item.substring (15);
				}
				if (item.contains ("Content-Location"))
				{
					location = item.substring (18);
				}
			}
			
		} catch (UnknownHostException e)
		{
			return;
		} catch (SocketException e)
		{
			return;
		} catch (IOException e)
		{
			return;
		}
	}
	
	/**
	 * Helper function to strip out unwanted request header from client
	 */
	private void stripUnwantedHeaders ()
	{
		
		if (header.containsKey ("user-agent")) header.remove ("user-agent");
		if (header.containsKey ("referer")) header.remove ("referer");
		if (header.containsKey ("proxy-connection")) header.remove ("proxy-connection");
		if (header.containsKey ("connection") && header.get ("connection").equalsIgnoreCase ("keep-alive"))
		{
			header.remove ("connection");
		}
	}
	
	/**
	 * Helper function to check for client input and output stream, reconnect if closed
	 */
	private void checkClientStreams ()
	{
		
		try
		{
			if (clientSocket.isOutputShutdown ()) clientOutputStream = clientSocket.getOutputStream ();
			if (clientSocket.isInputShutdown ())
				clientInputStream = new DataInputStream (clientSocket.getInputStream ());
		} catch (UnknownHostException e)
		{
			return;
		} catch (SocketException e)
		{
			return;
		} catch (IOException e)
		{
			return;
		}
	}
	
	/**
	 * Helper function to check for remote input and output stream, reconnect if closed
	 */
	private void checkRemoteStreams ()
	{
		
		try
		{
			if (remoteSocket.isOutputShutdown ()) remoteOutputStream = remoteSocket.getOutputStream ();
			if (remoteSocket.isInputShutdown ())
				remoteInputStream = new DataInputStream (remoteSocket.getInputStream ());
		} catch (UnknownHostException e)
		{
			return;
		} catch (SocketException e)
		{
			return;
		} catch (IOException e)
		{
			return;
		}
	}
	
	/**
	 * Helper function to parse URI from full URL
	 */
	private void getUri ()
	{
		
		if (header.containsKey ("host"))
		{
			int temp = url.indexOf (header.get ("host"));
			temp += header.get ("host").length ();
			
			if (temp < 0)
			{
				// prevent index out of bound, use entire url instead
				uri = url;
			} else
			{
				// get uri from part of the url
				uri = url.substring (temp);
			}
		}
	}
	
	/**
	 * 从文件中读取缓存内容，按行读取
	 *
	 * @param fileInputStream
	 *
	 * @return
	 */
	private ArrayList<String> readCache (FileInputStream fileInputStream)
	{
		ArrayList<String> result = new ArrayList<> ();
		String temp;
		BufferedReader br = new BufferedReader (new InputStreamReader (fileInputStream));
		try
		{
			while ((temp = br.readLine ()) != null)
			{
				result.add (temp);
			}
			
		} catch (IOException e)
		{
			e.printStackTrace ();
		}
		return result;
	}
	
	private void write_cache (int c) throws IOException
	{
		ProxyServer.fileOutputStream.write ((char) c);
	}
	
	private void write_cache (byte[] bytes, int offset, int len)
			throws IOException
	{
		for (int i = 0; i < len; i++)
			write_cache ((int) bytes[offset + i]);
	}
	
	/**
	 * 提取modifytime
	 *
	 * @param cache_temp
	 * @param request
	 *
	 * @return
	 */
	private String findModifyTime (ArrayList<String> cache_temp, String request)
	{
		String LastModifiTime = null;
		int startSearching = 0;
		
//		System.out.println ("Header to find modified time is: " + request);
		for (int i = 0; i < cache_temp.size (); i++)
		{
			if (cache_temp.get (i).equals (request))
			{
				startSearching = i;
				cache_url_index = i;
				for (int j = startSearching + 1; j < cache_temp.size (); j++)
				{
					if (cache_temp.get (j).contains ("http://"))
						break;
					if (cache_temp.get (j).contains ("Last-Modified:"))
					{
						LastModifiTime = cacheFilePath.substring (cache_temp.get (j).indexOf ("Last-Modified:"));
						return LastModifiTime;
					}
					if (cache_temp.get (j).contains ("<html>"))
					{
						has_cache_no_timestamp = true;
						return LastModifiTime;
					}
				}
			}
		}
		return LastModifiTime;
	}
	
	/**
	 * 这个函数做三件事：从网站接收响应，发送给浏览器，并将响应写入缓存
	 *
	 * @throws IOException
	 */
	private void transmitResponseToClient () throws IOException
	{
		
		byte[] bytes = new byte[2048];
		int length = 0;
		
		while (true)
		{
			if ((length = inputStream_Web.read (bytes)) > 0)
			{
				outputStream_client.write (bytes, 0, length);
				String show_response = new String (bytes, 0, bytes.length);
				System.out.println ("服务器发回的消息是:\n---\n" + show_response + "\n---");
				write_cache (bytes, 0, length);
				write_cache ("\r\n".getBytes (), 0, 2);
				continue;
			}
			break;
		}
		
		outPrintWriter_client.write ("\r\n");
		outPrintWriter_client.flush ();
	}
	
}
