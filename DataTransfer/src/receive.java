import java.io.IOException;

public class receive {
	
    public static void main(String[] args) throws InterruptedException, IOException
    {
	
		GBNReceive gbnReceiveThread = new GBNReceive(conf.SERVER_PORT,conf.CLIENT_PORT);
		gbnReceiveThread.start();
	
	    //参数为启动监听的端口
//	    RDTReceive rdtreceiveThread = new RDTReceive (conf.CLIENT_PORT,conf.SERVER_PORT);
//	    rdtreceiveThread.start ();
//
//	    RDTSend rdtsendThread = new RDTSend (conf.SERVER_PORT,conf.CLIENT_PORT,conf.GBN_DATA_NUM);
//	    rdtsendThread.start ();
	   
//	    client_side(conf.RDT_DATA_NUM);
//	    server_side (conf.RDT_DATA_NUM   * 2);
//	    RDTFileReceiver rdtreceiverThread = new RDTFileReceiver (conf.SERVER_PORT,conf.CLIENT_PORT,"/Users/luodian/Desktop/DataTransfer/src/b.txt");
//	    rdtreceiverThread.start ();
//
//	    RDTFileSender rdtsendThread = new RDTFileSender (conf.CLIENT_PORT,conf.SERVER_PORT,"/Users/luodian/Desktop/DataTransfer/src/test.txt",conf.LOCAL_ADDRESS);
//	    rdtsendThread.start ();

    }
    
    private static void client_side (int package_num) throws InterruptedException, IOException
    {
        GBNSend gbnsendThread = new GBNSend (conf.CLIENT_PORT,conf.SERVER_PORT,package_num);
	    GBNReceive gbnReceiveThread = new GBNReceive (conf.SERVER_PORT,conf.CLIENT_PORT);
	    
	    gbnReceiveThread.start ();
	    gbnsendThread.start ();
    }
	
	private static void server_side(int package_num) throws InterruptedException, IOException
	{
		GBNSend gbnsendThread = new GBNSend (conf.CLIENT_PORT + 1,conf.SERVER_PORT + 1,package_num);
		GBNReceive gbnReceiveThread = new GBNReceive (conf.SERVER_PORT + 1,conf.CLIENT_PORT + 1);
		gbnReceiveThread.start ();
		gbnsendThread.start ();
	}
}


