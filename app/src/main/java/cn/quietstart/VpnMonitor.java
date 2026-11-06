package cn.quietstart;

import android.content.Context;
import android.net.*;
import android.os.Handler;
import android.os.Process;
import java.util.HashMap;
import java.util.Map;

/** VPN-only callbacks; never prepares a VPN and never polls on a timer. */
final class VpnMonitor implements AutoCloseable {
    private final ConnectivityManager manager;
    private final Runnable changed;
    private final Map<Network,NetworkCapabilities> capabilities=new HashMap<>();
    private final Map<Network,LinkProperties> properties=new HashMap<>();
    private boolean registered;
    private final ConnectivityManager.NetworkCallback callback=new ConnectivityManager.NetworkCallback(){
        @Override public void onAvailable(Network network){if(registered){capabilities.putIfAbsent(network,null);changed.run();}}
        @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities caps){
            if(registered){capabilities.put(network,caps);changed.run();}
        }
        @Override public void onLinkPropertiesChanged(Network network,LinkProperties links){
            if(registered){properties.put(network,links);changed.run();}
        }
        @Override public void onLost(Network network){
            if(registered){capabilities.remove(network);properties.remove(network);changed.run();}
        }
    };
    VpnMonitor(Context context,Runnable changed){manager=context.getSystemService(ConnectivityManager.class);this.changed=changed;}
    void start(Handler handler){
        if(registered)return;
        // Default NetworkRequest excludes VPNs. Clear defaults so restricted VPNs
        // and VPNs without validated internet are observed too.
        NetworkRequest request=new NetworkRequest.Builder().clearCapabilities()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN).build();
        manager.registerNetworkCallback(request,callback,handler);registered=true;
        for(Network network:manager.getAllNetworks()){
            NetworkCapabilities caps=manager.getNetworkCapabilities(network);
            if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)){
                capabilities.put(network,caps);properties.put(network,manager.getLinkProperties(network));
            }
        }
    }
    static boolean own(NetworkCapabilities caps,LinkProperties links){
        if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return false;
        if(caps.getOwnerUid()==Process.myUid())return true;
        // Some OEMs redact the owner. Our exact virtual DNS/address pair is a fallback.
        return links!=null&&links.getLinkAddresses().stream().anyMatch(a->"10.77.0.1".equals(a.getAddress().getHostAddress()))
                &&links.getDnsServers().stream().anyMatch(a->"10.77.0.2".equals(a.getHostAddress()));
    }
    boolean hasOther(){
        for(var entry:capabilities.entrySet()){
            NetworkCapabilities caps=entry.getValue();LinkProperties links=properties.get(entry.getKey());
            if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&links!=null&&!own(caps,links))return true;
        }
        return false;
    }
    boolean blocksStart(){
        for(var entry:capabilities.entrySet()){
            NetworkCapabilities caps=entry.getValue();
            if(caps==null||(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&!own(caps,properties.get(entry.getKey()))))return true;
        }
        return false;
    }
    static boolean otherVpnPresent(Context context){
        ConnectivityManager manager=context.getSystemService(ConnectivityManager.class);
        for(Network network:manager.getAllNetworks()){
            NetworkCapabilities caps=manager.getNetworkCapabilities(network);
            if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&!own(caps,manager.getLinkProperties(network)))return true;
        }
        return false;
    }
    @Override public void close(){
        if(registered){registered=false;manager.unregisterNetworkCallback(callback);}
        capabilities.clear();properties.clear();
    }
}
