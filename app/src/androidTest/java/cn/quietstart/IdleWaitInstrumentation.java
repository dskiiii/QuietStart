package cn.quietstart;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import java.util.concurrent.*;

/** Device-only OS integration checks; no device means these are compiled, not executed. */
public final class IdleWaitInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            checkStop(false);checkStop(true);checkPacket();
            result.putString("stream","PASS: idle blocks; explicit stop wakes; stop before wait; packet wakes; repeated close\n");finish(-1,result);
        }catch(Throwable e){result.putString("stream","FAIL: "+e+"\n");finish(1,result);}
    }
    private FutureTask<Boolean> startWait(TunWaiter waiter){
        FutureTask<Boolean> task=new FutureTask<>(waiter::awaitPacket);
        Thread thread=new Thread(task,"IdleWaitTest");thread.setDaemon(true);thread.start();return task;
    }
    private void checkStop(boolean before) throws Exception {
        ParcelFileDescriptor[] tunnel=ParcelFileDescriptor.createPipe();
        try(TunWaiter waiter=new TunWaiter(tunnel[0].getFileDescriptor())){
            if(before)waiter.signalStop();
            FutureTask<Boolean> result=startWait(waiter);
            if(!before){
                try{result.get(150,TimeUnit.MILLISECONDS);throw new AssertionError("idle wait returned without event");}
                catch(TimeoutException expected){}
                waiter.signalStop();
            }
            if(result.get(2,TimeUnit.SECONDS))throw new AssertionError("stop treated as packet");
            waiter.signalStop();
        }finally{tunnel[0].close();tunnel[1].close();}
    }
    private void checkPacket() throws Exception {
        ParcelFileDescriptor[] tunnel=ParcelFileDescriptor.createPipe();
        try(TunWaiter waiter=new TunWaiter(tunnel[0].getFileDescriptor())){
            FutureTask<Boolean> result=startWait(waiter);
            Os.write(tunnel[1].getFileDescriptor(),new byte[]{1},0,1);
            if(!result.get(2,TimeUnit.SECONDS))throw new AssertionError("packet treated as stop");
        }finally{tunnel[0].close();tunnel[1].close();}
    }
}
