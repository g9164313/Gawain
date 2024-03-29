package prj.sputter;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.jfoenix.controls.JFXButton;
import com.sun.glass.ui.Application;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.image.ImageView;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import jssc.SerialPort;
import jssc.SerialPortException;
import narl.itrc.DevTTY;
import narl.itrc.Gawain;
import narl.itrc.Misc;
import narl.itrc.PadTouch;
import narl.itrc.PanBase;

/**
 * INFICON STM-160 Milti-Film Rate/Thickness Monitor.<p>
 * @author qq
 *
 */
public class DevSQM160 extends DevTTY {

	public DevSQM160() {
		TAG = "SQM160";
	}
	@Override
	public void afterOpen() {
		addState(STG_INIT,()->state_initial()).
		addState(STG_LIFE,()->state_chk_life()).
		addState(STG_MONT,()->state_monitor());			
		playFlow(STG_INIT);
	}
	@Override
	public void beforeClose() {
	};	
	//------------------------------------//
	
	public final BooleanProperty shutter = new SimpleBooleanProperty(false);

	public final StringProperty filmName = new SimpleStringProperty("");
	
	public final StringProperty unitRate = new SimpleStringProperty("？");
	public final StringProperty unitThick= new SimpleStringProperty("？");
	
	public final FloatProperty meanRate = new SimpleFloatProperty();
	public final FloatProperty meanThick= new SimpleFloatProperty();
	
	public final FloatProperty minRate = new SimpleFloatProperty(0f);
	public final FloatProperty maxRate = new SimpleFloatProperty(100f);
	public final FloatProperty minThick= new SimpleFloatProperty(0f);
	public final FloatProperty maxThick= new SimpleFloatProperty(100f);
	
	public String getTextRate() {
		return rate[0].get()+unitRate.get();
	}
	public String getTextThick() {
		return thick[0].get()+unitThick.get();
	}

	//SQM-160 can connect 6 sensors~~~
	public final SimpleStringProperty[] rate = {
		new SimpleStringProperty("？"),//average rate value
		new SimpleStringProperty(),
		new SimpleStringProperty(),
		new SimpleStringProperty(),
		new SimpleStringProperty(),
		new SimpleStringProperty(),
		new SimpleStringProperty()
	};
	public final SimpleStringProperty[] thick= {
		new SimpleStringProperty("？"),//average thick value
		new SimpleStringProperty(),
		new SimpleStringProperty(),
		new SimpleStringProperty(),
		new SimpleStringProperty(),
		new SimpleStringProperty(),
		new SimpleStringProperty()
	};
	//Frequency for each sensor, no average reading~~~
	public final SimpleStringProperty[] freq = {
		null,
		new SimpleStringProperty(),
		new SimpleStringProperty(),
		new SimpleStringProperty(),
		new SimpleStringProperty(),
		new SimpleStringProperty(),
		new SimpleStringProperty()
	};
	//Crystal Life for each sensor, no average reading, 2 decimal digital~~~
	public final FloatProperty[] life = {
		null,
		new SimpleFloatProperty(),
		new SimpleFloatProperty(),
		new SimpleFloatProperty(),
		new SimpleFloatProperty(),
		new SimpleFloatProperty(),
		new SimpleFloatProperty()
	};
	
	private int film_indx = 0;
	private final String[] film_data = {
		"?",  // Name or title
		"0",  // Density (0.50 to 99.99 g/cm³)
		"0",  // Tooling (10~399 %)
		"0.1",// Z-ratio/factor (0.10~9.999)
		"0.0",// Final Thickness (0.000~9999.000 kÅ)
		"0.0",// Thickness Set-point (0.000~9999 kÅ)
		"0",  // Time Set-point (0~5999 second)
		"63", // Sensor Average (decimal, map to which sensor)
	};//current film data
	public String[] split_a_text(final String txt) {
		film_data[0] = "？？？？";//rest old name
		if(txt.length()==0){  return film_data; }
		if(txt.charAt(0)!='A') { return film_data; }
		film_data[0] = txt.substring(1,9);
		final String[] arg = txt.substring(9).trim().split("\\s+");
		for(int i=0; i<arg.length; i++) {
			if(i>=film_data.length){
				break;
			}
			film_data[1+i] = arg[i];
		}
		return film_data;
	}
	public String get_film_name() {
		return String.format("[%02d]%s", film_indx, film_data[0]);
	}
	
	private final String[] sys1_param = {
		"",// Time Base
		"",// Simulate Mode (0 or 1)
		"",// Display Mode (0:Å/s,kÅ, 1:nm/s,μm, 2:Hz, 3:ng/cm²/s,μg/cm²)
		"",// Rate Resolution (0:0.1 Å/s, 1:0.01 Å/s)
		"",// Rate Filter (1~20, sample reading)
		"",// Crystal Tooling-1 (10~399 %)
		"",// Crystal Tooling-2
		"",// Crystal Tooling-3
		"",// Crystal Tooling-4
		"",// Crystal Tooling-5
		"",// Crystal Tooling-6
	};	
	private final String[] sys2_param = {
		"",// Minimum Frequency (1.000~6.400 MHz)
		"",// Maximum Frequency (1.000~6.400 MHz)
		"",// Minimum Rate (-99~999 Å/s)
		"",// Maximum Rate
		"",// Minimum Thickness (0.000~9999 Å)
		"",// Maximum Thickness
		"",// Etch Mode (0:Off, 1:On)
	};
	private final String[] sensor_v = {
		"", "", "", //sensor-1
		"", "", "", //sensor-2
		"", "", "", //sensor-3
		"", "", "", //sensor-4	
		"", "", "", //sensor-5
		"", "", "", //sensor-6	
	};
	private void split_text(final String txt, final String[] lst) {
		for(int i=0; i<lst.length; i++) {
			lst[i] = "";//clear old data~~~
		}
		if(txt.length()==0){  return; }
		if(txt.charAt(0)!='A') { return; }
		final String[] arg = txt.substring(1).trim().split("\\s+");
		int cnt = arg.length;
		if(cnt>=lst.length) { cnt = lst.length; }
		for(int i=0; i<cnt; i++) {
			if(i>=lst.length){
				break;
			}
			lst[i] = arg[i];
		}
	}
	
	private static final String STG_INIT = "initial";
	private static final String STG_LIFE = "chklife";
	private static final String STG_MONT = "monitor";
	
	private void state_initial() {		
		//final String version = exec("@");//!#@O7 --> !0AMON_Ver_4.13(85)(119)
		
		final String a_txt = exec(String.format("A%c?", (film_indx+48)));
		split_a_text(a_txt);
		
		final String b_txt = exec("B?");
		split_text(b_txt, sys1_param);
		
		final String c_txt = exec("C?");
		split_text(c_txt, sys2_param);
		
		final boolean u_val = txt2boolean(exec("U?"));
		
		Application.invokeLater(()->{
			refresh_sys_info();
			filmName.set(get_film_name());
			shutter.set(u_val);
		});
		nextState(STG_MONT);
		//nextState(STG_LIFE);
	}
	private void state_chk_life() {
		final float[] r_val = {0, 0, 0, 0, 0, 0,};
		String r_txt;
		for(int i=0; i<6; i++){
			r_txt = exec(String.format("R%d",i+1));
			r_val[i] = txt2float(r_txt);
		}
		Application.invokeLater(()->{
			for(int i=1; i<life.length; i++){
				life[i].set(r_val[i-1]);
			}
		});
		nextState(STG_MONT);
	}
	
	private long monitor_tick = 0L;
	
	private void state_monitor() {

		final String m_txt = exec("M");//read average Rate		
		final float m_val = txt2float(m_txt);
		
		final String o_txt = exec("O");//read average Thickness
		final float o_val = txt2float(o_txt);
		
		final String w_txt = exec("W");//read all for each sensor, simultaneously
		split_text(w_txt, sensor_v);
		
		final boolean u_val = txt2boolean(exec("U?"));
				
		Application.invokeLater(()->{
			meanRate.set(m_val);
			meanThick.set(o_val);
			rate[0].set(m_txt.substring(2));
			thick[0].set(o_txt.substring(2));
			for(int i=1; i<rate.length; i++) {
				rate[i].set(sensor_v[(i-1)*3+0]);
			}		
			for(int i=1; i<thick.length; i++) {
				thick[i].set(sensor_v[(i-1)*3+1]);
			}
			for(int i=1; i<freq.length; i++) {
				freq[i].set(sensor_v[(i-1)*3+2]);
			}
			shutter.set(u_val);
		});
		block_sleep_msec(500);
		
		final long current_tick = System.currentTimeMillis();//millis second
		if((current_tick-monitor_tick)>=(10*60*1000)){
			monitor_tick = current_tick;//update tick!!!
			nextState(STG_LIFE);
		}else{
			nextState(STG_MONT);
		} 
	}
	
	public String exec(final String cmd) {
		if(port.isPresent()==false){
			return "";
		}
		final SerialPort dev = port.get();
		
		//Command Packet (Host to SQM-160 Message)
		//<Sync character> <Length character> <Message> <CRC1><CRC2>
		//max-length is 190 byte
		//test command: !#@O7
		final byte[] buf = new byte[200];
		int len = cmd.length();
		short crc;
		
		buf[0] = (byte)('!');
		buf[1] = (byte)(len+34);
		crc = 0x3fff;
		crc = calculate_crc(crc,buf[1]);		
		for(int i=0; i<len; i++) {			
			int val = cmd.charAt(i) & 0xFF;					
			crc = calculate_crc(crc,val);
			buf[2+i] = (byte)val;
		}
		buf[2+len] = (byte) (((crc   ) & 0x7f) + 34);
		buf[3+len] = (byte) (((crc>>7) & 0x7f) + 34);
				
		try {
			dev.purgePort(SerialPort.PURGE_RXCLEAR);
			//ready to send package(Host to SQM-160)
			dev.writeBytes(buf);
			//get response package (SQM-160 to Host Message)
			buf[0] = dev.readBytes(1)[0];//!, synchronize
			buf[1] = dev.readBytes(1)[0];//package length, it is ASCII code
			len = ((int)buf[1]&0xFF)-35;
			byte[] txt = dev.readBytes(len+2);//include CRC!!
			for(int i=0; i<txt.length; i++) {
				buf[2+i] = txt[i];
			}
		} catch (SerialPortException e) {
			Misc.loge(e.getMessage());
			return "";
		}
		
		//verify CRC...
		crc = 0x3fff;	
		for(int i=1; i<(1+1+len); i++) {
			//避開 sync 字元，所以偏移多加 1，而且 length 也要算進 CRC
			int val = buf[i] & 0xFF;			
			crc = calculate_crc(crc,val);
		}
		byte chk1 = (byte) (((crc   ) & 0x7f) + 34);
		byte chk2 = (byte) (((crc>>7) & 0x7f) + 34);
		//不知道怎麼把 CRC 逆算回去2個 byte，似乎只能順向
		final String resp = new String(buf,2,len);
		if(chk1!=buf[2+len] || chk2!=buf[3+len]){
			Misc.logw("[%s] CRC is invalid!! (%s)-(%s)", TAG, cmd, resp);
			//return "E";
		}
		return resp;
	}
	private short calculate_crc(short crc, int val) {
		crc = (short) (crc ^ (short)val);
		for (int ix = 0; ix < 8; ix++) {
			short tmpCRC = crc;
			crc = (short) (crc >> 1);
			if ((tmpCRC & 0x1) == 1) {
				crc = (short) (crc ^ 0x2001);
			}
		}
		return (short) (crc & 0x3fff);
	}
	
	private boolean txt2boolean(final String txt) {
		if(txt.length()==0){  return false; }
		if(txt.charAt(0)!='A') { return false; }
		if(txt.charAt(1)=='1') { return true; }
		return false;
	}
	private float txt2float(final String txt) {
		if(txt.length()==0){  return Float.NaN; }
		if(txt.charAt(0)!='A') { return Float.NaN; }
		try {
			return Float.valueOf(txt.substring(1).trim());
		}catch(NumberFormatException e) {
			Misc.loge(e.getMessage());
		}
		return Float.NaN;
	}
	
	private void refresh_sys_info() {
		int mod = -1;
		try {
			mod = Integer.valueOf(sys1_param[2].trim());			
			minRate.set(Float.valueOf(sys2_param[2]));
			maxRate.set(Float.valueOf(sys2_param[3]));			
			minThick.set(Float.valueOf(sys2_param[4]));
			maxThick.set(Float.valueOf(sys2_param[5]));			
		}catch(NumberFormatException e) {
			Misc.loge(e.getMessage());
			return;
		}
		switch(mod) {
		case 0:
			unitRate.set("Å/s");//Å, font problem
			unitThick.set("kÅ");//thick
			break;
		case 1:
			unitRate.set("nm/s");
			unitThick.set("μm");//thick
			break;
		case 2:
			unitRate.set("Hz");
			unitThick.set("Hz");//thick
			break;
		case 3:
			unitRate.set("ng/cm²/s");
			unitThick.set("μg/cm²");//thick
			break;
		}
	}
	//-----------------------------------------------//
	
	public void cmd_S_T() {
		try {
			Misc.logv("[%s] 清除計錄與時間",TAG);
			exec("S");
			TimeUnit.MILLISECONDS.sleep(250);
			exec("T");
			TimeUnit.MILLISECONDS.sleep(250);
		} catch (InterruptedException e1) {
			Misc.loge("[%s] INTERRUPT in command-S-T",TAG);
		}
	}
	public void cmd_U(
		final boolean flg,
		final Runnable event_done,
		final Runnable event_fail
	) {
		try {
			String u_txt = "";
			if(flg==true) {
				Misc.logv("[%s] 打開擋板",TAG);
				u_txt = exec("U1");
			}else {
				Misc.logv("[%s] 關閉擋板",TAG);
				u_txt = exec("U0");
			}
			Application.invokeLater(()->shutter.set(flg));
			TimeUnit.MILLISECONDS.sleep(250);
			if(u_txt.charAt(0)=='A') {
				if(event_done!=null) { Application.invokeLater(event_done); }
			}else{
				if(event_fail!=null) { Application.invokeLater(event_fail);}
			}			
		} catch (InterruptedException e) {
			Misc.loge("[%s] INTERRUPT in command-U",TAG);
		}
	}
		
	public void zeros() {asyncBreakIn(()->{
		cmd_S_T();
		nextState(STG_MONT);
	});}
	public void shutter(
		final boolean on_off,
		final Runnable event_done,
		final Runnable event_fail
	) {asyncBreakIn(()->{
		//U1 --> True: shutter open
		//U0 --> False: shutter close
		cmd_U(on_off,event_done,event_fail);
		nextState(STG_MONT);
	});}
	public void shutter_and_zeros(
		final boolean on_off,
		final Runnable event_done,
		final Runnable event_fail
	){asyncBreakIn(()->{		
		cmd_S_T();
		cmd_U(on_off,event_done,event_fail);
		nextState(STG_MONT);
	});}
	
	public void activeFilm(final int ID) {
		if(ID<0 || 100<ID) {
			PanBase.notifyError("錯誤的ID",String.format("非法的ID(%d), 1~99",ID));
			return;
		}
		asyncBreakIn(()->{
			final char id = (char)(ID+48);
			//read film data~~~
			final String a_txt = exec(String.format("A%c?",id));			
			//active film data~~~
			final String d_txt = exec(String.format("D%c",id));
			if(d_txt.charAt(0)=='A') {
				film_indx = ID;//update index~~~
				split_a_text(a_txt);				
				Application.invokeLater(()->filmName.set(get_film_name()));
			}else {
				Application.invokeLater(()->PanBase.notifyError("內部錯誤","無法啟動薄膜ID-"+ID));
			}
		});
	}
	public boolean activeFilm(
		final int ID,
		final String name,
		final float density,		
		final int tooling,
		final float z_ratio,		
		final float  sp_thick,
		final String sp_time,
		final int sensors
	) {
		if(ID<0 || 100<ID) {
			PanBase.notifyError("錯誤的ID",String.format("非法的ID(%d), 1~99",ID));
			return false;
		}
		if(density<0.5f || 99.99f<density) {
			PanBase.notifyError("Density",String.format("非法的Density, 0.5~99.99",density));
			return false;
		}
		if(tooling<10 || 399<tooling) {
			PanBase.notifyError("Tooling",String.format("非法的tooling, 10~399",tooling));
			return false;
		}
		if(z_ratio<0.1f || 9.999f<z_ratio) {
			PanBase.notifyError("Z-Ratio",String.format("非法的z_ratio, 0.1~9.999",z_ratio));
			return false;
		}
		if(sp_thick<0.000f || 9999.000f<sp_thick) {
			PanBase.notifyError("厚度",String.format("非法的厚度, 0.000~9999.000",sp_thick));
			return false;
		}
		if(sensors>=64) {
			PanBase.notifyError("Sensors",String.format("非法的Sensor it(%d), >=64",sensors));
			return false;
		}
		final String _name = name
			.toUpperCase()
			.replace(" ", "_")
			.concat("__________")			
			.substring(0, 8);
		
		final long s_time = Misc.text2tick(sp_time)/1000L;
		
		asyncBreakIn(()->{
			final String cmd =String.format(
				"A%c%s %.2f %d %.3f %.3f %.3f %d %d",
				(char)(ID+48),_name,
				density, tooling, z_ratio,
				sp_thick, sp_thick, s_time,
				sensors
			); 
			final String res = exec(cmd);
			if(res.charAt(0)!='A') {				
				Misc.logw("[%s] fail to active film", TAG);				
			}else {
				Misc.logv("[%s] update film(%d)", ID);
			}
		});
		return true;
	}
	
	public void updateFilm(final String cmd) {asyncBreakIn(()->{
		String a_txt = exec(cmd);
		final String resp = a_txt; 
		if(a_txt.charAt(0)!='A') {
			Application.invokeLater(()->PanBase.notifyError("內部錯誤","無法更新薄膜資料("+resp+")"));
		}else {
			a_txt = exec(String.format("A%c?",cmd.charAt(1)));
			film_indx = cmd.charAt(1) - 48;//update index~~~
			split_a_text(a_txt);
			Application.invokeLater(()->filmName.set(get_film_name()));
		}		
	});}	
	//-------------------------//

	public static Pane genInfoPanel(final DevSQM160 dev) {
		
		final Label rate = new Label();
		rate.textProperty().bind(dev.rate [0].concat(dev.unitRate));
		
		final Label thic = new Label();
		thic.textProperty().bind(dev.thick[0].concat(dev.unitThick));
		
		final Label film = new Label();
		film.textProperty().bind(dev.filmName);
		
		final Button btn_pick = new Button();
		btn_pick.setGraphic(Misc.getIconView("toc.png"));
		btn_pick.setOnAction(e->{
			PadTouch pad = new PadTouch('N',"薄膜編號:");
			Optional<String> opt = pad.showAndWait();
			if(opt.isPresent()==false) {
				return;
			}
			dev.activeFilm(Integer.valueOf(opt.get()));
		});
		
		final Button btn_edit = new Button();
		btn_edit.setGraphic(Misc.getIconView("wrench.png"));
		btn_edit.setOnAction(e->{
			final DialogFilm dia = new DialogFilm(dev.film_data);
			final Optional<String> opt = dia.showAndWait();
			if(opt.isPresent()==false) {
				return;
			}
			dev.updateFilm(opt.get());
		});
		
		final Label[] txt ={
			new Label("速率："), rate,
			new Label("厚度："), thic,
			new Label("薄膜："), film,
		};
		for(Label obj:txt) {
			obj.getStyleClass().addAll("font-size5");
			if(obj.textProperty().isBound()==true){
				obj.setMinWidth(67.);
				obj.setMaxWidth(Double.MAX_VALUE);
			}
			obj.setAlignment(Pos.CENTER_RIGHT);
			GridPane.setHgrow(obj, Priority.ALWAYS);
		}
		HBox.setHgrow(txt[5], Priority.ALWAYS);
		
		final GridPane lay0 = new GridPane();
		lay0.getStyleClass().addAll("box-pad");
		lay0.addRow(0, txt[0], txt[1]);
		lay0.addRow(1, txt[2] ,txt[3]);
		lay0.addRow(2, txt[4],new HBox(txt[5],btn_pick,btn_edit));
		return lay0;
	}
	
	public static Pane genCtrlPanel(final DevSQM160 dev) {
		
		final Label[] info = new Label[6];
		for(int i=0; i<info.length; i++) {
			info[i] = new Label();
			info[i].setMinWidth(80);
			info[i].getStyleClass().addAll("font-size4");
			GridPane.setHgrow(info[i], Priority.ALWAYS);
		}
		info[0].setText("速率："); info[1].setPrefWidth(200.); info[1].textProperty().bind(dev.rate [0].concat(dev.unitRate)); 
		info[2].setText("厚度："); info[3].setPrefWidth(140.); info[3].textProperty().bind(dev.thick[0].concat(dev.unitThick)); 
		info[4].setText("薄膜："); info[5].setPrefWidth(140.); info[5].textProperty().bind(dev.filmName);
		
		final Button btn_film_pick = new Button("選取");
		btn_film_pick.setFocusTraversable(false);
		btn_film_pick.setOnAction(e->{
			PadTouch pad = new PadTouch('N',"薄膜編號:");
			Optional<String> opt = pad.showAndWait();
			if(opt.isPresent()==false) {
				return;
			}
			dev.activeFilm(Integer.valueOf(opt.get()));
		});
		
		final JFXButton btn_film_data = new JFXButton("薄膜設定");
		btn_film_data.getStyleClass().add("btn-raised-1");
		btn_film_data.setMaxWidth(Double.MAX_VALUE);
		btn_film_data.setOnAction(e->{
			final DialogFilm dia = new DialogFilm(dev.film_data);
			final Optional<String> opt = dia.showAndWait();
			if(opt.isPresent()==false) {
				return;
			}
			dev.updateFilm(opt.get());
		});

		final JFXButton btn_zeros_all = new JFXButton("歸零鍍膜");
		btn_zeros_all.getStyleClass().add("btn-raised-1");
		btn_zeros_all.setMaxWidth(Double.MAX_VALUE);
		btn_zeros_all.setOnAction(e->dev.zeros());
		
		final JFXButton btn_shutter_on = new JFXButton("檔板開");
		btn_shutter_on.getStyleClass().add("btn-raised-2");
		btn_shutter_on.setMaxWidth(Double.MAX_VALUE);
		btn_shutter_on.setOnAction(e->dev.shutter(true,null,null));
		
		final JFXButton btn_shutter_off = new JFXButton("檔板關");
		btn_shutter_off.getStyleClass().add("btn-raised-0");
		btn_shutter_off.setMaxWidth(Double.MAX_VALUE);
		btn_shutter_off.setOnAction(e->dev.shutter(false,null,null));

		final ImageView img_sh1 =  Misc.getIconView("lock-open-outline.png");
		final ImageView img_sh2 =  Misc.getIconView("lock-outline.png");
		img_sh1.visibleProperty().bind(dev.shutter);
		img_sh1.visibleProperty().bind(dev.shutter.not());
		
		final GridPane lay = new GridPane();
		lay.getStyleClass().addAll("box-pad");
		lay.add(info[0], 0, 0, 1, 1);
		lay.add(info[1], 1, 0, 2, 1);
		lay.add(info[2], 0, 1, 1, 1);
		lay.add(info[3], 1, 1, 2, 1);		
		lay.addRow(2, info[4], info[5], btn_film_pick);
		lay.add(btn_film_data, 0, 3, 3, 1);
		lay.add(btn_zeros_all, 0, 4, 3, 1);
		lay.add(new StackPane(img_sh1,img_sh2), 0, 5, 1, 1);
		lay.add(new HBox(btn_shutter_on, btn_shutter_off), 1, 5, 2, 1);
		return lay;
	}
	//-------------------------//
	
	private static class DialogFilm extends Dialog<String>{
		public DialogFilm(final String[] param){
			
			final TextField arg_name = new TextField(param[0]);
			final TextField arg_density = new TextField(param[1]);//g/cm³
			final TextField arg_tooling = new TextField(param[2]);//%
			final TextField arg_z_ratio = new TextField(param[3]);//no unit~~~
			final TextField arg_thick_final = new TextField(param[4]);// kÅ
			final TextField arg_thick_setpoint = new TextField(param[5]);// kÅ
			
			final TextField arg_time_setpoint = new TextField(
				Misc.tick2text(Long.valueOf(param[6])*1000L)
			);// seconds
			final CheckBox[] arg_sensor_bit = {
				new CheckBox("1"), new CheckBox("2"), new CheckBox("3"),
				new CheckBox("4"), new CheckBox("5"), new CheckBox("6"),
			};
			final int bits = Integer.valueOf(param[7]);
			for(int i=0; i<arg_sensor_bit.length; i++) {
				if((bits & (1<<(i)))!=0) {
					arg_sensor_bit[i].setSelected(true);
				}else {
					arg_sensor_bit[i].setSelected(false);
				}
			}
			
			final GridPane lay = new GridPane();
			lay.getStyleClass().addAll("box-pad");
			lay.addRow(0, new Label("薄膜名稱："), arg_name);
			lay.addRow(1, new Label("薄膜密度(g/cm³)："), arg_density);
			lay.addRow(2, new Label("tooling(%)："), arg_tooling);
			lay.addRow(3, new Label("z-ratio："), arg_z_ratio);
			lay.addRow(4, new Label("最終厚度(kÅ)："), arg_thick_final);
			lay.addRow(5, new Label("厚度停止點(kÅ)："), arg_thick_setpoint);
			lay.addRow(6, new Label("時間停止點(mm:ss)："), arg_time_setpoint);
			lay.addRow(7, new Label("偵測器編號："), new HBox(arg_sensor_bit));
			
			final DialogPane pan = getDialogPane();			
			pan.getStylesheets().add(Gawain.sheet);
			pan.getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);
			pan.setContent(lay);
			
			//setTitle(String.format("薄膜編號"));
			setResultConverter(dia->{
				final ButtonData btn = (dia==null)?(null):(dia.getButtonData());
				if(btn!=ButtonData.OK_DONE) {
					return null;				
				}
				char[] name_buff = {
					'_', '_', '_', '_',
					'_', '_', '_', '_'
				};
				final String name_user = arg_name.getText().trim().toUpperCase();
				for(int i=0; i<name_buff.length; i++){
					if(i>=name_user.length()){
						break;
					}
					final char cc = name_user.charAt(i);
					if((48<=cc && cc<=56) || (65<=cc && cc<=90)) {
						name_buff[i] = cc;
					}					
				}
				int bit_sum = 0;
				for(int i=0; i<arg_sensor_bit.length; i++) {
					if(arg_sensor_bit[i].isSelected()==true) {
						bit_sum = bit_sum + (1<<i);
					}
				}
				//ID:0-->update current film data
				return String.format(
					"A%c%s %s %s %s %s %s %d %d",
					0+48, 
					new String(name_buff),
					arg_density.getText().trim(),
					arg_tooling.getText().trim(),
					arg_z_ratio.getText().trim(),
					arg_thick_final.getText().trim(),
					arg_thick_setpoint.getText().trim(),
					Misc.text2tick(arg_time_setpoint.getText().trim()) / 1000,
					bit_sum
				);
			});
		} 
	}
}
