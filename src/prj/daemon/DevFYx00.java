package prj.daemon;

import java.math.BigDecimal;
import java.util.Optional;

import com.sun.glass.ui.Application;

import javafx.beans.binding.FloatBinding;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import narl.itrc.DevModbus;
import narl.itrc.Gawain;
import narl.itrc.PadTouch;
import narl.itrc.PanBase;
import narl.itrc.PanDialog;

/**
 * PID controller for FA-TAIE.
 * Refer to: https://www.fa-taie.com.tw/admin/download/file/2014-12-31/54a3b0464a601.pdf
 */
public class DevFYx00 extends DevModbus {

	public final IntegerProperty SV,OUT_P,OBIT,CT,PV,DP,UNIT;
	public IntegerProperty SEG,TIMR;

	public final FloatProperty  pv_val= new SimpleFloatProperty();
	public final StringProperty pv_txt= new SimpleStringProperty("？？？？");
	public final StringProperty sv_txt= new SimpleStringProperty("？？？？");

	public final StringProperty INP1 = new SimpleStringProperty("？？？？");//輸入1 接的電偶級種類

	@Override
	protected void ignite_task(){
		final int inp1 = readRegVal('I',0x0048);
		Application.invokeLater(()->{

		});
	}

	@Override
	protected void looper_event(){
		String unit = "?";
		switch(UNIT.get()){
		case 0: unit="C"; break;
		case 1: unit="F"; break;
		case 2: unit="A"; break;
		}
		if(PV.get()!=0x7FFF){
			BigDecimal pp = BigDecimal.valueOf(PV.get());
			pp = pp.movePointLeft(DP.get());
			pv_val.set(pp.floatValue());
			pv_txt.set(pp.toString()+" "+unit);
		}else{
			pv_txt.set("!!ERROR!!");
		}		
		BigDecimal ss = BigDecimal.valueOf(SV.get());
		ss = ss.movePointLeft(DP.get());
		sv_txt.set(ss.toString()+" "+unit);
	}

	public DevFYx00(){
		this("FYx00");
	}
	public DevFYx00(final String tag){
		TAG = tag;
		mapAddress(16,"h0000","h004B","h0066","h0087-008A");
		SV   = mapShort(0x0000);//-999~9999		
		//SEG  = mapShort(0x0007);
		//TIMR = mapShort(0x0008);
		DP   = mapShort(0x004B);//decimal point: 0->none, 1->.0, 2->.00, 3->.000
		UNIT = mapShort(0x0066);//unit: 0-->C, 1-->F, 2-->A(角度？)
		OUT_P= mapShort(0x0087);//0~1000, 輸出百分比，OUT%
		OBIT = mapShort(0x0088);//狀態指示
		CT   = mapShort(0x0089);//0~999
		PV   = mapShort(0x008A);//-999~9999
	}

	public FloatBinding prop_num_SV(){
		return SV.divide(10f);
	}
	public FloatBinding prop_num_PV(){
		return PV.divide(10f);
	}

	public static Pane genInfoPanel(final String title, final DevFYx00 dev){

		final Label txt_pv = new Label();
		final Label txt_sv = new Label();

		//GridPane.setHgrow(txt[1], Priority.ALWAYS);

		txt_pv.textProperty().bind(dev.pv_txt);
		txt_sv.textProperty().bind(dev.sv_txt);

		final Button btn_setv = new Button("set value");
		btn_setv.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(btn_setv, Priority.ALWAYS);
		btn_setv.setOnAction(act->{
			final PadTouch pad = new PadTouch('f',"SV數值");
			Optional<String> opt = pad.showAndWait();			
			if(opt.isPresent()==false) {
				return;
			}
			final String txt = opt.get();
			final int org_DP = dev.DP.get();
			dev.asyncBreakIn(()->{
				BigDecimal val = new BigDecimal(txt);
				int cur_DP = val.scale();
				int shift = 0;
				if(org_DP>cur_DP){
					shift = val.movePointRight(org_DP-cur_DP).intValue();
				}else if(org_DP<=cur_DP){
					shift = val.movePointRight(org_DP).intValue();
				}
				dev.writeRegVal(0x0000, shift);
				//!!!We can't change DP register!!!
				//dev.writeRegPair(
				//	0x0000,shift,
				//	0x004B,cur_DP
				//);
			});
		});

		final Button btn_edit = new Button("edit.");
		btn_edit.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(btn_edit, Priority.ALWAYS);
		btn_edit.setOnAction(act->dev.pop_editor());
		
		final GridPane lay = new GridPane();
		lay.setMinWidth(178.);
		lay.getStyleClass().addAll("box-pad","font-size4");
		lay.add(new Label(title), 0, 0, 2, 1);
		lay.addRow(1, new Label("PV："), txt_pv);
		lay.addRow(2, new Label("SV："), txt_sv);
		lay.add(new Separator(), 0, 3, 2, 1);
		lay.add(btn_setv, 0, 6, 2, 1);
		lay.add(btn_edit, 0, 7, 2, 1);
		return lay;
	}
	//--------virtual program--------//

	/*private static class PanProgForm extends PanDialog<Integer>{
		private void gen_box(){
			box = new TextField[2][][];
		}
		private void box2reg(){
		}
		PanProgForm(short[][][] args){
			reg=args;
			gen_box();
			final GridPane lay = new GridPane();
			lay.getStyleClass().addAll("box-pad","font-size4");
			lay.addRow( 0, new Label("\\"  ), new Label("Ch.1"), new Label("")    , new Label("Ch.2"));
			lay.addRow( 1, new Label("SV_1"), box[0][0][0]     , new Label("SV_1"), box[1][0][0]     );
			lay.addRow( 2, new Label("TM_1"), box[0][0][1]     , new Label("TM_1"), box[1][0][1]     );
			lay.addRow( 3, new Label("SV_2"), box[0][1][0]     , new Label("SV_2"), box[1][1][0]     );
			lay.addRow( 4, new Label("TM_2"), box[0][1][1]     , new Label("TM_2"), box[1][1][1]     );
			lay.addRow( 5, new Label("SV_3"), box[0][2][0]     , new Label("SV_3"), box[1][2][0]     );
			lay.addRow( 6, new Label("TM_3"), box[0][2][1]     , new Label("TM_3"), box[1][2][1]     );
			lay.addRow( 7, new Label("SV_4"), box[0][3][0]     , new Label("SV_4"), box[1][3][0]     );
			lay.addRow( 8, new Label("TM_4"), box[0][3][1]     , new Label("TM_4"), box[1][3][1]     );
			lay.addRow( 9, new Label("SV_5"), box[0][4][0]     , new Label("SV_5"), box[1][4][0]     );
			lay.addRow(10, new Label("TM_5"), box[0][4][1]     , new Label("TM_5"), box[1][4][1]     );
			lay.addRow(11, new Label("SV_6"), box[0][5][0]     , new Label("SV_6"), box[1][5][0]     );
			lay.addRow(12, new Label("TM_6"), box[0][5][1]     , new Label("TM_6"), box[1][5][1]     );
			lay.addRow(13, new Label("SV_7"), box[0][6][0]     , new Label("SV_7"), box[1][6][0]     );
			lay.addRow(14, new Label("TM_7"), box[0][6][1]     , new Label("TM_7"), box[1][6][1]     );
			lay.addRow(15, new Label("SV_8"), box[0][7][0]     , new Label("SV_8"), box[1][7][0]     );
			lay.addRow(16, new Label("TM_8"), box[0][7][1]     , new Label("TM_8"), box[1][7][1]     );
			init(lay);
		}
		@Override
		protected boolean set_result_and_close(ButtonType type) {
			if(type.equals(ButtonType.OK)) {
				box2reg();			
				setResult(box.length);
				return true;
			}else if(type.equals(ButtonType.CANCEL)){
				setResult(0);
				return true;
			}	
			return false;
		}
	};*/
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
