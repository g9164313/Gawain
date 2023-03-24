package prj.sputter.cargo1;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import narl.itrc.Misc;

public class StepWaiting extends StepCommon {

	public static final String action_name = "等待氣壓";
	
	final TextField txt_press = new TextField("2E-6");
	
	public StepWaiting() {
		chain(op1,op2);
	}
	
	long tick = 0;
	final Runnable op1 = ()->{
		msg[0].setText("確認氣壓");		
		tick = System.currentTimeMillis();
		next();
	};
	final Runnable op2 = ()->{		
		final float aa = PanMain.all_press.get();
		final float bb = Float.parseFloat(txt_press.getText());
		if(aa<=bb) {
			msg[0].setText("氣壓確認!!");
			msg[1].setText(String.format("%.2E", aa));
			jump();
			return;
		}
		msg[0].setText("等待中...");
		msg[1].setText(Misc.tick2text(
			System.currentTimeMillis()-tick, 
			true
		));
		trig(this.op2);
	};
	
	@Override
	public Node getContent() {
		
		Label t_gauge = new Label();
		t_gauge.textProperty().bind(PanMain.all_press.asString("%.2E"));
		
		GridPane lay = new GridPane();
		lay.getStyleClass().addAll("box-pad");
		lay.addColumn(0,msg);
		lay.add(new Separator(Orientation.VERTICAL), 1, 0, 1, 2);
		lay.addRow(0, new Label("氣壓高於"), txt_press, new Label("Torr，保持待命狀態"));
		lay.addRow(1, new Label("腔體氣壓"), t_gauge, new Label("Torr"));		
		return lay;
	}
	@Override
	public void eventEdit() {
	}
	@Override
	public String flatten() {
		return control2text(txt_press);
	}
	@Override
	public void expand(String txt) {
		text2control(txt,txt_press);
	}
}
