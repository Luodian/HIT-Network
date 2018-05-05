import java.io.IOException;

public class send
{
	public static void main(String [] args) throws IOException, InterruptedException
	{
		GBNSend gbnSendThread = new GBNSend (conf.CLIENT_PORT,conf.SERVER_PORT,conf.GBN_DATA_NUM);
		gbnSendThread.start();
	}
}
