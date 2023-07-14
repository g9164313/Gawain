package prj.daemon;

import java.math.BigDecimal;

import java.util.Optional;

import com.sun.glass.ui.Application;

import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import narl.itrc.DevModbus;
import narl.itrc.PadChoise;
import narl.itrc.PadTouch;
import narl.itrc.PanDialog;

/**
 * PID controller for FA-TAIE.
 * Modbus 通訊位置，注意文件！！！，新舊版不同！！！
 * Refer to: https://www.fa-taie.com.tw/admin/download/file/2022-04-06/624cf2980735f.pdf
 * 操作使用手冊：
 * Refer to: https://www.fa-taie.com.tw/admin/download/file/2022-04-06/624ceff4604de.pdf
 */
public class DevFYx00 extends DevModbus {

	/**
	 * INP1:
	 *  0-->K1:-50.0~ 600.0 度C (K型電偶)
	 *  1-->K2:-50  ~1200.
	 *  2-->J1:-50.0~ 400.0 度C
	 *  3-->J2:-50  ~ 400. 
	 */
	public final IntegerProperty SV,SEG,TIMR,INP1,PV,LAP1;

	public final FloatProperty  pv_val= new SimpleFloatProperty();
	public final StringProperty pv_txt= new SimpleStringProperty("？？？？");
	public final StringProperty sv_txt= new SimpleStringProperty("？？？？");

	@Override
	protected void ignite_task(){
		writeRegVal(0x115, 1);//REMO: 開啟遠程控制
		Application.invokeLater(()->{
			LAP1.set(-1);
		});
	}

	@Override
	protected void looper_event(){
		final int inp1 = INP1.get();
		final int pv_v = PV.get();
		final int sv_v = SV.get();
		switch(inp1){		
		case 0:
		case 2:
		case 9:
		case 14:
			if(pv_v>=0x7FFF){
				pv_val.set(0.f);
				pv_txt.set("？？？？");
			}else{
				BigDecimal pp = BigDecimal.valueOf(pv_v);
				pp = pp.movePointLeft(1);			
				pv_val.set(pp.floatValue());
				pv_txt.set(pp.toString());
			}
			BigDecimal ss = BigDecimal.valueOf(sv_v);
			ss = ss.movePointLeft(1);
			sv_txt.set(ss.toString());
			break;
		default:
			if(pv_v>=0x7FFF){
				pv_val.set(0.f);
				pv_txt.set("？？？？");
			}else{
				pv_val.set(PV.floatValue());
				pv_txt.set(String.format("%4d", pv_v));
			}
			sv_txt.set(String.format("%4d", sv_v));
			break;
		}
	}

	public DevFYx00(){
		this("FYx00");
	}
	public DevFYx00(final String tag){
		TAG = tag;
		mapAddress(16,"h000","h007-008","h048","h08A","h408");
		SV  =mapShort(0x0000);//USPL~LSPL
		SEG =mapShort(0x0007);//Prog. segment
		TIMR=mapShort(0x0008);//Prog. timer left
		INP1=mapShort(0x0048);//輸入類型與解析度
		//OBIT=mapShort(0x0088);//狀態
		PV  =mapShort(0x008A);//USPL~LSPL
		LAP1=mapShort(0x0408);//狀態指示, PKE
	}

	private static void update_visible(final Node node, final int stas, final int bits){
		if((stas&(1<<bits))!=0){
			node.setVisible(true);
		}else{
			node.setVisible(false);
		}	
	}

	public static Pane genInfoPanel(final String title, final DevFYx00 dev){

		final Label txt_pv = new Label();
		final Label txt_sv = new Label();
		txt_pv.textProperty().bind(dev.pv_txt);
		txt_sv.textProperty().bind(dev.sv_txt);

		final Label[] txt_stas = {
			new Label("OUT1"), new Label("OUT2"), new Label("ATun"), 
			new Label("AL1 "), new Label("AL2 "), new Label("AL3 "), 
			new Label(), new Label(), new Label()
		};		
		dev.LAP1.addListener((obv,oldVal,newVal)->{
			final int stas = newVal.intValue();
			update_visible(txt_stas[0], stas, 0);
			update_visible(txt_stas[1], stas, 1);
			update_visible(txt_stas[2], stas, 2);
			update_visible(txt_stas[3], stas, 3);
			update_visible(txt_stas[4], stas, 4);
			update_visible(txt_stas[5], stas, 5);
			txt_stas[7].setVisible(false);
			txt_stas[8].setVisible(false);
			if((stas&(1<<6))!=0){
				txt_stas[6].setVisible(true);
				txt_stas[7].setVisible(true);
				txt_stas[8].setVisible(true);
				if((stas&(1<<8))!=0){
					txt_stas[6].setText("RUN ");
				}else if((stas&(1<<9))!=0){
					txt_stas[6].setText("END ");
				}else if((stas&(1<<10))!=0){
					txt_stas[6].setText("WALT");
				}else if((stas&(1<<12))!=0){
				}else{
					txt_stas[6].setText("HALT");
				}
			}else if((stas&(1<<7))!=0){
				txt_stas[6].setVisible(true);
				txt_stas[6].setText("MAN ");
			}else{
				txt_stas[6].setVisible(false);
			}
		});
		txt_stas[7].textProperty().bind(dev.SEG.asString("SEG%1d"));
		txt_stas[8].textProperty().bind(dev.TIMR.asString("%04d"));


		final Button btn_setv = new Button("指定 SV");
		btn_setv.setMaxWidth(Double.MAX_VALUE);
		btn_setv.setOnAction(act->{
			final PadTouch pad = new PadTouch('f',"SV數值");
			Optional<String> opt = pad.showAndWait();			
			if(opt.isPresent()==false) {
				return;
			}
			final String txt = opt.get();
			final int inp1 = dev.INP1.get();
			dev.asyncBreakIn(()->{
				BigDecimal val = new BigDecimal(txt);
				switch(inp1){		
					case 0:
					case 2:
					case 9:
					case 14:
						val = val.movePointRight(1);// multify 10
						dev.writeForce(0x000,val.shortValue());//SV
						break;
					default:
						dev.writeForce(0x000,val.shortValue());//SV
						break;
					}
			});
		});
		GridPane.setHgrow(btn_setv, Priority.ALWAYS);

		final Button btn_prog = new Button("程式");
		btn_prog.setMaxWidth(Double.MAX_VALUE);
		btn_prog.setOnAction(act->{
			final short[] regs = new short[2*8*3];
			dev.asyncBreakIn(()->{
				//slow reading~~~ why???
				short[] buf = new short[3];
				for(int i=0; i<regs.length; i+=3){
					dev.readRegVal('I', 0x09+i, buf);
					System.arraycopy(buf, 0, regs, i, 3);	
				}				
			}, ()->{
				PanProgForm pan = new PanProgForm(regs);
				Optional<Integer> opt = pan.showAndWait();
				if(opt.isPresent()==false){
					return;
				}
				dev.asyncBreakIn(()->{
					//don't dump all array once, why???
					short[] buf = new short[3];
					for(int i=0; i<regs.length; i+=3){
						System.arraycopy(pan.regs, i, buf, 0, 3);	
						dev.writeForce(0x09+i, buf);
					}
				});
			});			
		});
		HBox.setHgrow(btn_prog, Priority.ALWAYS);

		final Button btn_actn = new Button("動作");		
		btn_actn.setMaxWidth(Double.MAX_VALUE);		
		btn_actn.setOnAction(act->{
			final String[] lst_cmd = {"啟動-RUN","暫停-HALT","下一組-JUMP", "停止-Reset"};
			PadChoise<String> pad = new PadChoise<String>(lst_cmd);
			Optional<String> opt = pad.showAndWait();
			if(opt.isPresent()==false){
				return;
			}
			final String cmd = opt.get();
			dev.asyncBreakIn(()->{
				if(cmd.equals(lst_cmd[0])==true){
					dev.writeForce(0x409, (1<<8));
				}else if(cmd.equals(lst_cmd[1])==true){
					dev.writeForce(0x409, (1<<9));
				}else if(cmd.equals(lst_cmd[2])==true){
					dev.writeForce(0x409, (1<<10));
				}else if(cmd.equals(lst_cmd[3])==true){
					dev.writeForce(0x409, (1<<11));
				}
			});
		});
		HBox.setHgrow(btn_actn, Priority.ALWAYS);

		final Button btn_edit = new Button("edit.");
		btn_edit.setMaxWidth(Double.MAX_VALUE);
		btn_edit.setOnAction(act->dev.pop_editor());
		GridPane.setHgrow(btn_edit, Priority.ALWAYS);

		final GridPane lay = new GridPane();
		lay.getStyleClass().addAll("box-pad");
		lay.add(new Label(title), 0, 0, 2, 1);
		lay.addRow(1, new Label("PV："), txt_pv);
		lay.addRow(2, new Label("SV："), txt_sv);		
		lay.add(new Separator(), 0, 3, 2, 1);
		lay.add(btn_setv, 0, 4, 2, 1);
		lay.add(new HBox(btn_prog,btn_actn), 0, 5, 2, 1);
		lay.add(btn_edit, 0, 6, 2, 1);
		
		lay.add(new VBox(
			txt_stas[0], 
			txt_stas[1], 
			txt_stas[2], 
			txt_stas[3],
			txt_stas[4],
			txt_stas[5],
			txt_stas[6],
			txt_stas[7],
			txt_stas[8]
		), 3, 0, 1, 8);

		return lay;
	}
	//--------virtual program--------//

	private static class PanProgForm extends PanDialog<Integer>{
		TextField[][][] box;
		public short[] regs = null;	
		private void gen_box(){
			box = new TextField[2][][];//channel
			for(int i=0; i<2; i++){
				box[i] = new TextField[8][];//segment
				for(int j=0; j<8; j++){
					box[i][j] = new TextField[3];//SV, TM, OUT
					for(int k=0; k<3; k++){
						TextField obj = new TextField();
						obj.setPrefWidth(80.);
						box[i][j][k] = obj;
					}
				}
			}
		}
		private void reg2box(short[] val){
			for(int i=0; i<2; i++){
				for(int j=0; j<8; j++){
					for(int k=0; k<3; k++){
						int v = (int)(val[i*24+j*3+k]);
						box[i][j][k].setText(String.valueOf(v));
					}
				}
			}
		}
		private void box2reg(){
			regs = new short[2*8*3];
			for(int i=0; i<2; i++){
				for(int j=0; j<8; j++){
					for(int k=0; k<3; k++){						
						final String txt = box[i][j][k].getText();
						try{
							regs[i*24+j*3+k] = Short.valueOf(txt);
						}catch(NumberFormatException e){							
						}
					}
				}
			}
		}
		PanProgForm(short[] val){
			gen_box();
			reg2box(val);
			final GridPane lay = new GridPane();
			lay.getStyleClass().addAll("box-pad");
			lay.addRow( 0, 
				new Label("**"), 
				new Label("Ch.1"), new Label(""),  new Label(""),
				new Label("Ch.2"), new Label(""),  new Label("")
			);
			lay.addRow( 1, 
				new Label("||"), 
				new Label("SV"),new Label("TM"),new Label("OUT"),
				new Label("SV"),new Label("TM"),new Label("OUT")
			);
			for(int j=0; j<8; j++){
				lay.addRow( j+2, 
					new Label(String.format("%d", j+1)), 
					box[0][j][0], box[0][j][1], box[0][j][2],
					box[1][j][0], box[1][j][1], box[1][j][2]
				);
			}
			init(lay);
		}
		@Override
		protected boolean set_result_and_close(ButtonType type) {
			if(type.equals(ButtonType.OK)) {
				box2reg();
				setResult(box.length);
				return true;
			}else if(type.equals(ButtonType.CANCEL)){
				setResult(null);
				return true;
			}	
			return false;
		}
	};
	//------------------------------------

	private static Object[][] MAP_INP1 = {
		{0x00, "K1",}, {0x01, "K2",}, {0x02, "K3",}, {0x03, "K4",}, {0x04, "K5",}, {0x05, "K6",},
		{0x06, "J1",}, {0x07, "J2",}, {0x08, "J3",}, {0x09, "J4",}, {0x0A, "J5",}, {0x0B, "J6",},
		{0x0C, "R1",}, {0x0D, "R2",}, 
		{0x0E, "S1",}, {0x0F, "S2",}, 
		{0x10, "B1",},
		{0x11, "E1",}, {0x11, "E2",},
		{0x13, "N1",}, {0x14, "N2",},
		{0x15, "T1",}, {0x16, "T2",}, {0x17, "T3",},
	};
}
