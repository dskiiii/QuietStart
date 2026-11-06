package cn.quietstart;

import android.net.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class VpnMonitorTest {
    private NetworkCapabilities caps;
    @Before public void setup(){
        caps=mock(NetworkCapabilities.class);
        when(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).thenReturn(true);
        when(caps.getOwnerUid()).thenReturn(-1);
    }
    @Test public void ownedVpnIsNotAnExternalVpn(){
        when(caps.getOwnerUid()).thenReturn(android.os.Process.myUid());
        assertTrue(VpnMonitor.own(caps,null));
    }
    @Test public void redactedOwnerUsesExactVirtualAddressAndDns()throws Exception{
        LinkProperties links=mock(LinkProperties.class);LinkAddress address=mock(LinkAddress.class);
        when(address.getAddress()).thenReturn(java.net.InetAddress.getByName("10.77.0.1"));
        when(links.getLinkAddresses()).thenReturn(java.util.List.of(address));
        when(links.getDnsServers()).thenReturn(java.util.List.of());
        assertFalse(VpnMonitor.own(caps,links));
        when(links.getDnsServers()).thenReturn(java.util.List.of(java.net.InetAddress.getByName("10.77.0.2")));
        assertTrue(VpnMonitor.own(caps,links));
    }
    @Test public void otherVpnWithUnknownOwnerIsNotOurs(){assertFalse(VpnMonitor.own(caps,new LinkProperties()));}
    @Test public void ordinaryNetworkIsNotOurs(){
        when(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).thenReturn(false);
        when(caps.getOwnerUid()).thenReturn(android.os.Process.myUid());
        assertFalse(VpnMonitor.own(caps,null));assertFalse(VpnMonitor.own(null,null));
    }
}
