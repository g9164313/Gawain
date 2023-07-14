package narl.itrc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import com.sun.glass.ui.Application;

import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

/**
 * Support Modbus device, provide a looper to update or modify registers.<p>
 * This module is dependent on native libmodbus.<p>
 * @author qq
 *
 */
public class DevModbus extends DevBase {

	public DevModbus(){
		TAG = "Modbus";
	}
	
	//below variables will be accessed by native code
	private String rtuName = "";
	private int    rtuBaud = 9600;
	private byte   rtuData = '8';
	private byte   rtuMask = 'n';
	private byte   rtuStop = '1';

	private String tcpName = "";	
	private int    tcpPort = 502;
	
	private long handle= 0L;
	
	private short slave= 0;//MODBUS_BROADCAST_ADDRESS

	private final static String STG_IGNITE = "ignite";
	private final static String STG_LOOPER = "looper";
	
	
	/**
	 * Choose which type connection, format is below:
	 * RTU:[device name],[baud-rate],[8n1]
	 * TCP:[IP address]#[port]
	 */
	@Override
	public void open() {
		final String path = Gawain.prop().getProperty(TAG, "");
		if(path.length()==0) {
			Misc.logw("No default tty name...");
			return;
		}
		open(path,0);
	}
	public void open(final String path) {
		open(path,0);
	}
	public void open(final DevModbus dev,final int sid) {
		//common wire!!!, example: Modbus RTU
		handle= dev.handle; 
		slave = (short)sid;
		if(cells.size()!=0 && handle>0L) {
			playLoop();
		}
	}
	public void open(final String path, final int sid) {
		if(path.matches("^[rR][tT][uU]:[\\/\\w]+,\\d+,[78][neoNEO][12]")==true) {			
			String[] col = path.substring(4).split(",");	
			if(col.length>=1) {
				rtuName = col[0];
			}
			if(col.length>=2) {
				rtuBaud = Integer.valueOf(col[1]);
			}
			if(col.length>=3) {
				rtuData = (byte)col[2].charAt(0);
				//LibModbus use upper-case mask sign!!!
				rtuMask = (byte)Character.toUpperCase(col[2].charAt(1));
				rtuStop = (byte)col[2].charAt(2);
			}
			implOpenRtu();
			
		}else if(path.matches("^[tT][cC][pP]:\\d{1,3}.\\d{1,3}.\\d{1,3}.\\d{1,3}[#]?[\\d]*")==true) {		
			
			String[] col = path.substring(4).split("#");
			if(col.length>=1) {
				tcpName = col[0];
			}
			if(col.length>=2) {
				tcpPort = Integer.valueOf(col[1]);
			}
			implOpenTcp();
			
		}else {
			handle = 0L;
		}
		slave = (short)sid;
		if(cells.size()!=0 && handle>0L) {
			playLoop();
		}
	}


	private static final Semaphore sema = new Semaphore(1);

	/**
	 * 	User can override this to prepare something.<p>
	 * 	Remember this is not called by GUI-thread.<p>
	 */
	protected void ignite_task(){}

		/**
	 * 	User can override this to launch change-event.<p>
	 * 	This hook is called by GUI-thread.<p>
	 */
	protected void looper_event(){}
	
	private ArrayList<RecallCell> cells = new ArrayList<>();
	
	public void playLoop() {
		if(isFlowing()==true) {
			return;
		}
		addState(STG_IGNITE,()->{
			nextState(STG_LOOPER);
			ignite_task();		
		});
		addState(STG_LOOPER,()->{
			nextState(STG_LOOPER);
			if(cells.size()==0) {
				block_sleep_msec(500);
				return;
			}
			try {
				sema.acquire();
				//System.out.printf("[%s] acquire\n", TAG);
				implSlaveID(slave);
				for(RecallCell cc:cells) {
					cc.fecth_data();
				}
				//System.out.printf("[%s] relase\n", TAG);
				sema.release();
			} catch (InterruptedException e) {			
				System.out.printf("[%s][STG_LOOPER] %s\n", TAG, e.getMessage());
				return;
			}		
			Application.invokeLater(()->{
				for(RecallCell cc:cells) {
					cc.update_property();
				}
				looper_event();
			});
			block_sleep_msec(200);
		});
		playFlow(STG_IGNITE);
	}

	@Override
	public void close() {		
		if(handle==0L) {
			return;
		}
		if(cells.size()!=0) {
			stopFlow();
		}
		implClose();
	}
	//-----------------------
	
	private class MirrorCell {
		final int    offset;
		/**
		 * property type present~:
		 * S:16-bit, 
		 * I:32-bit,HL
		 * J:32-bit,LH 
		 * F:ieee3248, float,
		 */
		final char   t_type;
		final Object target;
		MirrorCell(
			final int off,
			final char typ,
			final Object obj
		) {
			offset = off;
			t_type = Character.toUpperCase(typ);
			target = obj;
		}
		void update_number(final short[] blkdata) {
			switch(t_type) {
			case 'S': 
				update_short(blkdata);
				break;		
			case 'I':
				update_integer(blkdata,true);
				break;
			case 'J':
				update_integer(blkdata,false);
				break;
			case 'F':
				update_float(blkdata); 
				break;
			}
		}
		private void update_short(final short[] blkdata) {
			if(offset>=blkdata.length) {
				return;
			}
			int vv = (((int)blkdata[offset])&0x0000FFFF);				
			((IntegerProperty)target).set(vv);		
		}
		private void update_integer(final short[] blkdata,final boolean HtoL) {
			if((offset+1)>=blkdata.length) {
				return;
			}
			int vv = 0, aa=1, bb=0;
			if(HtoL){
				aa=0; bb=1;		
			}else{
				aa=1; bb=0;	
			}
			vv = (int)blkdata[offset+aa];
			vv = vv << 16;
			vv = vv | (((int)blkdata[offset+bb])&0x0000FFFF);	
			((IntegerProperty)target).set(vv);	
		}
		private void update_float(final short[] blkdata) {
			if((offset+1)>=blkdata.length) {
				return;
			}
			byte[] bb = {
				(byte)((blkdata[offset+1] & 0xFF00)>>8),
				(byte)((blkdata[offset+1] & 0x00FF)>>0),
				(byte)((blkdata[offset+0] & 0xFF00)>>8),
				(byte)((blkdata[offset+0] & 0x00FF)>>0),
			};
			float v = ByteBuffer.wrap(bb).getFloat();
			((FloatProperty)target).set(v);
				
		}
	}
	//--------------------------------------------------------

	private class RecallCell {
		final char func_id;		
		final int  address;
		final short[] blkdata;  
		final ArrayList<MirrorCell> prop = new ArrayList<>();

		RecallCell(
			final char fid,
			final int addr,
			final int size
		) {
			func_id = fid;
			address = addr;			
			blkdata = new short[size];
		}
		void fecth_data() {
			switch(func_id) {
			case 'C':
				//coils, function code = 1
				implReadC(address,blkdata);
				break;
			case 'S':
				//input status, function code = 2
				implReadS(address,blkdata);
				break;
			case 'R':
			case 'I':
				//input register, function code = 4
				implReadI(address,blkdata);
				break;	
			case 'H':
				//holding register, function code = 3
				implReadH(address,blkdata);
				break;
			}
		}
		void update_property() {
			for(MirrorCell mc:prop) {
				mc.update_number(blkdata);
			}
		}
	};

	/**
	 * mapping register address.<p>
	 * first character is register type, it can be C, S, I, H<p>
	 * C - coil register
	 * S - input status
	 * H - holding register
	 * I - input register
	 * @param radix - address value radix
	 * @param address - register address, ex:H303, I12A 
	 * @return
	 */
	public DevModbus mapAddress(
		final int radix,
		final String... address
	) {
		String pattn;
		if(radix==16) {
			pattn = "[CSHI][0123456789ABCDEF]+([-~][0123456789ABCDEF]+)?";
		}else {
			pattn = "[CSHI]\\d+([-~]\\d+)?";
		}		
		for(String txt:address) {
			txt = txt.toUpperCase();
			if(txt.matches(pattn)==false) {
				continue;
			}else if(txt.length()==0){
				continue;
			}
			char fid = txt.toUpperCase().charAt(0);
			int addr = 0;
			int size = 1;
			String[] col = txt.substring(1).split("[-~]");
			if(col.length>=1) {
				addr = Integer.valueOf(col[0],radix);
			}
			if(col.length>=2) {
				size = Integer.valueOf(col[1],radix) - addr + 1;
				if(size<=0) {
					return this;
				}
			}
			cells.add(new RecallCell(fid,addr,size));
		}
		return this;
	}
	/**
	 * convenience function for 'mapAddress(radix,address)'.<p>
	 * address base is decimal.<p>
	 * mapping register address.<p>
	 * first character is register type, it can be C, H, I.<p>
	 * C - coil register
	 * H - holding register
	 * I/R - input register
	 * @param address
	 * @return
	 */
	public DevModbus mapAddress(final String... address) {
		return mapAddress(10,address);
	}
	public DevModbus mapAddress10(final String... address) {
		return mapAddress(10,address);
	}
	public DevModbus mapAddress16(final String... address) {
		return mapAddress(16,address);
	}
	
	public IntegerProperty mapShort(final int address) {
		IntegerProperty prop = new SimpleIntegerProperty();
		add_mirror_cell(address,'S',prop);
		return prop;
	}
	public IntegerProperty mapInteger(final int address) {
		return mapIntegerHL(address);
	}
	public IntegerProperty mapIntegerHL(final int address) {
		IntegerProperty prop = new SimpleIntegerProperty();
		add_mirror_cell(address,'I',prop);
		return prop;
	}
	public IntegerProperty mapIntegerLH(final int address) {
		IntegerProperty prop = new SimpleIntegerProperty();
		add_mirror_cell(address,'J',prop);
		return prop;
	}
	public FloatProperty mapFloat(final int address) {		
		FloatProperty prop = new SimpleFloatProperty();
		add_mirror_cell(address,'F',prop);
		return prop;
	}

	/**
	 * 
	 * @param address
	 * @param prop_type
	 * @param property
	 */
	private void add_mirror_cell(
		final int address,
		final char prop_type,
		final Object property
	) {
		for(RecallCell cc:cells) {
			final int beg = cc.address;
			final int end = cc.address + cc.blkdata.length;
			if(beg<=address && address<end) {
				cc.prop.add(new MirrorCell(
					address - cc.address,
					prop_type,property
				));
				return;
			}
		}
	}
	//-------------------------------------//

	private int pan_addr=0, pan_size=8;
	private short[] pan_buff = null;

	private class PanRanger extends PanDialog<Integer>{
		final TextField box_addr, box_size;
		PanRanger(){
			box_addr = new TextField(String.format("x%05X", pan_addr));
			box_addr.setPrefWidth(80.);
			box_size = new TextField(String.format("%d", pan_size));
			box_size.setPrefWidth(80.);

			final GridPane lay = new GridPane();
			lay.getStyleClass().addAll("box-pad");
			lay.addRow(0, new Label("位址："), box_addr);
			lay.addRow(0, new Label("大小："), box_size);
			init(lay);
		}
		@Override
		protected boolean set_result_and_close(ButtonType type) {
			if(type.equals(ButtonType.OK)) {
				try{
					String txt_addr = box_addr.getText().trim();
					if(txt_addr.startsWith("0x")){
						txt_addr = txt_addr.substring(2);
						pan_addr = Integer.valueOf(txt_addr,16);
					}else if(txt_addr.charAt(0)=='x'){
						txt_addr = txt_addr.substring(1);
						pan_addr = Integer.valueOf(txt_addr,16);
					}else{
						pan_addr = Integer.valueOf(txt_addr);
					}				
					pan_size = Integer.valueOf(box_size.getText());
					setResult(1);
				}catch(NumberFormatException e){
					setResult(0);
				}
			}else if(type.equals(ButtonType.CANCEL)){
				setResult(0);
			}
			return true;
		}
	};
	private class PanEditor extends PanDialog<Integer>{
		TextField[] box_vals = null;
		PanEditor(){
			box_vals = new TextField[pan_buff.length];
			for(int i=0; i<pan_buff.length; i++){
				box_vals[i] = new TextField(String.format("%d", (int)pan_buff[i]));
				box_vals[i].setPrefWidth(80.);
			}
			final GridPane lay = new GridPane();
			lay.getStyleClass().addAll("box-pad");
			lay.addRow(0, new Label("位址"), new Label("數值（10進制）"));
			for(int i=0; i<pan_buff.length; i++){
				lay.addRow(i+1, new Label(String.format("0x%05X",pan_addr+i)), box_vals[i]);
			}
			init(lay);
		}
		@Override
		protected boolean set_result_and_close(ButtonType type) {
			if(type.equals(ButtonType.OK)) {				
				for(int i=0; i<pan_buff.length; i++){
					try{
						pan_buff[i] = Short.valueOf(box_vals[i].getText());
					}catch(NumberFormatException e){
					}
				}
				setResult(1);
			}else if(type.equals(ButtonType.CANCEL)){
				setResult(0);
			}
			return true;
		}
	};
	
	protected void pop_editor(){
		final PanRanger rng = new PanRanger();
		if(rng.showAndWait().get()==0){
			return;
		}		
		asyncBreakIn(()->{
			//fetch register data from device
			pan_buff = readRegVal('I', pan_addr, pan_size);
		}, ()->{
			final PanEditor edt = new PanEditor();
			if(edt.showAndWait().get()==0){
				return;
			}
			asyncBreakIn(()->{
				//write data back to device register
				writeRegVal(pan_addr, pan_buff);
				pan_buff = null;
			});
		});
	}
	//-------------------------------------//

	public void readRegVal(final char fid,final int addr,final short[] regs){
		char _fid = Character.toUpperCase(fid);
		try {
			sema.acquire();
			implSlaveID(slave);		
			switch(_fid) {
			case 'C'://coils, function code = 1
				implReadC(addr,regs);
				break;
			case 'S'://discrete input, function code = 2
				implReadS(addr,regs);
				break;
			case 'H'://holding register, function code = 3
				implReadH(addr,regs);
				break;				
			case 'I'://input register, function code = 4
				implReadI(addr,regs);
				break;
			}
			sema.release();
		} catch (InterruptedException e) {
			System.err.printf("[%s][readRegVal] %s\n", TAG, e.getMessage());
		}
	}
	public short[] readRegVal(final char fid,final int addr,final int size) {		
		short[] val = new short[size];
		readRegVal(fid,addr,val);
		return val;
	}
	public int readRegVal(final char fid,final int addr){
		short[] val = readRegVal(fid,addr,1);
		return ((int)val[0])&0x0000FFFF;
	}

	public int writeRegVal(final int addr, final short... value){
		int res = 0;
		try {
			sema.acquire();
			implSlaveID(slave);
			res = implWrite(addr,value);
			sema.release();
		} catch (InterruptedException e) {
			System.err.printf("[%s][writeRegVal] %s\n", TAG, e.getMessage());
			res = -100;
		}
		return res;
	}
	public int writeRegVal(final int addr, final int... value){
		short[] val = new short[value.length];
		for(int i=0; i<value.length; i++){
			val[i] = (short)(value[i]&0xFFFF);
		}
		return writeRegVal(addr,val);
	}
	
	public void writeForce(final int addr, final short... value){
		try {
			sema.acquire();
			int res = 0;
			do{
				implSlaveID(slave);			
				res = implWrite(addr,value);
				if(res<0){
					block_sleep_msec(10);
				}
			}while(res<0);
			sema.release();
		} catch (InterruptedException e) {
			System.err.printf("[%s][writeRegVal] %s\n", TAG, e.getMessage());
		}
	}
	public void writeForce(final int addr, final int... value){
		short[] val = new short[value.length];
		for(int i=0; i<value.length; i++){
			val[i] = (short)(value[i]&0xFFFF);
		}
		writeForce(addr,val);
	}	

	public int writeRegPair(final int... pair){
		int res = 0;
		try {
			sema.acquire();
			implSlaveID(slave);
			for(int i=0; i<pair.length; i+=2){
				int addr = pair[i+0];
				short[] val = {(short)(pair[i+1]&0xFFFF)};				
				res = res | implWrite(addr,val);
			}			
			sema.release();
		} catch (InterruptedException e) {
			System.err.printf("[%s][writeRegVal] %s\n", TAG, e.getMessage());
			res = -100;
		}
		return res;
	}
	//-------------------------------------//

	protected native void implOpenRtu();	
	protected native void implOpenTcp();
	protected native void implSlaveID(int slave_id);
	protected native void implReadC(int addr,short buff[]);//coils status(FC=1)
	protected native void implReadS(int addr,short buff[]);//input status(FC=2)	
	protected native void implReadH(int addr,short buff[]);//holding register
	protected native void implReadI(int addr,short buff[]);//input register
	protected native int implWrite(int addr,short buff[]);
	protected native void implClose();
}
