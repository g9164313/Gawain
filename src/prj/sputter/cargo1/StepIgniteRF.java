package prj.sputter.cargo1;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import narl.itrc.Misc;

public class StepIgniteRF extends StepCommon {

	public static final String action_name = "RF 電漿";
	
	final TextField txt_rf = new TextField();
	final TextField txt_dc = new TextField();
	
	public StepIgniteRF() {
		txt_rf.setPrefWidth(100.);
		txt_dc.setPrefWidth(100.);
		chain(
			op1, work_waiting(1000,msg[1]),
			op2, work_waiting(1000,msg[1]),
			op3, work_waiting(1000,msg[1]), 
			op4, run_holding, op5
		);
	}

	final Runnable op1 = ()->{
		msg[1].setText("關閉檔板");		
		adam1.asyncSetAllLevel(
			false,
			chk_sh2.isSelected(), 
			chk_sh3.isSelected()
		);
		next();
	};
	final Runnable op2 = ()->{
		msg[1].setText("apply");
		sar2.apply_setpoint(
			Misc.txt2Float(txt_rf.getText()),
			Misc.txt2Float(txt_dc.getText())
		);
		next();
	};
	final Runnable op3 = ()->{
		msg[1].setText("Remote!!");		
		sar2.set_onoff(true);
		next();
	};
	final Runnable op4 = ()->{
		msg[1].setText("Fire!!");		
		sar2.set_RF_fire(true);
		next();
	};
	protected final Runnable op5 = ()->{
		msg[1].setText("cont-");		
		if(chk_cont.isSelected()==false) {
			msg[1].setText("");
			sar2.set_RF_fire(false);
		}
		jump();
	};
		
	@Override
	public Node getContent() {
		
		final Label inf1 = new Label();
		inf1.textProperty().bind(sar2.txt_forward.textProperty());
		
		final Label inf2 = new Label();
		inf2.textProperty().bind(sar2.txt_reflect.textProperty());
		
		return gen_grid_pane(
			action_name,"5:00",true,
			chk_sh2, chk_sh3, 
			null, null,
			new Label("RF輸出(W):"), new Label("DC偏壓(V):"), txt_rf, txt_dc,
			new Label("輸出(W):"), new Label("反射(W):"), inf1, inf2
		);
	}
	@Override
	public void eventEdit() {
	}
	@Override
	public String flatten() {
		return control2text(
			chk_sh2, chk_sh3, box_hold, chk_cont,
			txt_rf, txt_dc
		);
	}
	@Override
	public void expand(String txt) {
		text2control(txt,
			chk_sh2, chk_sh3, box_hold, chk_cont,
			txt_rf, txt_dc
		);
	}
}
