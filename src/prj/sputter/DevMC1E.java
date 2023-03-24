package prj.sputter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import narl.itrc.DevMC0Eth;
import narl.itrc.Misc;
import narl.itrc.TokenBook.Token;

/**
 * Implement MC protocol(1E frame).<p>
 * The MC protocol for the Ethernet adapter is a subset of A compatible 1E frames.<p>
 * '1E frame' may mean 'the first generate Ethernet communication'.<p>
 * MELSOFT(GX Developer else...) or HMI maybe use this to connect PLC controller.<p>
 * This protocol is used by MITSUBISHI ,PLC controller(FC3U).<p>
 */
public class DevMC1E extends DevMC0Eth {
	
	public DevMC1E(final String tag) {
		super(tag);
	}
	public DevMC1E() {
		super("MC1E");
	}
	//--------------------------------------//

	private int monit = 10;//monitoring timer, default~~~
	@SuppressWarnings("unused")
	private String name = "";//model name
	
	@Override
	public void igniteHook() {
		//name = exec_get_name();
		//Misc.logv("[%s] %s on line!!", TAG, name);
		//testing!!!
		//exec_write("Y000",true,true,false,false);		
	}	
	@Override
	public Token parse(final Token tkn, final String txt) {
		if(txt.matches(REGX_TOKEN)==false) {
			return null;
		}
		MCPayload mp = null;
		switch(txt.charAt(0)) {
		case 'M':
		case 'X':
		case 'Y':
			mp = new MCPayload(0x00).init_device(txt);
			break;
		case 'D':
			mp = new MCPayload(0x01).init_device(txt);
			break;
		}
		if(mp==null) {
			return null;
		}
		tkn.user_data = mp;
		return tkn;
	}
	@Override
	public void pack2token(MCPack pk, Token tk) {
		MCPayload mp = (MCPayload)pk;
		tk.valInt = mp.d_vals;
	}
	//---------------------------------------
	
	private final static String REGX_TOKEN= "[XYMDSTCRtc]\\d{1,4}+([-~]\\d{1,4}+)?+";
		
	private class MCPayload implements MCPack {
		@Override
		public void sendPack(OutputStream ss) throws IOException {
			final byte[] head = gen_head();
			final byte[] ctxt = gen_ctxt();
			final byte[] vals = gen_vals();
			ss.write(Misc.chainBytes(head,ctxt,vals));
		}
		@Override
		public void recvPack(InputStream ss) throws IOException {
			if(get_complete(ss)==false) {
				return;
			}
			int count = 0;
			switch(s_code) {
			case 0x80+0x00://read in bits, four-bits as 1-value
				count = (d_size*4)/8;
				break;
			case 0x80+0x01://read in word, one-bits as 1-value
				count = (d_size*16)/8;
				break;
			case 0x80+0x15://get CPU name
				count = d_size = 2;//trick!!!
				break;
			default:
				//the other command only has same answer(complete code and sub-header)
				return;
			}
			if(d_vals==null) {
				d_vals = new int[d_size];
			}
			//prepare buffer~~~
			byte[] reply;
			if(USE_ASCII==true) {
				reply = new byte[count*2];
			}else {
				reply = new byte[count];
			}			
			ss.read(reply);				
			//write back to 『d_vals』~~~			
			for(int i=0; i<d_vals.length; i++) {
				if(USE_ASCII==true) {
					if(count<d_size) {
						//read in bits....
						d_vals[i] = Integer.valueOf(""+
							(char)(reply[i]),16
						);
					}else if(count>d_size) {
						//read in word....
						d_vals[i] = Integer.valueOf(""+
							(char)(reply[i*4+0])+
							(char)(reply[i*4+1])+
							(char)(reply[i*4+2])+
							(char)(reply[i*4+3]), 16
						);
					}else if(count==d_size) {
						d_vals[i] = Integer.valueOf(""+
							(char)(reply[i*2+0])+
							(char)(reply[i*2+1]), 16
						);
					}
				}else {
					if(count<d_size) {
						//read in bits....
						final int bb = (int)(reply[i/2]) & 0xFF;
						d_vals[i] = (i%2==0)?( bb&0xF0 ):( bb&0x0F );
					}else if(count>d_size) {
						//read in word....
						final int ll = (int)(reply[i*2+0]) & 0xFF;
						final int hh = (int)(reply[i*2+1]) & 0xFF;
						d_vals[i] = (hh<<8) | ll;//bit0 is first device value~~~
					}else if(count==d_size) {
						d_vals[i] = (int)(reply[i]) & 0xFF;
					}					
				}
			}
		}
		//------------//
		
		final int shead;
		final int pc_no;
		int s_code = 0;//same as sub-header, but Bit7 is'1'
		int c_code = 0;//complete or abnormal code
		
		MCPayload(
			final int subheader,
			final int pc_number
		){
			shead = subheader;
			pc_no = pc_number;
		}
		MCPayload(
			final int subheader
		){
			this(subheader,0xFF);
		}
		
		int d_code = -1;//device head(code)
		int d_numb = -1;//device name(number)
		int d_size = 0;//device points
		
		/* In MITSUBISHI PLC,
		 * 'device' means a node for memory-map.<p>
		 * 'point' means bit.<p>
		 * Bit device(1-point): X,Y,M,S,T(contact),C(contact) <p>
		 * Word device(16-point): t(value),c(value),D,R <p>
		 */
		MCPayload init_device(final String txt) {
			int[] val = parse_token(txt);
			d_code = val[0];
			d_numb = val[1];
			d_size = val[2];
			return this;
		}
		
		int[] d_vals = null;//Writing or Reading value

		MCPayload init_device(final String memo,final int... vals) {
			init_device(memo);
			if(d_code<=0) { return this; }
			d_vals = vals;
			d_size = vals.length;
			return this; 
		}
		MCPayload init_device(final String memo,final boolean... vals) {
			int[] _v = new int[vals.length];
			for(int i=0; i<vals.length; i++) {
				_v[i] = (vals[i]==true)?(1):(0);
			}
			return init_device(memo, _v); 
		}
		
		byte[] gen_head() {
			if(USE_ASCII==true) {
				return String.format(
					"%02X%02X%04X", 
					(shead&0xFF), 
					(pc_no&0xFF), 
					(monit&0xFFFF)
				).getBytes();
			}else {
				return new byte[] {
					Misc.maskInt0(shead),
					Misc.maskInt0(pc_no),
					Misc.maskInt0(monit),
					Misc.maskInt1(monit)
				};
			}
		}
		byte[] gen_ctxt() {
			if(d_size==0) {
				return null;
			}
			if(USE_ASCII==true) {
				return String.format(
					"%04X%08X%02X00", 
					(d_code&0xFFFF), 
					(d_numb&0xFFFF), 
					(d_size&0xFF)
				).getBytes();
			}else {
				return new byte[] {
					Misc.maskInt0(d_numb),
					Misc.maskInt1(d_numb),
					Misc.maskInt2(d_numb),
					Misc.maskInt3(d_numb),
					Misc.maskInt0(d_code),
					Misc.maskInt1(d_code),
					Misc.maskInt0(d_size),
					0
				};
			}
		}
		byte[] gen_vals() {
			if(d_vals==null) {
				return null;
			}
			byte[] buff = null;	
			switch(shead) {
			case 0x02://write in bit(02H)
				if(USE_ASCII==true) {
					for(int i=0; i<d_size; i++) {
						buff = Misc.chainBytes(
							buff,
							String.format("%1X", d_vals[i]).getBytes()
						);
					}
				}else {
					buff = new byte[(d_size*4)/8];
					for(int i=0; i<d_size; i+=2) {
						final int aa = (d_vals[i*2+0])&0x0F;
						final int bb = (d_vals[i*2+1])&0x0F;
						buff[i/2] = (byte)((aa<<4)|bb);
					}
				}
				break;
			case 0x03://write in word(03H)
				if(USE_ASCII==true) {
					for(int i=0; i<d_size; i++) {
						buff = Misc.chainBytes(
							buff,
							String.format("%04X", d_vals[i]).getBytes()
						);
					}
				}else {
					buff = new byte[d_size*2];
					for(int i=0; i<d_size; i++) {
						buff[i*2+0] = Misc.maskInt0(d_vals[i]);
						buff[i*2+1] = Misc.maskInt1(d_vals[i]);
					}
				}
				break;
			case 0x04://test in bit(random write??)
				break;
			default:
				return null;
			}
			return buff;
		}

		boolean get_complete(InputStream ss) throws IOException {
			byte[] buff;
			if(USE_ASCII==true) {
				buff = new byte[4];
				ss.read(buff);
				s_code = Integer.valueOf(""+(char)(buff[0])+(char)(buff[1]), 16);
				c_code = Integer.valueOf(""+(char)(buff[2])+(char)(buff[3]), 16);				
			}else {
				buff = new byte[2];
				ss.read(buff);
				s_code = Misc.byte2int(buff[0]);
				c_code = Misc.byte2int(buff[1]);
			}
			if(c_code==0) {
				return true;
			}
			if(USE_ASCII==true) {
				ss.read(buff);
				c_code = Integer.valueOf(""+(char)(buff[0])+(char)(buff[1]), 16);				
			}else {
				ss.read(buff);
				c_code = Misc.byte2int(buff[0]);
			}
			return false;
		}		
	};
	
	private int[] parse_token(final String txt) {

		if(txt.matches(REGX_TOKEN)==false) {
			Misc.logv("[%s] invalid text for device code and number", txt);
			return new int[] {-1,-1,0};
		}
		
		int d_code=-1, d_numb=-1, d_size=0;
		
		String[] addr = txt.substring(1).split("-");
		if(addr.length==1) {
			d_numb = Integer.parseInt(addr[0]);
			if(d_size==0) {
				d_size = 1;//override. be careful device values!!!
			}				
		}else {
			final int d_num2 = Integer.parseInt(addr[1]);
			d_numb = Integer.parseInt(addr[0]);				
			d_size = d_num2 - d_numb + 1;
		}
		
		switch(txt.charAt(0)) {
		case 'd':
		case 'D': 
			d_code = 0x4420;
			if(d_numb>=8000) { d_numb += 0x1F40; }
			break;
		case 'r':
		case 'R': d_code = 0x5220; break;
		case 't': d_code = 0x544E; break;//value(timer, 16-bit)
		case 'c': //value(counter, 16-bit)
			d_code = 0x434E;
			if(d_numb>=200) { d_numb += 0x00C8; }
			break;
		case 'T': d_code = 0x5453; break;//contact (timer)
		case 'C': //contact (counter)
			d_code = 0x4353;
			if(d_numb>=200) { d_numb += 0x00C8; }
			break;
		case 'X': d_code = 0x5820; break;
		case 'Y': d_code = 0x5920; break;
		case 'M': 
			d_code = 0x4D20;
			if(d_numb>=8000) { d_numb += 0x1F40; }
			break;
		case 'S': d_code = 0x5320; break;		
		}
		return new int[] {d_code, d_numb, d_size};
	}
	//--------------------------------------------------
	
	@Override
	public void write_bit(String name, boolean flag) {asyncBreakIn(()->{
			MCPayload mp = new MCPayload(0x02).init_device(name,flag);
			transmit(mp);
	});}
	
	public int execWrite(final String txt,final boolean... flg) {
		MCPayload mp = new MCPayload(0x02).init_device(txt,flg);
		transmit(mp);
		return mp.c_code;
	}
	
	public void toggleWrite(String name) {asyncBreakIn(()->{
		//TODO: under construct!!!!
		MCPayload mp = new MCPayload(0x02).init_device(name,true);
		transmit(mp);
		
		transmit(mp);
	});}
	
	public int execRun(final boolean flg) {
		MCPayload mp = new MCPayload((flg==true)?(0x13):(0x14));
		transmit(mp);
		return mp.c_code;	
	}
	
	@SuppressWarnings("unused")
	private String exec_get_name() {
		MCPayload mp = new MCPayload(0x15);
		transmit(mp);
		switch(mp.d_vals[0]) {
		case 0xF5: return "FX3S";
		case 0xF4: return "FX3G/FX3GC";
		case 0xF3: return "FX3U/FX3UC";
		}
		return String.format("%02XH", mp.d_vals[0]);
	}

	@SuppressWarnings("unused")
	private void exec_loopback() {
		final byte[] ctxt = {
			4,
			0x55, (byte)0xAA, 0x55, (byte)0xAA
		};
		
		Misc.loge("[%s][exec_loopback] %s", TAG);
	}
	//--------------------------------------------------

}
