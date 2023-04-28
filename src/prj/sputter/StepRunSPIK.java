package prj.sputter;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import narl.itrc.Misc;
import narl.itrc.Stepper;

public class StepRunSPIK extends Stepper {
	
	public static final String action_name = "SPIK點火";
	
	DevSPIK2k dev;
	final Label[] msg = {new Label(), new Label() };
	
	final CheckBox  dc_1 = new CheckBox("DC1");
	final TextField vol_1 = new TextField();
	final TextField amp_1 = new TextField();
	final TextField pow_1 = new TextField();
	
	final CheckBox  dc_2 = new CheckBox("DC2");
	final TextField vol_2 = new TextField();
	final TextField amp_2 = new TextField();
	final TextField pow_2 = new TextField();
	
	//final TextField ramp = new TextField("30");
	final TextField hold = new TextField("30");
	final CheckBox  cont = new CheckBox("連續");
	
	public StepRunSPIK(final DevSPIK2k device) {
		dev = device;
		chain(
			op1, work_waiting(3000,msg[1]),
			op2_1, work_waiting(3000,msg[1]), op2_2,
			op3_1, work_waiting(3000,msg[1]), 
			op3_2, work_waiting(3000,msg[1]),
			op4_1, work_waiting(3000,msg[1]), op4_2,
			work_waiting(Misc.text2tick(hold.getText()),msg[1]), op_end
		);
	}
	
	final Runnable op1 = ()->{
		msg[0].setText("APPLY~");
		msg[1].setText("V.I.P!!");
		
		dev.set_DC_value(null, '1', 'V', Misc.txt2Float(vol_1.getText()));
		dev.set_DC_value(null, '1', 'I', Misc.txt2Float(amp_1.getText()));
		dev.set_DC_value(null, '1', 'P', Misc.txt2Float(pow_1.getText()));
		dev.set_DC_value(null, '2', 'V', Misc.txt2Float(vol_2.getText()));
		dev.set_DC_value(null, '2', 'I', Misc.txt2Float(amp_2.getText()));
		dev.set_DC_value(null, '2', 'P', Misc.txt2Float(pow_2.getText()));
		
		next();
	};
	
	final Runnable op2_1 = ()->{
		msg[0].setText("-RUN-");
		msg[1].setText("");
		if(dev.Run.get()==false) {
			dev.toggleRun(null,true);
		}
		next();
	};
	final Runnable op2_2 = ()->{
		if(dev.Run.get()==true) {
			msg[1].setText("On!!");
			next();
		}else {
			msg[1].setText("wait");
			trig(this.op2_2);
		}
	};
	
	long tick_diff;
	int  power_set;
	final Runnable op3_1 = ()->{
		tick_diff = System.currentTimeMillis();
		power_set = dev.DC1_P_Set.get();
		msg[0].setText("-DC1-");
		msg[1].setText("");		
		dev.toggleDC1(null,dc_1.isSelected());
		next();
	};
	final Runnable op3_2 = ()->{
		if(dev.DC1.get()==dc_1.isSelected()) {
			next();//TODO: DC1_P_Act no update!!!!		
		}else {
			msg[1].setText("wait~~");
			trig(this.op3_2);
		}
	};
	
	final Runnable op4_1 = ()->{
		msg[0].setText("DC2");
		msg[1].setText("");
		next();
		dev.toggleDC2(null,dc_2.isSelected());	
	};
	
	final Runnable op4_2 = ()->{
		if(dev.DC2.get()==dc_2.isSelected()) {
			msg[1].setText("check!!");
			next();
		}else {
			msg[1].setText("wait~~");
			trig(this.op4_2);
		}		
	};
		
	final Runnable op_end = ()->{
		msg[0].setText(action_name);
		if(cont.isSelected()==false) {
			msg[1].setText("OFF");
			if(dev.DC1.get()==true) {
				dev.toggleDC1(null,false);
			}
			if(dev.DC2.get()==true) {
				dev.toggleDC2(null,false);
			}
		}		
		jump();
	};
	
	@Override
	public Node getContent() {
		msg[0].setText(action_name);
		msg[0].setMinWidth(100.);		
		msg[1].setMinWidth(100.);
		
		cont.setSelected(true);
		
		for(Control obj:new Control[] {
			dc_1,vol_1,amp_1,pow_1,
			dc_2,vol_2,amp_2,pow_2,
			hold
		}) {
			obj.setPrefWidth(87.);
		}
		
		GridPane lay = new GridPane();
		lay.getStyleClass().addAll("box-pad");
		lay.addColumn(0,msg);
		lay.add(new Separator(Orientation.VERTICAL), 1, 0, 1, 2);
		lay.addRow(0, 
			dc_1, new Label("V:"), vol_1, new Label("I:"), amp_1, new Label("P:"), pow_1
		);
		lay.addRow(1, 
			dc_2, new Label("V:"), vol_2, new Label("I:"), amp_2, new Label("P:"), pow_2
		);
		lay.add(new Separator(Orientation.VERTICAL), 9, 0, 1, 2);
		lay.addRow(0, new Label("維持時間:"), hold, cont);
		return lay;
	}
	@Override
	public void eventEdit() {
	}

	@Override
	public String flatten() {
		return control2text(
			dc_1,vol_1,amp_1,pow_1,
			dc_2,vol_2,amp_2,pow_2,
			hold,cont
		);
	}
	@Override
	public void expand(String txt) {
		text2control(txt,
			dc_1,vol_1,amp_1,pow_1,
			dc_2,vol_2,amp_2,pow_2,
			hold,cont
		);
	}
}
