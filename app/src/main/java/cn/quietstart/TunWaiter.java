package cn.quietstart;

import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;

/** Block without periodic timeouts. Closing the pipe writer wakes an idle reader. */
final class TunWaiter implements Closeable {
    private final ParcelFileDescriptor[] pipe;
    private final StructPollfd[] descriptors;
    TunWaiter(FileDescriptor tun) throws IOException {
        pipe=ParcelFileDescriptor.createPipe();
        StructPollfd packets=new StructPollfd();packets.fd=tun;packets.events=(short)OsConstants.POLLIN;
        StructPollfd stop=new StructPollfd();stop.fd=pipe[0].getFileDescriptor();stop.events=(short)OsConstants.POLLIN;
        descriptors=new StructPollfd[]{packets,stop};
    }
    boolean awaitPacket() throws IOException {
        for(;;) {
            try {Os.poll(descriptors,-1);break;}
            catch(ErrnoException e){if(e.errno!=OsConstants.EINTR)throw new IOException(e);}
        }
        if(descriptors[1].revents!=0)return false;
        if((descriptors[0].revents&(OsConstants.POLLERR|OsConstants.POLLHUP|OsConstants.POLLNVAL))!=0)
            throw new IOException("VPN closed");
        return (descriptors[0].revents&OsConstants.POLLIN)!=0;
    }
    void signalStop(){try{pipe[1].close();}catch(IOException ignored){}}
    @Override public void close(){signalStop();try{pipe[0].close();}catch(IOException ignored){}}
}
