package narl.itrc;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.glass.ui.Application;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public abstract class DevBase implements Runnable {
	
	protected String TAG = "DevBase";

	abstract public void open();	
	abstract public void close();

	@Override
	public String toString() { return TAG; }
		
	public static final AtomicBoolean shutdown = new AtomicBoolean(false); 
	
	public final StringProperty propStateName = new SimpleStringProperty();

	private boolean is_flowing() {
		if(blockage.get()==true || shutdown.get()==true){
			return false;
		}
		return true;
	}

	protected final AtomicBoolean blockage = new AtomicBoolean(true);
	
	private final ConcurrentHashMap<String,Runnable> state_task = new ConcurrentHashMap<String,Runnable>();
	
	private final AtomicReference<String> next_state_name = new AtomicReference<String>("");
	
	private static final String NAME_BREAK_IN = "__breakIn__";//special state
	private static final String NAME_BREAK_OUT= "__breakOut_";//special state
	@Override
	public void run() {
		//main looper
		while(is_flowing()==true) {
			//first, check whether we had break-in event.
			if(state_task.containsKey(NAME_BREAK_IN)==true) {
				Application.invokeLater(()->propStateName.set(NAME_BREAK_IN));
				state_task.get(NAME_BREAK_IN).run();
				state_task.remove(NAME_BREAK_IN);
				if(state_task.containsKey(NAME_BREAK_OUT)==true) {
					Application.invokeLater(state_task.get(NAME_BREAK_OUT));
					state_task.remove(NAME_BREAK_OUT);
				}
				continue;
			}
			//go through state-flow
			final String name = next_state_name.get();
			if(name.length()==0) {
				//if name is empty, it means idle state....
				Application.invokeLater(()->propStateName.set("--idle--"));
				synchronized(taskFlow) {
					try {
						taskFlow.wait();
					} catch (InterruptedException e) {
						Misc.logv("%s is interrupted!!", TAG);//debug message~~~
					}
				}
				continue;
			}
			Application.invokeLater(()->propStateName.set(name));
			//device launch a GUI event, let GUI event decide the end time of emergence.<p>
			//device only launch the emergence once!!
			//it will not enter again, if the flag were not set again.!! 
			final Runnable work = state_task.get(name);
			if(work==null) {
				Misc.loge("[%s] invalid state - %s", TAG, name);
				next_state_name.set("");//edge case, no working, just goto idle.
			}else {
				work.run();
			}
		}
		Misc.logv("%s --> close", TAG);
	}

	private Thread taskFlow = null;
	
	public boolean isFlowing() {
		if(taskFlow==null){
			return false;
		}
		return taskFlow.isAlive();
	}
	public DevBase addState(
		final String name,
		final Runnable work
	) {
		state_task.put(name, work);
		return this;
	}
	
	public void playFlow(final String init_state) {
		//invoked by application thread
		if(taskFlow!=null) {
			return;
		}
		next_state_name.set(init_state);
		blockage.set(false);
		taskFlow = new Thread(this,TAG);
		taskFlow.setDaemon(true);
		taskFlow.start();
	}
	public void stopFlow() {
		//invoked by application thread
		blockage.set(true);
		if(Application.isEventThread()!=true){
			try {
				taskFlow.join();
			} catch (InterruptedException e) {
			}
		}
		taskFlow = null;//reset, because thread is 'dead'
	}	
	public void nextState(final String name) {
		next_state_name.set(name);
		if(taskFlow.getState()==Thread.State.WAITING) {
			synchronized(taskFlow) {
				taskFlow.notify();
			}
		}else if(taskFlow.getState()==Thread.State.TIMED_WAITING) {
			taskFlow.interrupt();
		}
	}
		
	public boolean isBreakOut(){
		return !state_task.containsKey(NAME_BREAK_IN);
	}
	
	/**
	 * 非同步喚醒裝置，插隊執行
	 * @param work - 插隊程式碼
	 * @return self
	 */
	public DevBase asyncBreakIn(final Runnable work) {
		return asyncBreakIn(work,null);
	}
	/**
	 * 非同步喚醒裝置，插隊執行
	 * @param work - 插隊程式碼
	 * @param after- 中斷後的回頭呼叫，這是 GUI-event
	 * @return
	 */
	public DevBase asyncBreakIn(final Runnable work, final Runnable after) {
		if(taskFlow==null) {
			new Thread(()->work.run(),TAG+"-breakin").start();
			return this;
		}
		if(state_task.containsKey(NAME_BREAK_IN)==true){
			System.err.printf("[%s] re-entry break in!!!\n",TAG);
			return this;
		}
		if(work==null){
			System.err.printf("[%s] null break in!!!\n",TAG);
			return this;
		}
		state_task.put(NAME_BREAK_IN, work);
		if(after!=null){
			state_task.put(NAME_BREAK_OUT, after);			
		}
		taskFlow.interrupt();//task may be sleepy~~~
		return this;
	}
	

	public void blockWaiting(){
		while(isBreakOut()==false) {
			try {
				TimeUnit.MILLISECONDS.sleep(25L);
			} catch (InterruptedException e1) {
			}
		}
	}
	
	private static final AtomicBoolean is_emergent = new AtomicBoolean(false);
	private static final StringProperty emergency_tag = new SimpleStringProperty();
	public static final ReadOnlyStringProperty EmergencyTag = emergency_tag;	
	public static Optional<Runnable> emergency = Optional.empty();
	
	protected static synchronized void emergency(final String tag) {
		if(is_emergent.get()==true) {
			return;
		}
		is_emergent.set(true);
		Misc.logw("[%s] !!EMERGENCY!!", tag);
		Application.invokeAndWait(()->{			
			emergency_tag.set(tag);
			if(emergency.isPresent()==true) {
				emergency.get().run();//Let GUI thread set flag again~~~
			}else {
				is_emergent.set(false);
			}
		});
	}
	
	public static void ignore_emergency() {
		if(Application.isEventThread()==false) {
			//only GUI-event can decide to exit in an emergency.
			return;
		}
		Misc.logw("~~ Ignore emergency~~");
		is_emergent.set(false);
	}

	protected void block_sleep_msec(final long val) {
		try {
			if(val<=0) { return; }
			TimeUnit.MILLISECONDS.sleep(val);
		} catch (InterruptedException e) {
		}
	}
	
	protected void block_sleep_sec(final long val) {
		try {
			if(val<=0) { return; }
			TimeUnit.SECONDS.sleep(val);
		} catch (InterruptedException e) {
		}
	}



}

