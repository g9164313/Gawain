package prj.sputter.labor1;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import narl.itrc.Misc;

public class StepWatcher extends StepCommon {
	
	public final static String ACT_NAME = "厚度監控";
	public final static String TAG_WATCH= "監控";
	
	public StepWatcher(){
		chain(
			shutter_open,
			op_3,
			turn_off,
			turn_off_wait,
			op_6
		);
	}
	
	private final Label inf_rate= new Label("");
	private final Label inf_avg = new Label("");
	private final Label inf_dev = new Label("");
	
	private final TextField[] box_args = {
		new TextField(""),new TextField("400"),new TextField("200"),
		new TextField("3"),new TextField("0.3"),new TextField(""),
	};
	final TextField box_goal = box_args[0];
	final TextField box_maxw = box_args[1];
	final TextField box_minw = box_args[2];
	final TextField box_prop = box_args[3];//proportional
	final TextField box_inte = box_args[4];//integral
	final TextField box_deri = box_args[5];//derivative
	
	private DescriptiveStatistics stats = new DescriptiveStatistics(30);
	
	long tick_beg = -1L, tick_end = -1L;

	final void PID_feedback() {
		int gain,maxw,minw;
		float goal,thres;
		try {
			gain = Integer.valueOf(box_prop.getText().trim());
			maxw = Integer.valueOf(box_maxw.getText().trim());
			minw = Integer.valueOf(box_minw.getText().trim());
			goal = Float.valueOf(box_goal.getText().trim());			
			int filter_size = Integer.valueOf(box_deri.getText().trim());
			if(filter_size!=stats.getWindowSize()) {
				stats.setWindowSize(filter_size);
				stats.clear();
				return;
			}			
			thres = Float.valueOf(box_inte.getText().trim());
		}catch(NumberFormatException e) {
			return;
		}		
		int n_size = (int)stats.getN();
		float delta = 0f; 
		for(int i=0; i<n_size; i++) {
			delta = delta + Math.abs(((float)stats.getElement(i)-goal));
		}
		delta = delta / ((float)n_size);
		if(delta<=thres) {
			return;
		}
		if(stats.getMean()>goal) {
			gain = -1*gain;
		}
		dcg1.asyncAdjustWatt(gain,minw,maxw);
	}
	
	final Runnable op_3 = ()->{
		//monitor shutter
		if(tick_beg<=0L){
			tick_beg = System.currentTimeMillis();
		}
		tick_end = System.currentTimeMillis();
		show_mesg(
			ACT_NAME,
			Misc.tick2text(tick_end-tick_beg,true),
			sqm1.getTextThick()
		);
		
		//final float rate_value= sqm1.rate[0].get();
		//final String rate_unit= sqm1.unitRate.get();
		
		stats.addValue(sqm1.meanRate.get());		
		inf_rate.setText(sqm1.getTextRate());
		inf_avg.setText(String.format("%5.3f", stats.getMean()));
		
		double sigma = stats.getVariance();
		if(sigma==Double.NaN) {
			inf_dev.setText("-----");
		}else {
			inf_dev.setText(String.format("%5.3f", sigma));
		}
		print_info(TAG_WATCH);
				
		if(sqm1.shutter.get()==false){
			trig(this.turn_off);
		}else{
			PID_feedback();
			trig(this.op_3);
		}
	};
	
	final Runnable op_6 = ()->{
		final String time = Misc.tick2text(tick_end-tick_beg,true);
		show_mesg(
			ACT_NAME,
			time,
			sqm1.getTextThick()
		);
		tick_beg = -1L;//for next turn~~~
		Misc.logv(
			"%s: %s [%s][%s]", 
			ACT_NAME, 
			"Stepper.LAST_STICKER",
			time,
			sqm1.getTextThick() 			
		);
	};
	
	@Override
	public Node getContent(){
		
		show_mesg(ACT_NAME);
		
		inf_rate.setPrefWidth(83);
		inf_avg.setPrefWidth(83);
		inf_dev.setPrefWidth(83);		
		for(TextField obj:box_args) {
			obj.setPrefWidth(83);
		}

		box_deri.setText(""+stats.getWindowSize());
		
		GridPane lay = new GridPane();
		lay.getStyleClass().addAll("box-pad");
		lay.addColumn(0, msg[0],msg[1],msg[2]);
		lay.add(new Separator(Orientation.VERTICAL), 1, 0, 1, 3);
		lay.addColumn(2,new Label("成長速率"),new Label("統計值"),new Label("標準差"));
		lay.addColumn(3,inf_rate,inf_avg,inf_dev);
		lay.add(new Separator(Orientation.VERTICAL), 4, 0, 1, 3);
		lay.addColumn(5,new Label("目標速率"),new Label("最大功率"),new Label("最小功率"));
		lay.addColumn(6,box_goal,box_maxw,box_minw);
		lay.addColumn(7,new Label("P"),new Label("I"),new Label("D"));
		lay.addColumn(8,box_prop,box_inte,box_deri);
		return lay;
	}
	@Override
	public void eventEdit() {
	}
	
	private static final String TAG0 = "ATTR-VAL";
	private static final String TAG1 = "ATTR-MIN";
	private static final String TAG2 = "ATTR-MAX";
	private static final String TAG3 = "PID-P";
	private static final String TAG4 = "PID-I";
	private static final String TAG5 = "PID-IT";
	
	@Override
	public String flatten() {
		return String.format(
			"%s:%s, %s:%s, %s:%s, %s:%s, %s:%s, %s:%s",
			TAG0, box_goal.getText().trim(),
			TAG1, box_minw.getText().trim(),
			TAG2, box_maxw.getText().trim(),
			TAG3, box_prop.getText().trim(),
			TAG4, box_inte.getText().trim(),
			TAG5, box_deri.getText().trim()
		);
	}
	@Override
	public void expand(String txt) {
		if(txt.matches("([^:,\\p{Space}]+[:]\\p{ASCII}*[,\\s]?)+")==false){
			Misc.loge("pasing fail-->%s",txt);
			return;
		}
		//trick, replace time format.
		//EX: mm#ss --> mm:ss
		String[] col = txt.split(":|,");
		for(int i=0; i<col.length; i+=2){
			final String tag = col[i+0].trim();
			final String val = col[i+1].trim();
			if(tag.equals(TAG0)==true){
				box_goal.setText(val);
			}else if(tag.equals(TAG1)==true){
				box_minw.setText(val);
			}else if(tag.equals(TAG2)==true){
				box_maxw.setText(val);
			}else if(tag.equals(TAG3)==true){
				box_prop.setText(val);
			}else if(tag.equals(TAG4)==true){
				box_inte.setText(val);
			}else if(tag.equals(TAG5)==true){
				box_deri.setText(val);
			}
		}
	}
}
