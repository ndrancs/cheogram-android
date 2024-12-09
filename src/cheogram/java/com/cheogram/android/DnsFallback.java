package com.cheogram.android;

import android.content.Context;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;
import java.util.stream.Collectors;

import org.minidns.dnsserverlookup.AbstractDnsServerLookupMechanism;
import org.minidns.dnsserverlookup.AndroidUsingExec;

public class DnsFallback extends AbstractDnsServerLookupMechanism {

    public DnsFallback() {
        super("DnsFallback", AndroidUsingExec.PRIORITY + 1000);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<String> getDnsServerAddresses() {
        return List.of("9.9.9.9");
    }
}
