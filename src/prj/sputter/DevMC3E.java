package prj.sputter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import narl.itrc.DevMC0Eth;
import narl.itrc.Misc;
import narl.itrc.TokenBook.Token;

/**
 * Implement MC protocol(3E frame) for PLC FX5U, MELSEC iQ-F.<p>
 * It is also called SLMP(?).<p>
 * @author qq
 *
 */
public class DevMC3E extends DevMC0Eth {

	public DevMC3E(final String tag) {
		TAG = tag;
	}
	public DevMC3E() {
		super("MC3E");
	}
	//---------------------------------------
	
	@Override
	public void igniteHook() {
		//exec_read_bit("X*0000-0007");
		//exec_assign_bit("M*16=1");
		//block_sleep_msec(500);
		//exec_assign_bit("M*16=0");
		return;
	}
	@Override
	public void write_bit(String name, boolean flag) {
		exec_write_bit(name,flag);
	}
	@Override
	public Token parse(final Token tkn, final String txt) {
		MCPayload mp = null;
		switch(txt.charAt(0)) {
		case 'X':
		case 'Y':
		case 'M':
		case 'L':
		case 'F':
		case 'V':
		case 'B':
		case 'S':
			mp = exec_read_bit(txt);
			break;
		case 'C':
		case 'D':
		case 'T':
		case 'W':
			mp = exec_read_u16(txt);
			break;
		}
		tkn.user_data = mp;
		return tkn;
	}
	@Override
	public void pack2token(final MCPack pk, final Token tk){
		MCPayload mp = (MCPayload)pk;
		if(mp.content==null) {
			return;
		}
		switch(tk.name.charAt(0)) {
		case 'X':
		case 'Y':
		case 'M':
		case 'L':
		case 'F':
		case 'V':
		case 'B':
		case 'S':
			tk.valInt = mp.getFeedback(1);
			break;
		case 'C':
		case 'D':
		case 'T':
		case 'W':
			tk.valInt = mp.getFeedback(4);
			break;
		}
	}
	//---------------------------------------
	
	private class MCPayload implements MCPack {
		@Override
		public void sendPack(OutputStream ss) throws IOException {			
			final byte[] head = gen_head();//the value of 『req_size』include data area!!! 
			ss.write(Misc.chainBytes(head,cmd_sub,content));
		}
		@Override
		public void recvPack(InputStream ss) throws IOException {
			if(get_complete(ss)==false) {
				return;
			}
			switch(command) {
			case 0x0401:
				switch(s_comma) {
				case 0x0001:
					get_batch_read_bit(ss);//data in bit unit
					break;
				case 0x0000:
					get_batch_read_u16(ss);//data in word unit
					break;
				}				
				break;
			case 0x1401://batch write~~~
			case 0x1402://random write(pick any devices to access)~~~
				break;
			case 0x0619:
				get_self_test(ss);
				break;
			default:
				Misc.logw("[%s] no supported command - %04X", TAG, command);
				break;
			}
		}
		//------------//

		int sub_head = 0x5000;//sub header
		int net_numb = 0x00;//network number
		int sta_numb = 0xFF;//request destination station number
		int mod_numb = 0x03FF;//request destination module I/O number
		int drp_numb = 0x00;//request destination multi_drop station number
		int req_size;//request data length(size), include reserved, command, sub-command, data area

		byte[] gen_head() {
			if(USE_ASCII==true) {
				req_size = (4+4+4);//reserve, command, sub-command
				if(content!=null) {
					req_size += content.length;
				}
				return String.format(
					"%04X%02X%02X%04X%02X%04X0000", 
					sub_head,
					net_numb,sta_numb,
					mod_numb,
					drp_numb,
					req_size
				).getBytes();
			}else {
				req_size = (2+2+2);//reserve, command, sub-command
				if(content!=null) {
					req_size += content.length;
				}
				return new byte[] {
					Misc.maskInt1(sub_head),
					Misc.maskInt0(sub_head),
					Misc.maskInt0(net_numb),
					Misc.maskInt0(sta_numb),
					Misc.maskInt0(mod_numb),
					Misc.maskInt1(mod_numb),
					Misc.maskInt0(drp_numb),
					Misc.maskInt0(req_size),
					Misc.maskInt1(req_size),
					0x10, 0x00, //reserved
				};
			}
		}
				
		int command = -1;
		int s_comma = -1;
		byte[] cmd_sub = null;
		byte[] content = null;

		MCPayload setContent(
			final int cmd, 
			final int sub, 
			final byte[] ctxt
		) {
			command = cmd;
			s_comma = sub;
			if(USE_ASCII==true) {
				cmd_sub = String.format(
					"%04X%04X",
					command,s_comma
				).getBytes();
			}else {
				cmd_sub = new byte[] {
					Misc.maskInt0(command), Misc.maskInt1(command),
					Misc.maskInt0(s_comma), Misc.maskInt1(s_comma),
				};
			}
			content = ctxt;
			return this;
		}
		
		byte[] feedback= null;//when read from device....
		
		int[] getFeedback(int SizeOf) {
			if(e_code!=0) { return null; }
			if(feedback==null) { return null; }
			//SizeOf:1 --> content in bit unit
			//SizeOf:4 --> content in word(u16) unit
			int[] res = null;
			if(USE_ASCII==true) {
				res = new int[feedback.length/SizeOf];
				for(int i=0; i<res.length; i++) {
					final int j = i*SizeOf;
					final String v = buf2txt(feedback,j,SizeOf);
					res[i] = Integer.valueOf(v, 16);
				}				
			}else {
				//TODO!!!!
			}
			return res;
		}
		
		//int s_code;//reply code from PLC, subheader~~
		int e_code;//reply from PLC, end(complete?) code 
		
		boolean get_complete(InputStream ss) throws IOException {
			int s_code=0, r_size = 0; //response data length
			byte[] buff;
			if(USE_ASCII==true) {
				buff = new byte[11*2];
				ss.read(buff);				
				s_code = Integer.valueOf(buf2txt(buff, 0,4), 16);
				r_size = Integer.valueOf(buf2txt(buff,14,4), 16) - 4;//skip end code
				e_code = Integer.valueOf(buf2txt(buff,18,4), 16);				
			}else {
				buff = new byte[11];
				ss.read(buff);
				s_code = Misc.byte2int(buff[ 0], buff[1]);
				r_size = Misc.byte2int(buff[ 8], buff[7]) - 2;//skip end code
				e_code = Misc.byte2int(buff[10], buff[9]);//L,H endian
			}
			if(e_code==0) {
				return true;
			}
			//get error information(binary mode):
			//  Network number(responding station), 1 byte
			//  Request destination station number(responding station), 1 byte
			//  Request destination module I/O number, 2 byte
			//  Request destination multi-drop station number, 1 byte
			//  Command, 2 byte
			//  Sub-command, 2 byte
			feedback = new byte[r_size];
			if(USE_ASCII==true) {
				ss.read(buff);
				Misc.loge("[%s][recv] %s", TAG, new String(feedback));
			}else {
				ss.read(buff);
				Misc.loge("[%s][recv] ****", TAG);
			}
			return false;
		}
		
		void get_batch_read_bit(InputStream ss) throws IOException {
			//first, take size from context
			int size;
			if(USE_ASCII==true) {
				size = Integer.valueOf(buf2txt(content,-4,4),16);
				feedback = new byte[size];
			}else {				
				size = Misc.byte2int(
					feedback[content.length-2],
					feedback[content.length-1]
				);
				size = size * 4;
				if(size%8!=0) {
					size = size/8 + 1;
				}else {
					size = size/8;
				}
				feedback = new byte[(size*4)/8];
			}
			ss.read(feedback);
		}		
		void get_batch_read_u16(InputStream ss) throws IOException {
			int size;
			if(USE_ASCII==true) {
				size = Integer.valueOf(buf2txt(content,-4,4),16);
				feedback = new byte[size*4];
			}else {
				size = Misc.byte2int(
					feedback[content.length-2],
					feedback[content.length-1]
				);
				feedback = new byte[size*2];
			}
			ss.read(feedback);
		}
		
		void get_self_test(InputStream ss) throws IOException {
			if(USE_ASCII==true) {
				byte[] buf = new byte[4];//number of loopback data
				ss.read(buf);
				final int size = Integer.valueOf(buf2txt(buf),16);
				feedback = new byte[size];
				ss.read(feedback);
			}else {
				byte[] buf = new byte[2];//number of loopback data
				ss.read(buf);
				final int size = Misc.byte2int(buf[1],buf[0]);
				feedback = new byte[size];
				ss.read(feedback);
			}
		}
		
		String buf2txt(byte[] buf, int off, int size) {
			String txt = "";
			if(off<0) {
				off = buf.length + off;//-1 will be last character
			}else {
				off = off % buf.length;
			}
			for(int i=off; i<(off+size); i++) {
				if(i>=buf.length) {
					Misc.logw("[%s] invalid size for ASCII buffer", TAG);
					break;
				}
				txt = txt + (char)(buf[i]);
			}
			return txt;
		}
		String buf2txt(byte[] buf) {
			return buf2txt(buf,0,buf.length);
		}
	};
	//---------------------------------------
	/**
	 * X*,Y*: digital input, output, code:9C, 9D
	 * M*: internal relay(bit), code:90
	 * L*: latching relay(bit), code:92
	 * F*: annunciator(bit), code:93
	 * V*: edge relay(bit), code:94
	 * B*: link relay(bit), code:A0
	 * S*: step relay(bit), code:98
	 * D*: data register(word, 16 bit), code:A8
	 * W*: link register(word, 16 bit), code:B4
	 * TS,TC,TN: timer, TN is 16 bit value, code:C1 C0 C2
	 * CS,CC,CN: counter, CN is 16 bit value, code:C4 C3 C5
	 * Device head number(address) must be 6-digital.<p>
	 * the last is also head number.<p>
	 * There are some special registers and relays which we don't support.<p>
	 * Example: Y*16, X*16~220, D820=1000
	 */
	private final static String REGX_TOKEN="[XYMDSTCLFVBW][\\*SCN]\\d{1,6}+([-~=]\\d{1,6}+)?+";
	
	private int prfx2code(final String prefix) {
		int code = -1;
		switch(prefix.charAt(0)) {
		case 'X': code=0x9C; break;
		case 'Y': code=0x9D; break;
		case 'M': code=0x90; break;
		case 'L': code=0x92; break;
		case 'F': code=0x93; break;
		case 'V': code=0x94; break;
		case 'B': code=0xA0; break;
		case 'S': code=0x98; break;
		case 'D': code=0xA8; break;
		case 'W': code=0xB4; break;
		case 'T':
			switch(prefix.charAt(1)) {
			case 'S': code=0xC1; break;
			case 'C': code=0xC0; break;
			case 'N': code=0xC2; break;
			}
			break;
		case 'C':
			switch(prefix.charAt(1)) {
			case 'S': code=0x51; break;
			case 'C': code=0x50; break;
			case 'N': code=0x52; break;
			}
			break;
		default:
			Misc.logw("[%s] invalid token: %s", TAG);
			break;
		}
		return code;
	}
	
	private byte[] parse_range_token(final String txt) {
		if(txt.matches(REGX_TOKEN)==false) {
			Misc.logw("[%s] token no matching: %s", TAG, txt);
			return null;
		}		
		String prfx = txt.substring(0,2);
		String[] cols = txt.substring(2).split("[-|~]");
		final int noa = Integer.valueOf(cols[0]);
		final int nob = (cols.length==1)?(noa):(Integer.valueOf(cols[1]));
		if((nob-noa)<0) {
			Misc.logw("[%s] invalid token size: %s", TAG, txt);
			return null;
		}
		final int size = nob - noa + 1;
		if(USE_ASCII==true) {
			return String.format("%s%6d%04d", prfx, noa, size).getBytes();
		}else {
			final int code = prfx2code(prfx);
			if(code<0) {
				return null;
			}
			return new byte[] {
				Misc.maskInt0(noa),
				Misc.maskInt1(noa),
				Misc.maskInt2(noa),
				Misc.maskInt0(code),
				Misc.maskInt0(size),
				Misc.maskInt1(size),
			};
		}
	}
		
	private byte[] parse_assign_token(final String txt) {
		if(txt.contains("=")==false) {
			return null;
		}
		if(txt.matches(REGX_TOKEN)==false) {
			Misc.logw("[%s] token no matching: %s", TAG, txt);
			return null;
		}
		String prfx = txt.substring(0,2);
		String[] cols = txt.substring(2).split("=");
		final int addr= Integer.valueOf(cols[0]);
		final int sign= Integer.valueOf(cols[1]);
		if(USE_ASCII==true) {
			if(is_bit_unit(prfx.charAt(0))==true) {
				return String.format("%s%6d%02d", prfx, addr, sign).getBytes();
			}else {
				return String.format("%s%6d%04d", prfx, addr, sign).getBytes();
			}			
		}else {
			final int code = prfx2code(prfx);
			if(code<0) {
				return null;
			}
			if(is_bit_unit(prfx.charAt(0))==true) {
				return new byte[] {
					Misc.maskInt0(addr),
					Misc.maskInt1(addr),
					Misc.maskInt2(addr),
					Misc.maskInt0(code),
					Misc.maskInt0(sign),
				};
			}else {
				return new byte[] {
					Misc.maskInt0(addr),
					Misc.maskInt1(addr),
					Misc.maskInt2(addr),
					Misc.maskInt0(code),
					Misc.maskInt0(sign),
					Misc.maskInt1(sign),
				};
			}
		}
	}

	private boolean is_bit_unit(final char cc) {
		switch(cc) {		
		case 'X':
		case 'Y':
		case 'M':
		case 'L':
		case 'F':
		case 'V':
		case 'B':
		case 'S':
			return true;
		default:
		case 'C':
		case 'D':
		case 'T':
		case 'W':
			return false;
		}
	}

	private byte[] arg2bit_unit(boolean... args) {
		byte[] buf;
		if(USE_ASCII==true) {
			buf = new byte[args.length];
			for(int i=0; i<args.length; i++) {
				buf[i] = (byte)((args[i]==true)?('1'):('0'));
			}
		}else {
			//4 bit present a boolean flag
			final int cnt = (args.length*4)/8 + (args.length%2);//trick!!!
			buf = new byte[cnt];
			for(int i=0; i<args.length; i+=2) {
				int flg = 0;				
				if(args[i+0]==true) { flg = flg | 0x10; }
				if((i+1)<args.length) {
					if(args[i+1]==true) { flg = flg | 0x01; }
				}
				buf[i/2] = (byte)flg;
			}
		}		
		return buf;
	}
	
	/**
	 * prepare arguments for bit device in word unit.<p>
	 * @param args
	 * @return
	 */
	private byte[] args2word_unit(boolean... args) {
		//1 bit present a boolean flag
		if(USE_ASCII==true) {			
			String txt = "";
			for(int i=0; i<args.length; i+=16) {
				int flg = 0;
				for(int j=0; j<16; j++) {
					if((i+j)>=args.length) {
						break;
					}
					if(args[i+j]==true) {
						flg = (flg << 1) + 1;
					}else {
						flg = (flg << 1) + 0;
					}
				}
				txt = txt + String.format("%04X", flg);
			}
			return txt.getBytes();
		}else {
			int cnt = args.length/16;
			if(args.length%16!=0) { cnt+=1; }
			
			byte[] buf = new byte[2*cnt];
			for(int i=0; i<args.length; i+=16) {
				int flg = 0;
				for(int j=0; j<16; j++) {
					if((i+j)>=args.length) {
						break;
					}
					if(args[i+j]==true) {
						flg = (flg << 1) + 1;
					}else {
						flg = (flg << 1) + 0;
					}
				}
				buf[i/16+0] = Misc.maskInt0(flg);//little-endian
				buf[i/16+1] = Misc.maskInt1(flg);//little-endian
			}
			return buf;
		}
	}
	private byte[] args2word_unit(int... args) {
		if(USE_ASCII==true) {
			String txt = "";
			for(int i=0; i<args.length; i+=16) {
				txt = txt + String.format("%04X", args[i]);
			}
			return txt.getBytes();
		}else {
			byte[] buf = new byte[args.length * 2];
			for(int i=0; i<args.length; i++) {
				buf[i*2+0] = Misc.maskInt0(args[i]);//little-endian
				buf[i*2+1] = Misc.maskInt1(args[i]);//high-endian
			}
			return buf;
		}
	}
	//---------------------------------------
	
	/**
	 * give device name and header(address).<p>
	 * EX: X1000, Y0016, etc.....<p>
	 * Then, context will be bit data.<p>
	 * @param txt - device name and header(address) 
	 * @return payload
	 */
	private MCPayload exec_read_bit(final String txt) {
		return exec_read(0x0001, txt);
	}
	/**
	 * give device name and header(address).<p>
	 * EX: X1000, Y0016, etc.....<p>
	 * Then, context will be word(16 bit) data.<p>
	 * @param txt - device name and header(address) 
	 * @return payload
	 */
	private MCPayload exec_read_u16(final String txt) {
		return exec_read(0x0000, txt);
	}
	private MCPayload exec_read(final int sub_cmd, final String txt) {
		MCPayload mp = new MCPayload();
		byte[] buf = parse_range_token(txt);
		if(buf==null) {	return mp; }
		mp.setContent(0x0401, sub_cmd, buf);
		transmit(mp);
		return mp;
	}
	
	/**
	 * 2 digit code/6 digit number specification.<p>
	 * no check arguments size!!.<p>
	 * batch writing <p>
	 * @param txt
	 * @param vals
	 * @return payload
	 */
	private MCPayload exec_write_bit(		
		final String txt,
		final boolean... vals
	) {
		MCPayload mp = new MCPayload();
		byte[] buf1 = parse_range_token(txt);
		byte[] buf2 = arg2bit_unit(vals);
		mp.setContent(
			0x1401, 0x0001, 
			Misc.chainBytes(buf1,buf2)
		);
		transmit(mp);
		return mp;
	}
	
	/**
	 * random writing. pick any device to write value~~.<p>
	 * @param txt
	 * @param val
	 * @return
	 */
	private MCPayload exec_assign_bit(
		final String... lst
	) {
		ArrayList<byte[]> bufs = new ArrayList<byte[]>();
		for(String txt:lst) {
			byte[] buf = parse_assign_token(txt);
			if(buf==null) {
				continue;
			}
			bufs.add(buf);
		}
		byte[] ctxt = String.format("%02d", bufs.size()).getBytes();
		for(byte[] bb:bufs) {
			ctxt = Misc.chainBytes(ctxt,bb);
		}
		
		MCPayload mp = new MCPayload();
		mp.setContent(0x1402, 0x0001, ctxt);
		transmit(mp);
		return mp;
	}
	
	public void execToggleBit(final String txt) { asyncBreakIn(()->{
		exec_assign_bit(txt+"=1");
		block_sleep_msec(500);
		exec_assign_bit(txt+"=0");
	});}
	
	/**
	 * 2 digit code/6 digit number specification.<p>
	 * no check arguments size!!.<p>
	 * @param txt
	 * @param vals
	 * @return payload
	 */
	/*private MCPayload exec_write_val(
		final String txt, 
		final int... vals
	) {
		MCPayload mp = new MCPayload();
		byte[] buf = parse_assign_token(txt);
		if(buf==null) {	return mp; }
		mp.setContent(
			0x1401, 0x0000, 
			Misc.chainBytes(buf,args2word_unit(vals))
		);
		transmit(mp);
		return mp;
	}*/
	
	/**
	 * test communication with '55AA55AA'
	 * @return
	 */
	private boolean exec_self_test() {
		final String TEST = "55AA55AA";
		final int TEST_SIZE = TEST.length();
		MCPayload mp = new MCPayload();
		byte[] buff, pads;
		if(USE_ASCII==true) {
			pads = String.format("%04d", TEST_SIZE).getBytes();
		}else {
			pads = new byte[2];
			pads[0] = Misc.maskInt0(TEST_SIZE);
			pads[1] = Misc.maskInt1(TEST_SIZE);
		}
		buff = Misc.chainBytes(
			pads,
			TEST.getBytes()
		);
				
		mp.setContent(0x0619, 0x0000, buff);
		transmit(mp);
		//compare buffer
		if(mp.feedback==null) {
			Misc.loge("[%s] SELF-TEST: no response content!!", TAG);
			return false;
		}
		if(mp.feedback.length!=TEST_SIZE) {
			Misc.loge("[%s] SELF-TEST: mismatch length!!", TAG);
			return false;
		}
		final String res = new String(mp.feedback);
		if(res.equals(TEST)==false) {
			Misc.loge("[%s] SELF-TEST: mismatch data!!", TAG);
			return false;
		}
		Misc.logv("[%s] SELF-TEST done...", TAG);
		return true;
	}
	//---------------------------------------

}
