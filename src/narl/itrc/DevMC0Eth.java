package narl.itrc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.Socket;
import java.net.UnknownHostException;

import com.sun.glass.ui.Application;

import narl.itrc.TokenBook.Token;

/**
 * Support Ethernet frame for MC protocol.
 * Implement relative methods for MC protocol Ethernet frame.<>
 * @author qq
 *
 */
@SuppressWarnings("restriction")
public abstract class DevMC0Eth extends DevBase {

	public DevMC0Eth(String tag) {
		TAG = tag;
	}	
	public DevMC0Eth() {
		this("DevMC_E");
	}
	//--------------------------------------//
	
	@Override
	public void open() {
		final String path = Gawain.prop().getProperty(TAG, "");
		if(path.length()==0) {
			Misc.logw("[%s][open] no options",TAG);
			return;
		}
		open(path);
	}
	/**
	 * Connection argument format is below:
	 * [Ethernet address]:[port],[ascii/binary]
	 * @param arg
	 */
	public void open(final String path) {
		String[] arg = path.split(",");
		String[] inet = arg[0].split(":");
		USE_ASCII = true;//default option~~~
		if(arg.length>=2) {
			final String arg1 = arg[1].toLowerCase();
			if(arg1.startsWith("asc")==true) {
				USE_ASCII = true;
			}else if(arg1.startsWith("bin")==true) {
				USE_ASCII = false;
			}
		}
		if(inet.length!=2) {
			Misc.logw("[%s][open] invalid path - %s", TAG, path);
			return;
		}
		try {
			host = inet[0];
			port = Integer.parseInt(inet[1]);
			addState(STG_IGNITE,()->igniteRunner());
			addState(STG_LOOPER,()->looperRunner());
			playFlow(STG_IGNITE);
		}catch(NumberFormatException e) {
			Misc.loge("[%s][open] %s", TAG, e.getMessage());
		}
	}
	
	@Override
	public void close() {
		if(ss==null) { return; }
		try {
			ss.close();
			ss = null;
		} catch (IOException e) {
			Misc.loge("[%s][ignite] %s", TAG, e.getMessage());
		}
	}
	//--------------------------------------//
	
	protected final static String STG_IGNITE = "ignite";
	protected final static String STG_LOOPER = "looper";

	protected boolean USE_ASCII = true;//ASCII or binary code....	
	protected String host ="";
	protected int port = 5556;	
	protected Socket ss = null;
		
	public abstract void igniteHook();
	
	public abstract Token parse(final Token tkn, final String txt);
	public abstract void pack2token(final MCPack mp, final Token tk);
	public abstract void write_bit(String name,boolean flag);
	
	public final TokenBook node = new TokenBook() {
		@Override
		public Token parseToken(final Token tkn, final String txt) {
			return parse(tkn,txt);
		}
		@Override
		public void writeBit(String name, boolean flag) {
			write_bit(name,flag);
		}
	};
	
	void igniteRunner() {
		try {
			ss = new Socket(host,port);
			igniteHook();			
			nextState(STG_LOOPER);
		} catch (UnknownHostException e) {
			Misc.loge("[%s][ignite] %s", TAG, e.getMessage());
			nextState("");
		} catch (IOException e) {
			Misc.loge("[%s][ignite] %s", TAG, e.getMessage());
			nextState("");
		}		
	}
	
	public int looperEventDelay = 100;//unit is millisecond
	
	void looperRunner() {
		if(node.lst.size()==0) {
			block_sleep_sec(10);
			return;
		}
		for(Token tk:node.lst) {
			if(tk.user_data==null) {
				continue;
			}
			MCPack mp = (MCPack)tk.user_data;
			transmit(mp);			
			pack2token(mp,tk);
		}		
		Application.invokeLater(node.refresh_property_vent);
		block_sleep_msec(looperEventDelay);
	}	
	//--------------------------------------//
	
	protected interface MCPack {
		void sendPack(OutputStream ss) throws IOException;
		void recvPack(InputStream ss) throws IOException;
	};
	
	/**
	 * transmit message and receive response.<p>
	 * @return message response
	 */
	protected MCPack transmit(MCPack pack) {	
		try {
			if(ss==null) {
				return pack;
			}
			if(ss.isConnected()==false) {
				Misc.logw("[%s] socket is offline!!", TAG);
				return pack;
			}
			pack.sendPack(ss.getOutputStream());
			pack.recvPack(ss.getInputStream());
		} catch (IOException e) {
			Misc.loge("[%s][transmit] %s", TAG, e.getMessage());	
		}
		return pack;
	}
}
