package prj.sputter.labor1;

import com.sun.glass.ui.Application;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import narl.itrc.Misc;
import narl.itrc.PanBase;
import narl.itrc.Stepper;
import prj.sputter.DevCouple;
import prj.sputter.DevDCG100;
import prj.sputter.DevSPIK2k;
import prj.sputter.DevSQM160;

public abstract class StepCommon extends Stepper {

	public static DevCouple coup;
	public static DevDCG100 dcg1;	
	public static DevSPIK2k spik;
	public static DevSQM160 sqm1;
	public static LayLogger logg;
	
	protected Label[] msg = {
		new Label(), new Label(), new Label(), new Label(),
	};
	
	public StepCommon(){
		for(Label obj:msg){
			obj.setPrefWidth(150.);
		}
	}
	
	protected void show_mesg(final String... txt) {		
		for(int i=0; i<msg.length; i++) {			
			if(i>=txt.length) {
				msg[i].setText("");
			}else {
				if(txt[i]==null) {
					continue;
				}
				msg[i].setText(txt[i]);
			}
		}		
	}
	
	protected void print_mesg(final String... txt) {
		show_mesg(txt);
		String inf = "["+txt[0]+"],";
		for(int i=1; i<txt.length; i++) {
			if(txt[i].length()==0) {
				continue;
			}
			inf = inf + txt[i] + ",";
		}
		Misc.logv(inf);
	}
	
	protected void print_info(final String TAG) {
		
		final float volt = dcg1.volt.get();		
		final float amps = dcg1.amps.get();				
		final int   watt = (int)dcg1.watt.get();
		
		final float rate = sqm1.meanRate.get();
		final String unit1 = sqm1.unitRate.get();
		
		final float high = sqm1.meanThick.get();
		final String unit2 = sqm1.unitThick.get();
		
		final float mfc1 = coup.PV_FlowAr.get();
		final float mfc2 = coup.PV_FlowN2.get();
		final float mfc3 = coup.PV_FlowO2.get();
		
		Misc.logv(
			"%s: %.3f V, %.3f A, %d W, "+
			"%.3f sccm, %.3f sccm, %.3f sccm, "+
			"%.3f %s, %.3f %s",
			TAG, 
			volt, amps, watt,
			mfc1, mfc2, mfc3,
			rate, unit1, high, unit2
		);
	}
	//-------------------------------//
	
	protected final Runnable shutter_close = ()->{		
		final String tag = "關閉擋板";
		show_mesg(tag);
		wait_breakin_hook(sqm1,()->{
			sqm1.shutter(false,()->{
				Misc.logv(tag);
			}, ()->{
				Misc.logv(tag+"失敗");
				abort();
				final Alert dia = new Alert(AlertType.WARNING);
				dia.setTitle("！！警告！！");
				dia.setHeaderText("無法"+tag);
				dia.showAndWait();
			});
		});
	};
	
	protected final Runnable shutter_open = ()->{		
		final String tag = "開啟擋板";
		show_mesg(tag);
		wait_breakin_hook(sqm1,()->{
			sqm1.shutter_and_zeros(true,()->{
				Misc.logv(tag);
			}, ()->{
				Misc.logv(tag+"失敗");
				abort();
				
				final Alert dia = new Alert(AlertType.WARNING);
				dia.setTitle("！！警告！！");
				dia.setHeaderText("無法"+tag);
				dia.showAndWait();
			});
		});
	};
	
	protected int t_on_pos = -1;
	protected int t_off_pos= -1;
	protected int t_on_neg = -1;
	protected int t_off_neg= -1;
	
	protected final Runnable spik_get_pulse = ()->{
		final String tag = "設定脈衝";
		show_mesg(tag);
		wait_breakin_hook(spik,()->{
			spik.asyncGetRegister(pay->{
				t_on_pos = pay.getValue(0);
				t_off_pos= pay.getValue(1);
				t_on_neg = pay.getValue(2);
				t_off_neg= pay.getValue(3);
			}, 4, 4);
		});
	};
	
	protected final Runnable spik_apply_pulse = ()->{
		final String tag = "設定脈衝";
		show_mesg(tag);
		wait_breakin_hook(spik,()->{
			spik.setPulseValue(tkn->{ Misc.logv(tag+":setPulseValue:"); }, 
				t_on_pos, t_off_pos, 
				t_on_neg, t_off_neg
			);
		});
	};
	
	protected final Runnable spik_running = ()->{		
		if(spik.Run.get()==false){
			show_mesg("啟動 H-Pin");
			spik.toggleRun(null,true);
		}else{
			show_mesg("H-Pin 工作中");
		}
	};
	
	protected int dcg_power = -1;
	protected int dcg_t_rise  = 5000;//5 sec
	protected int dcg_t_stable= 60000*3;//3 min
	
	protected final Runnable turn_on = ()->{
		final String tag = "啟動 DCG";		
		show_mesg(tag);
		wait_breakin(dcg1,()->{
			dcg1.exec("CHL=W");
			dcg1.exec("SPW="+dcg_power);
			dcg1.exec("SPR="+dcg_t_rise);//unit is millisecond
			dcg1.exec("TRG");
		});
	};
	protected final Runnable turn_on_dummy = ()->{
		next();
	};
	
	
	protected Runnable hook_turn_on_wait = ()->{
		final int vv = dcg1.volt.getValue().intValue();
		final int aa = spik.ARC_count.get();
		if(vv<700 && aa<50) {
			return;
		}
		dcg1.asyncExec("OFF");
		abort();
		Application.invokeLater(()->{
			final Alert dia = new Alert(AlertType.WARNING);
			dia.setTitle("！！警告！！");
			dia.setHeaderText("點火失敗");
			dia.showAndWait();
		});
	};
	
	protected final Runnable turn_on_wait = new work_period(
		msg[3],
		1000,
		dcg_t_rise+dcg_t_stable
	) {
		@Override
		public boolean doWork() {
			show_mesg(
				"等待輸出",				
				String.format("%5.1fV",dcg1.volt.get()),
				String.format("%5.3fA",dcg1.amps.get())
			);
			return true;
		}
	};
	
	protected final Runnable turn_on_wait_dummy = ()->{
		next();
	};
	
	protected final Runnable turn_off = ()->{
		show_mesg("關閉高壓");
		wait_breakin(dcg1,()->{
			if(dcg1.exec("OFF").endsWith("*")==false) {
				abort();
				Application.invokeLater(()->PanBase.notifyError("失敗", "無法關閉!!"));
			}
		});
		//TODO: goto next step!!!
	};
	protected final Runnable turn_off_dummy = ()->{
		next();
	};
	
	private long tick_wait = -1L;
	protected int off_volt = 300;
	protected final Runnable turn_off_wait = ()->{
		if(tick_wait<0L){
			tick_wait = System.currentTimeMillis();
		}
		long remian_msec = System.currentTimeMillis() - tick_wait;
		
		show_mesg(
			"放電中",
			Misc.tick2text(remian_msec,true),
			String.format("%5.1fV",dcg1.volt.get()),
			String.format("%5.3fA",dcg1.amps.get())
		);		
		
		final int vv = dcg1.volt.intValue();
		if(vv>=off_volt){
			trig(this.turn_off_wait);
		}else{
			tick_wait = -1L;
			next();
		}
	};
	protected final Runnable turn_off_wait_dummy = ()->{
		next();
	};
}
