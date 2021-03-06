package prj.sputter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javafx.concurrent.Task;
import javafx.scene.control.Label;
import narl.itrc.Gawain;
import narl.itrc.Misc;
import narl.itrc.Stepper;
import narl.itrc.UtilPhysical;
import narl.itrc.init.LogStream;
import narl.itrc.init.LogStream.Mesg;
import narl.itrc.init.Terminal;

abstract class StepExtender extends Stepper {
	
	protected static DevSQM160 sqm;
	protected static DevDCG100 dcg;
	protected static ModCouple cup;
	protected static DevSPIK2k spk;
	
	//protected static ScriptEngineManager sc_man = new ScriptEngineManager();
	//protected static ScriptEngine sc_eng = sc_man.getEngineByName("nashorn");
	
	protected Label[] msg = {
		new Label(), new Label(), new Label(),
	};
	
	public StepExtender() {
		msg[0].setPrefWidth(150);
		msg[1].setPrefWidth(150);
		msg[2].setPrefWidth(150);
	}
	
	protected void set_mesg(final String... txt) {
		for(int i=0; i<msg.length; i++) {
			if(i>=txt.length) {
				msg[i].setText("");
			}else {
				msg[i].setText(txt[i]);
			}
		}
	}
	
	protected void print_info(final String TAG) {
		
		final float volt = dcg.volt.get();		
		final float amps = dcg.amps.get();				
		final int   watt = (int)dcg.watt.get();
		
		final float rate = sqm.rate[0].get();
		final String unit1 = sqm.unitRate.get();
		
		final float high = sqm.thick[0].get();
		final String unit2 = sqm.unitHigh.get();
		
		final float mfc1 = cup.PV_FlowAr.get();
		final float mfc2 = cup.PV_FlowN2.get();
		final float mfc3 = cup.PV_FlowO2.get();
		
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
	//-----------------------------//
	
	private static final File study_fs = new File(Gawain.pathSock+"temp.csv");
	
	protected static final String pathLogStock= Gawain.pathSock+"監控紀錄"+File.separatorChar;
	protected static final String pathLogCache= pathLogStock+"cache"+File.separatorChar;
	
	private void check_path(final String path) throws Exception {
		File fs = new File(path);
		if(fs.exists()==false) {
			if(fs.mkdirs()==false) {
				throw new Exception("Fail to create "+path);
			}
		}
	}
	private void flush_record(final boolean flag) {
		final Task<?> tsk = new Task<Void>() {		
			@Override
			protected Void call() throws Exception {
				check_path(pathLogStock);
				check_path(pathLogCache);								
				Mesg[] mesg = (flag==true)?(
					LogStream.getInstance().flushPool()
				):(
					LogStream.getInstance().fetchPool()
				);
				try {
					FileWriter fs = new FileWriter(String.format(
						"%s%s.txt",
						pathLogStock,Misc.getDateName()
					));
					for(int i=0; i<mesg.length; i++){
						updateMessage(String.format(
							"%3d/%3d", 
							i,mesg.length
						));
						fs.write(String.format(
							"%s    %s\r\n",
							mesg[i].getTickText(""),
							mesg[i].getText()
						));				
					}
					fs.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				if(flag==true) {
					updateMessage("快取中...");
					Misc.serialize2file(mesg,String.format(
						"%s%s+%s.obj",
						pathLogCache,ladder.uuid(),Misc.getTickName()
					));
				}
				msg[2].textProperty().unbind();
				next_step();
				return null;
			}
		};
		msg[1].setText("匯出紀錄");
		msg[2].textProperty().bind(tsk.messageProperty());
		waiting_async(tsk);
	}

	public static Task<?> task_dump(final String uuid){ return new Task<Integer>() {
		@Override
		protected Integer call() throws Exception {			
			updateMessage("掃瞄快取");
			File[] lstFs = new File(pathLogCache).listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					if(uuid.length()==0){
						return false;
					}
					return name.startsWith(uuid);
				}
			});
			if(lstFs.length==0) {
				updateMessage("無快取資料");
				return -1;
			}
			//dump data as Excel file~~~
			prepare_book();			
			for(File fs:lstFs) {
				//list all cache files~~~
				Object obj = Misc.deserializeFile(fs.getAbsolutePath());
				Mesg[] msg = (Mesg[])obj;				
				writing_book(msg);
			}
			export_book();
			return lstFs.length;
		}
		
		private double[][] range;
		
		private void study_message(
			final String tag,
			final Mesg[] msg
		) {
			String study_tool =  Gawain.prop().getProperty("USE_PYTHON","");
			if(study_tool.length()==0) {
				range = null;//clear
				return;
			}
			updateMessage("分析數據");
			try {
				PrintWriter pw = new PrintWriter(new FileOutputStream(study_fs,false));
				int row_idx = 0;
				for(Mesg m:msg) {					
					String txt = m.getText();
					if(txt.startsWith(tag)==false) {
						continue;
					}
					updateProgress(++row_idx, msg.length);
					String[] _txt = txt
						.substring(txt.indexOf(":")+1)
						.split(",");
					String[][] col = new String[2][_txt.length];
					for(int i=0; i<_txt.length; i++) {
						String[] val = UtilPhysical.split(_txt[i]);
						col[0][i] = val[0];
						col[1][i] = val[1];
					}
					if(row_idx==1) {
						pw.write(String.join(",",col[1])+"\r\n");
					}
					pw.write(String.join(",",col[0])+"\r\n");
				}
				pw.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			String res = Terminal.exec(study_tool,"bound.py");
			String[] col = res.replace("\r\n", "\n").split("\n");
			range = new double[col.length][];
			for(int i=0; i<col.length; i++) {
				if(col[i].startsWith("range")==true) {
					String[] val = col[i].split(":");
					if(val.length<=3) {
						range[i] = new double[2];
						range[i][0] = Double.valueOf(val[1]);
						range[i][1] = Double.valueOf(val[2]);
						continue;
					}
				}
				range[i] = null;
			}
			return;
		}
		
		Workbook wb;
		DataFormat fmt;
		CellStyle styl_norm, styl_upper, styl_lower;
		
		private void writing_book(final Mesg[] msg) {
			Sheet sh = find_sheet(msg);
			get_cell(sh, 0, 0).setCellValue("清洗過程");
			get_cell(sh, 8, 0).setCellValue("鍍膜過程");

			//study_message(StepKindler.TAG_CLEAN,msg);			
			//dump_message(sh,0,StepKindler.TAG_CLEAN,msg);
			
			study_message(StepWatcher.TAG_WATCH,msg);
			dump_message(sh,8,StepWatcher.TAG_WATCH,msg);
		}
		private Sheet find_sheet(final Mesg[] msg) {
			String name = "???";//default sheet name
			for(Mesg m:msg) {
				final String txt = m.getText();
				if(txt.matches("^[\\>\\>].+[\\<\\<]$")==true){					
					name = txt.substring(2,txt.length()-2).trim();
					break;
				}
			}
			Sheet sh = wb.getSheet(name);
			if(sh==null) {
				sh = wb.createSheet(name);
			}
			return sh;
		}
		private void dump_message(
			final Sheet sh,
			final int start_column,
			final String tag,
			final Mesg[] msg
		) {
			int row = 1;
			get_cell(sh, start_column+0, row).setCellValue("時間");
			get_cell(sh, start_column+1, row).setCellValue("電壓");
			get_cell(sh, start_column+2, row).setCellValue("電流");
			get_cell(sh, start_column+3, row).setCellValue("功率");
			get_cell(sh, start_column+4, row).setCellValue("速率");
			get_cell(sh, start_column+5, row).setCellValue("厚度");
			row+=1;
			for(int i=0; i<msg.length; i++){
				updateProgress(i+1, msg.length);
				Mesg m = msg[i];
				String txt = m.getText();
				if(txt.startsWith(tag)==false) {
					continue;
				}
				//put time stamp~~
				get_cell(sh, start_column+0, row).setCellValue(m.getTickText(""));
				//put other records~~~
				String[] val = txt
					.substring(txt.indexOf(":")+1)
					.split(",");
				for(int col=1; col<=5; col++) {
					Cell cc = get_cell(sh, start_column+col, row);
					int v_idx = col - 1;
					double _val = UtilPhysical.getDouble(val[v_idx]);
					//check outline~~~
					cc.setCellStyle(styl_norm);
					if(range!=null && v_idx<range.length && v_idx!=4) {						
						double[] outline = range[v_idx];
						if(outline!=null) {							
							if(_val<=outline[0]) {								
								cc.setCellStyle(styl_lower);
							}else if(outline[1]<=_val){
								cc.setCellStyle(styl_upper);							
							}						
						}
					}
					cc.setCellValue(_val);
				}
				row+=1;
			}
		}		
		private void prepare_book() throws IOException {
			updateMessage("準備中");
			wb = new XSSFWorkbook();
			
			fmt = wb.createDataFormat();
			short f_id = fmt.getFormat("0.000");
			
			styl_norm = wb.createCellStyle();
			styl_norm.setDataFormat(f_id);
			
			Font fnt_red = wb.createFont();
			fnt_red.setColor(IndexedColors.RED.getIndex());
			
			Font fnt_blue = wb.createFont();
			fnt_blue.setColor(IndexedColors.BLUE.getIndex());
			
			styl_upper= wb.createCellStyle();
			styl_upper.setDataFormat(f_id);
			//styl_upper.setFillBackgroundColor(IndexedColors.RED.getIndex());
			//styl_upper.setFillPattern(FillPatternType.NO_FILL);			
			styl_upper.setFont(fnt_red);
			
			styl_lower= wb.createCellStyle();
			styl_lower.setDataFormat(f_id);
			//styl_lower.setFillBackgroundColor(IndexedColors.BLUE.getIndex());
			//styl_lower.setFillPattern(FillPatternType.NO_FILL);
			styl_lower.setFont(fnt_blue);
		}
		private void export_book() throws IOException {
			updateMessage("匯出中");
			FileOutputStream dst = new FileOutputStream(String.format(
				"製程紀錄-%s.xlsx",
				Misc.getDateName()
			));			
			wb.write(dst);		
			wb.close();
			dst.close();
		}		
		private Cell get_cell(
			final Sheet sheet,
			final int xx,
			final int yy
		){
			Row rr = sheet.getRow(yy);
			if(rr==null){
				rr = sheet.createRow(yy);			
			}
			Cell cc = rr.getCell(xx);
			if(cc==null){
				cc = rr.createCell(xx);
			}
			return cc;
		}		
	};}
}

